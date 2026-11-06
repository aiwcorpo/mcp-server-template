package pl.aiwcorpo.mcp.example.stub;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The backend system the template server talks to, with a few records in memory, so that the server runs end to end
 * (LocalGateway on a workstation, a container next to the server in a test stack) without a real system.
 *
 * <p>It answers the paths {@code ExampleResourceAdapter} calls: {@code GET /api/server-info} (no credential, like a
 * version probe), {@code GET /api/me} (the whoami the broker uses), {@code GET /api/records/{id}}, {@code GET /api/records?ids=} and {@code ?query=&limit=} (the query the
 * server builds: {@code project in ("ABC") AND text ~ "report"}), {@code PUT /api/records/{id}} for the title and the
 * comments of a record. Any request but the version needs an {@code Authorization} header and the stub accepts every
 * value except one that starts with {@code expired}, which is answered 401 like a revoked token, so the
 * {@code credential_required} path can be tried. The stub applies no rights of its own: it shows the record of the
 * restricted project to every caller, which is what the policy of the server has to refuse.
 *
 * <p>Not a model of the real system and not for any network but a closed test one: no TLS, no authentication, data
 * lost on exit.
 */
public final class StubBackendServer {

    static final String VERSION = "2.4.1";
    private static final int COMMENTS_OF_FIRST_RECORD = 12;
    private static final Pattern PROJECT_CLAUSE = Pattern.compile("project in \\(([^)]*)\\)");
    private static final Pattern TEXT_CONDITION = Pattern.compile("text ~ \"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern TITLE_FIELD = Pattern.compile("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private record Project(String key, String category) {
    }

    private record Record(String id, String project, String title, String status) {
    }

    private final Map<String, Project> projects = new LinkedHashMap<>();
    private final Map<String, Record> records = new ConcurrentHashMap<>();
    private final HttpServer server;

    private StubBackendServer(HttpServer server) {
        this.server = server;
        projects.put("ABC", new Project("ABC", ""));
        projects.put("DEF", new Project("DEF", ""));
        projects.put("HR", new Project("HR", "Restricted"));
        add("ABC-1", "ABC", "Quarterly report");
        add("ABC-2", "ABC", "Report archive");
        add("DEF-7", "DEF", "Report of the other team");
        add("HR-5", "HR", "Salary report");
    }

    private void add(String id, String project, String title) {
        records.put(id, new Record(id, project, title, "open"));
    }

    /** Starts the stub on {@code bind}:{@code port}; port 0 picks a free one. */
    public static StubBackendServer start(String bind, int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bind), port), 0);
        StubBackendServer stub = new StubBackendServer(http);
        http.createContext("/api/server-info", stub::serverInfo);
        http.createContext("/api/records", stub::records);
        http.createContext("/api/me", stub::me);
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        return stub;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    public static void main(String[] args) throws IOException {
        String bind = "127.0.0.1";
        int port = 18081;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bind" -> bind = value(args, ++i);
                case "--port" -> port = Integer.parseInt(value(args, ++i));
                default -> throw new IllegalArgumentException("usage: StubBackendServer [--bind <address>] [--port <port>]");
            }
        }
        StubBackendServer stub = start(bind, port);
        System.out.println("Stub backend on " + bind + ":" + stub.port() + " (records ABC-1, ABC-2, DEF-7, HR-5; HR is classified Restricted)");
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("a value is missing after " + args[index - 1]);
        }
        return args[index];
    }

    private void serverInfo(HttpExchange exchange) throws IOException {
        if (exchange.getRequestHeaders().getFirst("Authorization") != null) {
            respond(exchange, 400, "{\"message\":\"the version needs no credential\"}");
            return;
        }
        respond(exchange, 200, "{\"name\":\"stub\",\"version\":\"" + VERSION + "\"}");
    }

    /** The whoami of the system: the broker calls it with the user's token to check it and learn the user name. */
    private void me(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || authorization.isBlank()) {
            respond(exchange, 401, "{\"message\":\"credential required\"}");
        } else if (isExpired(authorization)) {
            respond(exchange, 401, "{\"message\":\"token expired\"}");
        } else {
            respond(exchange, 200, "{\"username\":\"stub-user\"}");
        }
    }

    private static boolean isExpired(String authorization) {
        return authorization.toLowerCase(Locale.ROOT).replaceFirst("^\\S+\\s+", "").startsWith("expired");
    }

    private void records(HttpExchange exchange) throws IOException {
        try (exchange) {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization == null || authorization.isBlank()) {
                send(exchange, 401, "{\"message\":\"credential required\"}");
                return;
            }
            if (isExpired(authorization)) {
                send(exchange, 401, "{\"message\":\"token expired\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath().substring("/api/records".length());
            Map<String, String> parameters = parameters(exchange.getRequestURI().getRawQuery());
            if (path.isEmpty() || path.equals("/")) {
                send(exchange, 200, parameters.containsKey("ids") ? byIds(parameters.get("ids")) : search(parameters));
            } else if (path.contains("/comments")) {
                comments(exchange, path.substring(1), parameters);
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                update(exchange, path.substring(1));
            } else {
                Record found = records.get(path.substring(1));
                send(exchange, found == null ? 404 : 200, found == null ? "{\"message\":\"no such record\"}" : json(found));
            }
        }
    }

    private String byIds(String ids) {
        List<String> found = new ArrayList<>();
        for (String id : ids.split(",")) {
            Record record = records.get(id);
            if (record != null) {
                found.add(json(record));
            }
        }
        return "{\"records\":[" + String.join(",", found) + "]}";
    }

    private String search(Map<String, String> parameters) {
        String query = parameters.getOrDefault("query", "");
        int limit = Integer.parseInt(parameters.getOrDefault("limit", "50"));
        Matcher clause = PROJECT_CLAUSE.matcher(query);
        List<String> inProjects = clause.find() ? List.of(clause.group(1).replace("\"", "").split("\\s*,\\s*")) : null;
        Matcher condition = TEXT_CONDITION.matcher(query);
        String needle = condition.find() ? unescape(condition.group(1)).toLowerCase(Locale.ROOT) : "";
        List<String> hits = records.values().stream()
                .sorted(java.util.Comparator.comparing(Record::id))
                .filter(record -> inProjects == null || inProjects.contains(record.project()))
                .filter(record -> record.title().toLowerCase(Locale.ROOT).contains(needle))
                .limit(limit)
                .map(this::json)
                .toList();
        return "{\"records\":[" + String.join(",", hits) + "]}";
    }

    private void comments(HttpExchange exchange, String path, Map<String, String> parameters) throws IOException {
        String[] parts = path.split("/comments", 2);
        if (!"ABC-1".equals(parts[0])) {
            send(exchange, records.containsKey(parts[0]) ? 200 : 404,
                    records.containsKey(parts[0]) ? "{\"comments\":[],\"hasMore\":false}" : "{\"message\":\"no such record\"}");
            return;
        }
        if (parts[1].startsWith("/")) {
            String number = parts[1].substring(1);
            boolean known = number.matches("[0-9]{1,3}") && Integer.parseInt(number) >= 1
                    && Integer.parseInt(number) <= COMMENTS_OF_FIRST_RECORD;
            send(exchange, known ? 200 : 404, known ? comment(Integer.parseInt(number)) : "{\"message\":\"no such comment\"}");
            return;
        }
        int start = Integer.parseInt(parameters.getOrDefault("start", "0"));
        int limit = Integer.parseInt(parameters.getOrDefault("limit", "10"));
        int end = Math.min(COMMENTS_OF_FIRST_RECORD, start + limit);
        List<String> rows = new ArrayList<>();
        for (int number = start + 1; number <= end; number++) {
            rows.add(comment(number));
        }
        send(exchange, 200, "{\"comments\":[" + String.join(",", rows) + "],\"hasMore\":" + (end < COMMENTS_OF_FIRST_RECORD) + "}");
    }

    private static String comment(int number) {
        return "{\"id\":\"" + number + "\",\"recordId\":\"ABC-1\",\"author\":\"stub-user\",\"body\":\"comment " + number + "\"}";
    }

    private void update(HttpExchange exchange, String id) throws IOException {
        Record found = records.get(id);
        Matcher title = TITLE_FIELD.matcher(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (found == null) {
            send(exchange, 404, "{\"message\":\"no such record\"}");
        } else if (!title.find()) {
            send(exchange, 400, "{\"message\":\"no title\"}");
        } else {
            Record changed = new Record(found.id(), found.project(), unescape(title.group(1)), found.status());
            records.put(id, changed);
            send(exchange, 200, json(changed));
        }
    }

    private String json(Record record) {
        return json(record, projects.get(record.project()));
    }

    private static String json(Record record, Project project) {
        return "{\"id\":" + quote(record.id()) + ",\"project\":" + quote(record.project()) + ",\"projectClassification\":"
                + quote(project.category()) + ",\"title\":" + quote(record.title()) + ",\"status\":" + quote(record.status()) + "}";
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 == text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    out.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    private static Map<String, String> parameters(String rawQuery) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                parameters.put(eq < 0 ? pair : pair.substring(0, eq),
                        eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return parameters;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            send(exchange, status, body);
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
