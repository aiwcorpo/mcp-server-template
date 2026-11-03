package pl.aiwcorpo.mcp.example.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The backend system in tests: a real HTTP server on loopback, so the adapter runs with its real client,
 * timeouts and error mapping. Records it knows:
 * <ul>
 *   <li>{@link #OPEN_1}, {@link #OPEN_2}: visible, in the open project (no classification), {@link #OPEN_1} with an
 *       extra field the server must not pass on;</li>
 *   <li>{@link #OTHER_7}: visible, in another open project;</li>
 *   <li>{@link #RESTRICTED_5}: visible to the user in the backend, but its project is classified Restricted;</li>
 *   <li>{@link #MOVED}: moved to the restricted project after the id was issued; the backend answers the old id with
 *       the record as it is now ({@link #MOVED_NOW});</li>
 *   <li>{@link #UNCLASSIFIED}: in a project whose classification the backend does not report;</li>
 *   <li>{@link #NO_STATUS}: without status; {@link #NO_PROJECT}: without project (a faulty answer);</li>
 *   <li>{@link #FORBIDDEN}: exists but the caller may not see it (403);</li>
 *   <li>{@link #FAILING}: the backend fails and echoes the caller's Authorization header in the body;</li>
 *   <li>{@link #BAD_JSON}: 200 with a body that is not JSON and carries {@link #BODY_MARKER};</li>
 *   <li>{@link #NO_ID}: 200 with a record without id;</li>
 *   <li>{@link #REDIRECT}: redirect to a second server, which records whether anything followed;</li>
 *   <li>{@link #SLOW}: answers after {@link #SLOW_MILLIS}; {@link #HUGE}: 200 with a body of {@link #HUGE_BYTES};</li>
 *   <li>anything else: 404.</li>
 * </ul>
 * A search returns every row that matches its text and the narrowing of a link in the query, whatever the project: the
 * requested project is not a condition of the query (the contracts do not allow it), the server narrows the rows. Only
 * a query that names a project (the systems without a query builder) is answered for that project. A search with
 * {@code indices} (OpenSearch) is answered for those index names only, and {@code /_resolve/index/} lists the
 * indices and the alias the expression matches. Any request with {@link #EXPIRED_CREDENTIAL} is answered 401, like a revoked token.
 */
final class StubBackend {

    static final String EXPIRED_CREDENTIAL = "Bearer expired-pat-of-jkowalski";
    static final String BODY_MARKER = "SECRETBODYTOKEN123";
    /** A search for this text makes the backend fail. */
    static final String FAILING_TEXT = "backend-fails";
    static final long SLOW_MILLIS = 4_000;
    static final int HUGE_BYTES = 256 * 1024;

    static final String OPEN_1 = SampleScope.id(SampleScope.OPEN_PROJECT, 1);
    static final String OPEN_2 = SampleScope.id(SampleScope.OPEN_PROJECT, 2);
    static final String OTHER_7 = SampleScope.id(SampleScope.OTHER_PROJECT, 7);
    static final String RESTRICTED_5 = SampleScope.id(SampleScope.RESTRICTED_PROJECT, 5);
    static final String MOVED = SampleScope.id(SampleScope.OPEN_PROJECT, 123);
    static final String MOVED_NOW = SampleScope.id(SampleScope.RESTRICTED_PROJECT, 45);
    static final String UNCLASSIFIED = SampleScope.id(SampleScope.UNCLASSIFIED_PROJECT, 1);
    static final String NO_STATUS = SampleScope.id(SampleScope.OPEN_PROJECT, 901);
    static final String NO_PROJECT = SampleScope.id(SampleScope.OPEN_PROJECT, 902);
    static final String FORBIDDEN = SampleScope.id(SampleScope.OPEN_PROJECT, 903);
    static final String FAILING = SampleScope.id(SampleScope.OPEN_PROJECT, 904);
    static final String BAD_JSON = SampleScope.id(SampleScope.OPEN_PROJECT, 905);
    static final String NO_ID = SampleScope.id(SampleScope.OPEN_PROJECT, 906);
    static final String REDIRECT = SampleScope.id(SampleScope.OPEN_PROJECT, 907);
    static final String SLOW = SampleScope.id(SampleScope.OPEN_PROJECT, 908);
    static final String HUGE = SampleScope.id(SampleScope.OPEN_PROJECT, 909);
    /** An alias an administrator could create: named like an index of the open project, pointing at another team's. */
    static final String ALIAS = SampleScope.OPEN_PROJECT + "-all";
    static final String MISSING = SampleScope.id(SampleScope.OPEN_PROJECT, 404);

    private static final String RECORDS = "/api/records/";
    /**
     * Comments of {@link #OPEN_1}: numbers 1 to 24 in the order of the backend, every fourth one filed under
     * {@link #OPEN_2} (a backend that does not keep a comment to its parent). The comment endpoint answers any number
     * of the range whatever record is in the path, and a list is the rows of its window, so the server has to do the
     * checking.
     */
    static final int COMMENTS = 24;
    static final String SERVER_VERSION = "2.4.1";

    private final HttpServer server;
    private final HttpServer elsewhere;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> elsewhereHits = new CopyOnWriteArrayList<>();
    private final List<String> searches = new CopyOnWriteArrayList<>();
    private final List<String> batches = new CopyOnWriteArrayList<>();
    private final List<String> indexSearches = new CopyOnWriteArrayList<>();
    private final List<String> resolutions = new CopyOnWriteArrayList<>();
    private final List<String> resolutionQueries = new CopyOnWriteArrayList<>();
    private final List<String> updates = new CopyOnWriteArrayList<>();
    private volatile boolean resolveNotFound;
    private volatile String serverVersion = SERVER_VERSION;
    private volatile boolean versionHidden;

    private StubBackend(HttpServer server, HttpServer elsewhere) {
        this.server = server;
        this.elsewhere = elsewhere;
    }

    static StubBackend start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            HttpServer elsewhere = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            StubBackend backend = new StubBackend(server, elsewhere);
            server.createContext(RECORDS, backend::handle);
            server.createContext("/api/records", backend::search);
            server.createContext("/api/server-info", backend::serverInfo);
            server.createContext("/_resolve/index/", backend::resolveIndex);
            elsewhere.createContext("/", exchange -> {
                backend.elsewhereHits.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
                respond(exchange, 200, record(REDIRECT, SampleScope.OPEN_PROJECT, "\"\"", "from elsewhere"));
            });
            // The slow record must not hold up the requests of the next test.
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            elsewhere.start();
            return backend;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Authorization headers received so far, in order. */
    List<String> authorizations() {
        return List.copyOf(authorizations);
    }

    /** Authorization headers that reached the redirect target. */
    List<String> elsewhereHits() {
        return List.copyOf(elsewhereHits);
    }

    /** The queries (decoded) of the searches received so far. */
    List<String> searches() {
        return List.copyOf(searches);
    }

    /** The id lists of the batch lookups received so far. */
    List<String> batches() {
        return List.copyOf(batches);
    }

    /** The searches by index names so far: {@code names?other parameters}. */
    List<String> indexSearches() {
        return List.copyOf(indexSearches);
    }

    /** The expressions of the {@code _resolve/index} calls so far. */
    List<String> resolutions() {
        return List.copyOf(resolutions);
    }

    /** The query strings of the {@code _resolve/index} calls so far. */
    List<String> resolutionQueries() {
        return List.copyOf(resolutionQueries);
    }

    /** Makes {@code _resolve/index} answer 404 index_not_found_exception, as a cluster does when no index matches. */
    void resolveAnswersNotFound(boolean value) {
        resolveNotFound = value;
    }

    /** The {@code title} sets of the PUT calls so far: {@code id title}. */
    List<String> updates() {
        return List.copyOf(updates);
    }

    /** The version {@code /api/server-info} reports from now on. */
    void reportsVersion(String version) {
        serverVersion = version;
        versionHidden = false;
    }

    /** {@code /api/server-info} answers without a version, as a backend that hides it does. */
    void hidesVersion() {
        versionHidden = true;
    }

    void reset() {
        updates.clear();
        resolveNotFound = false;
        indexSearches.clear();
        resolutions.clear();
        resolutionQueries.clear();
        authorizations.clear();
        elsewhereHits.clear();
        searches.clear();
        batches.clear();
    }

    private static final Pattern REQUESTED = Pattern.compile("(?:project|space) = \"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern LINKED = Pattern.compile("(?:project|space) in \\(([^)]*)\\)");
    private static final Pattern TEXT = Pattern.compile("(?:text|title) ~ \"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * {@code ?ids=a,b}: the records among them that the stub keeps in one place (a batch for the resolver). Otherwise
     * {@code ?query=}: the query the server built, read the way Jira and Confluence read the part that matters here: the
     * requested project or space, the narrowing of a link ({@code project in ("ABC")}) and the text of the title.
     */
    private void search(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(String.valueOf(authorization));
        if (EXPIRED_CREDENTIAL.equals(authorization)) {
            respond(exchange, 401, "{\"message\":\"token expired\"}");
            return;
        }
        Map<String, List<String>> parameters = parameters(exchange.getRequestURI().getRawQuery());
        if (parameters.containsKey("ids")) {
            batches.add(parameters.get("ids").getFirst());
            List<String> found = new ArrayList<>();
            for (String id : parameters.get("ids").getFirst().split(",")) {
                String json = batchRecord(id);
                if (json != null) {
                    found.add(json);
                }
            }
            respond(exchange, 200, "{\"records\":[" + String.join(",", found) + "]}");
            return;
        }
        String query = parameters.getOrDefault("query", List.of("")).getFirst();
        searches.add(query);
        boolean byIndices = parameters.containsKey("indices");
        if (byIndices) {
            indexSearches.add(parameters.get("indices").getFirst() + "?" + rawQueryWithoutText(exchange));
        }
        Matcher requested = REQUESTED.matcher(query);
        String project = requested.find() ? requested.group(1) : null;
        Matcher text = byIndices ? LITERAL.matcher(query) : TEXT.matcher(query);
        String needle = (text.find() ? text.group(1).replace("\\\"", "\"") : "").toLowerCase(Locale.ROOT);
        if (FAILING_TEXT.equals(needle)) {
            respond(exchange, 500, "{\"error\":\"boom\",\"seen\":\"" + authorization + "\"}");
            return;
        }
        List<String> linkedProjects = null;
        Matcher linked = LINKED.matcher(query);
        if (linked.find()) {
            linkedProjects = List.of(linked.group(1).replace("\"", "").split("\\s*,\\s*"));
        }
        List<String> indices = byIndices ? List.of(parameters.get("indices").getFirst().split(",")) : null;
        List<String> hits = new ArrayList<>();
        for (String[] row : SEARCH_ROWS) {
            boolean inProject = project == null || project.equals(row[0]);
            boolean inLink = linkedProjects == null || linkedProjects.contains(row[0]);
            boolean inIndices = indices == null || indices.contains(row[1]);
            if (inProject && inLink && inIndices && row[2].toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add("{\"id\":\"" + row[1] + "\",\"project\":\"" + row[0] + "\",\"title\":\"" + row[2] + "\"}");
            }
        }
        respond(exchange, 200, "{\"records\":[" + String.join(",", hits) + "],\"total\":" + hits.size() + "}");
    }

    private static String rawQueryWithoutText(HttpExchange exchange) {
        return String.join("&", java.util.Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                .filter(pair -> !pair.startsWith("query=") && !pair.startsWith("indices=")).toList());
    }

    /**
     * {@code GET _resolve/index/<expression>}: the indices the comma separated patterns match ({@code p} exactly,
     * {@code p-*} by prefix) and an alias named like an index of the open project that points at another team's index,
     * as an administrator could create it.
     */
    private void resolveIndex(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(String.valueOf(authorization));
        if (EXPIRED_CREDENTIAL.equals(authorization)) {
            respond(exchange, 401, "{\"message\":\"token expired\"}");
            return;
        }
        String expression = exchange.getRequestURI().getPath().substring("/_resolve/index/".length());
        resolutions.add(expression);
        resolutionQueries.add(String.valueOf(exchange.getRequestURI().getRawQuery()));
        if (resolveNotFound) {
            respond(exchange, 404, "{\"error\":{\"type\":\"index_not_found_exception\"},\"status\":404}");
            return;
        }
        List<String> patterns = List.of(expression.split(","));
        List<String> names = new ArrayList<>(List.of(OPEN_1, OPEN_2, OTHER_7, RESTRICTED_5, MOVED, UNCLASSIFIED));
        names.add(ALIAS);
        List<String> indices = new ArrayList<>();
        List<String> aliases = new ArrayList<>();
        for (String name : names) {
            boolean matches = patterns.stream().anyMatch(pattern -> pattern.endsWith("-*")
                    ? name.startsWith(pattern.substring(0, pattern.length() - 1)) : name.equals(pattern));
            if (matches) {
                (ALIAS.equals(name) ? aliases : indices).add("{\"name\":\"" + name + "\"}");
            }
        }
        respond(exchange, 200, "{\"indices\":[" + String.join(",", indices) + "],\"aliases\":[" + String.join(",", aliases)
                + "],\"data_streams\":[]}");
    }

    /** Searched project (as the row reports it), id, title. The moved record is still listed under its old project. */
    private static final String[][] SEARCH_ROWS = {
            {SampleScope.OPEN_PROJECT, OPEN_1, "Quarterly report"},
            {SampleScope.OPEN_PROJECT, OPEN_2, "Report archive"},
            {SampleScope.OTHER_PROJECT, OTHER_7, "Report of the other team"},
            {SampleScope.RESTRICTED_PROJECT, RESTRICTED_5, "Salary report"},
            {SampleScope.OPEN_PROJECT, MOVED, "Payroll report moved away"},
    };

    private static String batchRecord(String id) {
        if (OPEN_1.equals(id)) {
            return record(OPEN_1, SampleScope.OPEN_PROJECT, "\"\"", "Quarterly report");
        } else if (OPEN_2.equals(id)) {
            return record(OPEN_2, SampleScope.OPEN_PROJECT, "\"\"", "Report archive");
        } else if (OTHER_7.equals(id)) {
            return record(OTHER_7, SampleScope.OTHER_PROJECT, "\"\"", "Report of the other team");
        } else if (RESTRICTED_5.equals(id)) {
            return record(RESTRICTED_5, SampleScope.RESTRICTED_PROJECT, "\"Restricted\"", "Salary report");
        }
        return null;
    }

    private static Map<String, List<String>> parameters(String rawQuery) {
        Map<String, List<String>> parameters = new java.util.LinkedHashMap<>();
        if (rawQuery == null) {
            return parameters;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            parameters.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return parameters;
    }

    /** What a probe reads: no credential, only the version. The stub does not count these calls as caller requests. */
    private void serverInfo(HttpExchange exchange) throws IOException {
        if (exchange.getRequestHeaders().getFirst("Authorization") != null) {
            respond(exchange, 400, "{\"message\":\"the probe has no credential\"}");
        } else if (versionHidden) {
            respond(exchange, 200, "{\"name\":\"stub\"}");
        } else {
            respond(exchange, 200, "{\"name\":\"stub\",\"version\":\"" + serverVersion + "\"}");
        }
    }

    private void comments(HttpExchange exchange, String path, String authorization) throws IOException {
        if (EXPIRED_CREDENTIAL.equals(authorization)) {
            respond(exchange, 401, "{\"message\":\"token expired\"}");
            return;
        }
        String[] parts = path.split("/comments", 2);
        String recordId = parts[0];
        if (!OPEN_1.equals(recordId) && !OPEN_2.equals(recordId)) {
            respond(exchange, 404, "{\"message\":\"no such record\"}");
            return;
        }
        String rest = parts[1];
        if (rest.startsWith("/")) {
            int number = rest.substring(1).matches("[0-9]{1,3}") ? Integer.parseInt(rest.substring(1)) : 0;
            if (number < 1 || number > COMMENTS) {
                respond(exchange, 404, "{\"message\":\"no such comment\"}");
            } else {
                respond(exchange, 200, comment(number));
            }
            return;
        }
        Map<String, List<String>> parameters = parameters(exchange.getRequestURI().getRawQuery());
        int start = Integer.parseInt(parameters.getOrDefault("start", List.of("0")).getFirst());
        int limit = Integer.parseInt(parameters.getOrDefault("limit", List.of("10")).getFirst());
        int end = Math.min(COMMENTS, start + limit);
        List<String> rows = new ArrayList<>();
        for (int number = start + 1; number <= end; number++) {
            rows.add(comment(number));
        }
        respond(exchange, 200, "{\"comments\":[" + String.join(",", rows) + "],\"hasMore\":" + (end < COMMENTS) + "}");
    }

    /** Comment {@code number}: every fourth is filed under {@link #OPEN_2}, the others under {@link #OPEN_1}. */
    static String comment(int number) {
        String owner = number % 4 == 0 ? OPEN_2 : OPEN_1;
        return "{\"id\":\"" + number + "\",\"recordId\":\"" + owner + "\",\"author\":\"jkowalski\",\"body\":\"comment "
                + number + "\"}";
    }

    private void update(HttpExchange exchange, String id, String authorization) throws IOException {
        if (EXPIRED_CREDENTIAL.equals(authorization)) {
            respond(exchange, 401, "{\"message\":\"token expired\"}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Matcher title = Pattern.compile("\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
        if (!title.find()) {
            respond(exchange, 400, "{\"message\":\"no title\"}");
            return;
        }
        String known = batchRecord(id);
        if (known == null) {
            respond(exchange, 404, "{\"message\":\"no such record\"}");
            return;
        }
        updates.add(id + " " + title.group(1));
        respond(exchange, 200, known.replaceFirst("\"title\":\"[^\"]*\"", "\"title\":\"" + title.group(1).replace("$", "\\$") + "\""));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(String.valueOf(authorization));
        String id = exchange.getRequestURI().getPath().substring(RECORDS.length());
        String open = SampleScope.OPEN_PROJECT;
        if (id.contains("/comments")) {
            comments(exchange, id, authorization);
        } else if ("PUT".equals(exchange.getRequestMethod())) {
            update(exchange, id, authorization);
        } else if (EXPIRED_CREDENTIAL.equals(authorization)) {
            respond(exchange, 401, "{\"message\":\"token expired\"}");
        } else if (OPEN_1.equals(id)) {
            respond(exchange, 200, "{\"id\":\"" + OPEN_1 + "\",\"project\":\"" + open + "\",\"projectClassification\":\"\","
                    + "\"title\":\"Quarterly report\",\"status\":\"open\",\"internalNotes\":\"not for the model\"}");
        } else if (OPEN_2.equals(id)) {
            respond(exchange, 200, record(OPEN_2, open, "\"\"", "Report archive"));
        } else if (OTHER_7.equals(id)) {
            respond(exchange, 200, record(OTHER_7, SampleScope.OTHER_PROJECT, "\"\"", "Report of the other team"));
        } else if (RESTRICTED_5.equals(id)) {
            respond(exchange, 200, record(RESTRICTED_5, SampleScope.RESTRICTED_PROJECT, "\"Restricted\"", "Salary report"));
        } else if (MOVED.equals(id)) {
            respond(exchange, 200, record(MOVED_NOW, SampleScope.RESTRICTED_PROJECT, "\"Restricted\"", "Payroll report moved away"));
        } else if (UNCLASSIFIED.equals(id)) {
            respond(exchange, 200, "{\"id\":\"" + UNCLASSIFIED + "\",\"project\":\"" + SampleScope.UNCLASSIFIED_PROJECT
                    + "\",\"title\":\"Unclassified project\",\"status\":\"open\"}");
        } else if (NO_STATUS.equals(id)) {
            respond(exchange, 200, "{\"id\":\"" + NO_STATUS + "\",\"project\":\"" + open
                    + "\",\"projectClassification\":\"\",\"title\":\"Sparse record\"}");
        } else if (NO_PROJECT.equals(id)) {
            respond(exchange, 200, "{\"id\":\"" + NO_PROJECT + "\",\"title\":\"no project\"}");
        } else if (BAD_JSON.equals(id)) {
            respond(exchange, 200, "<html>" + BODY_MARKER + " login</html>");
        } else if (NO_ID.equals(id)) {
            respond(exchange, 200, "{\"project\":\"" + open + "\",\"title\":\"no id\"}");
        } else if (REDIRECT.equals(id)) {
            exchange.getResponseHeaders().set("Location",
                    "http://127.0.0.1:" + elsewhere.getAddress().getPort() + RECORDS + REDIRECT);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        } else if (SLOW.equals(id)) {
            sleep();
            respond(exchange, 200, record(SLOW, open, "\"\"", "late"));
        } else if (HUGE.equals(id)) {
            respond(exchange, 200, record(HUGE, open, "\"\"", "x".repeat(HUGE_BYTES)));
        } else if (FORBIDDEN.equals(id)) {
            respond(exchange, 403, "{\"message\":\"forbidden\"}");
        } else if (FAILING.equals(id)) {
            respond(exchange, 500, "{\"error\":\"boom\",\"seen\":\"" + authorization + "\"}");
        } else {
            respond(exchange, 404, "{\"message\":\"no such record\"}");
        }
    }

    private static String record(String id, String project, String classificationJson, String title) {
        return "{\"id\":\"" + id + "\",\"project\":\"" + project + "\",\"projectClassification\":" + classificationJson
                + ",\"title\":\"" + title + "\",\"status\":\"open\"}";
    }

    private static void sleep() {
        try {
            Thread.sleep(SLOW_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
