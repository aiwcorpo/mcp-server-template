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
 * The search of a project returns its own records and, as a backend with links between records does, records of other
 * projects. Any request with {@link #EXPIRED_CREDENTIAL} is answered 401, like a revoked token.
 */
final class StubBackend {

    static final String EXPIRED_CREDENTIAL = "Bearer expired-pat-of-jkowalski";
    static final String BODY_MARKER = "SECRETBODYTOKEN123";
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
    static final String MISSING = SampleScope.id(SampleScope.OPEN_PROJECT, 404);

    private static final String RECORDS = "/api/records/";

    private final HttpServer server;
    private final HttpServer elsewhere;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> elsewhereHits = new CopyOnWriteArrayList<>();
    private final List<String> searches = new CopyOnWriteArrayList<>();
    private final List<String> batches = new CopyOnWriteArrayList<>();

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

    void reset() {
        authorizations.clear();
        elsewhereHits.clear();
        searches.clear();
        batches.clear();
    }

    private static final Pattern REQUESTED = Pattern.compile("(?:project|space) = \"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern LINKED = Pattern.compile("(?:project|space) in \\(([^)]*)\\)");
    private static final Pattern TEXT = Pattern.compile("(?:text|title) ~ \"((?:[^\"\\\\]|\\\\.)*)\"");

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
        Matcher requested = REQUESTED.matcher(query);
        String project = requested.find() ? requested.group(1) : "";
        Matcher text = TEXT.matcher(query);
        String needle = (text.find() ? text.group(1).replace("\\\"", "\"") : "").toLowerCase(Locale.ROOT);
        if (SampleScope.FAIL_PROJECT.equals(project)) {
            respond(exchange, 500, "{\"error\":\"boom\",\"seen\":\"" + authorization + "\"}");
            return;
        }
        Matcher linked = LINKED.matcher(query);
        if (linked.find() && !List.of(linked.group(1).replace("\"", "").split("\\s*,\\s*")).contains(project)) {
            respond(exchange, 200, "{\"records\":[]}");
            return;
        }
        List<String> hits = new ArrayList<>();
        for (String[] hit : SEARCH_ROWS) {
            if (hit[0].equals(project) && hit[2].toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add("{\"id\":\"" + hit[1] + "\",\"title\":\"" + hit[2] + "\"}");
            }
        }
        respond(exchange, 200, "{\"records\":[" + String.join(",", hits) + "],\"total\":" + hits.size() + "}");
    }

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

    /** Searched project, id, title. Project ABC links to a record of DEF, to a restricted one and to a moved one. */
    private static final String[][] SEARCH_ROWS = {
            {SampleScope.OPEN_PROJECT, OPEN_1, "Quarterly report"},
            {SampleScope.OPEN_PROJECT, OPEN_2, "Report archive"},
            {SampleScope.OPEN_PROJECT, OTHER_7, "Report of the other team"},
            {SampleScope.OPEN_PROJECT, RESTRICTED_5, "Salary report"},
            {SampleScope.OPEN_PROJECT, MOVED, "Payroll report moved away"},
            {SampleScope.OTHER_PROJECT, OTHER_7, "Report of the other team"},
            {SampleScope.RESTRICTED_PROJECT, RESTRICTED_5, "Salary report"},
    };

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

    private void handle(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(String.valueOf(authorization));
        String id = exchange.getRequestURI().getPath().substring(RECORDS.length());
        String open = SampleScope.OPEN_PROJECT;
        if (EXPIRED_CREDENTIAL.equals(authorization)) {
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
