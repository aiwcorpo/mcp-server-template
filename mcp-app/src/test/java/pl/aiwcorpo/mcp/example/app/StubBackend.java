package pl.aiwcorpo.mcp.example.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * The backend system in tests: a real HTTP server on loopback, so the adapter runs with its real client,
 * timeouts and error mapping. Records it knows:
 * <ul>
 *   <li>{@code ABC-1}: visible, with an extra field the server must not pass on;</li>
 *   <li>{@code ABC-403}: exists but the caller may not see it (403);</li>
 *   <li>{@code ABC-500}: the backend fails and echoes the caller's Authorization header in the body;</li>
 *   <li>{@code ABC-BADJSON}: 200 with a body that is not JSON and carries {@link #BODY_MARKER};</li>
 *   <li>{@code ABC-NOID}: 200 with a record without id;</li>
 *   <li>{@code ABC-302}: redirect to a second server, which records whether anything followed;</li>
 *   <li>{@code ABC-SLOW}: answers after {@link #SLOW_MILLIS};</li>
 *   <li>{@code ABC-HUGE}: 200 with a body of {@link #HUGE_BYTES};</li>
 *   <li>anything else: 404.</li>
 * </ul>
 * Any request with {@link #EXPIRED_CREDENTIAL} is answered 401, like a revoked token.
 */
final class StubBackend {

    static final String EXPIRED_CREDENTIAL = "Bearer expired-pat-of-jkowalski";
    static final String BODY_MARKER = "SECRETBODYTOKEN123";
    static final long SLOW_MILLIS = 4_000;
    static final int HUGE_BYTES = 256 * 1024;

    private final HttpServer server;
    private final HttpServer elsewhere;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> elsewhereHits = new CopyOnWriteArrayList<>();

    private StubBackend(HttpServer server, HttpServer elsewhere) {
        this.server = server;
        this.elsewhere = elsewhere;
    }

    static StubBackend start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            HttpServer elsewhere = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            StubBackend backend = new StubBackend(server, elsewhere);
            server.createContext("/api/records/", backend::handle);
            elsewhere.createContext("/", exchange -> {
                backend.elsewhereHits.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
                respond(exchange, 200, "{\"id\":\"ABC-302\",\"title\":\"from elsewhere\"}");
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

    void reset() {
        authorizations.clear();
        elsewhereHits.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(String.valueOf(authorization));
        String id = exchange.getRequestURI().getPath().substring("/api/records/".length());
        if (EXPIRED_CREDENTIAL.equals(authorization)) {
            respond(exchange, 401, "{\"message\":\"token expired\"}");
        } else if ("ABC-1".equals(id)) {
            respond(exchange, 200, """
                    {"id":"ABC-1","project":"ABC","title":"Quarterly report","status":"open",
                     "internalNotes":"not for the model"}""");
        } else if ("ABC-BADJSON".equals(id)) {
            respond(exchange, 200, "<html>" + BODY_MARKER + " login</html>");
        } else if ("ABC-NOID".equals(id)) {
            respond(exchange, 200, "{\"title\":\"no id\"}");
        } else if ("ABC-302".equals(id)) {
            exchange.getResponseHeaders().set("Location",
                    "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/api/records/ABC-302");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        } else if ("ABC-SLOW".equals(id)) {
            sleep();
            respond(exchange, 200, "{\"id\":\"ABC-SLOW\",\"title\":\"late\"}");
        } else if ("ABC-HUGE".equals(id)) {
            respond(exchange, 200, "{\"id\":\"ABC-HUGE\",\"title\":\"" + "x".repeat(HUGE_BYTES) + "\"}");
        } else if ("ABC-403".equals(id)) {
            respond(exchange, 403, "{\"message\":\"forbidden\"}");
        } else if ("ABC-500".equals(id)) {
            respond(exchange, 500, "{\"error\":\"boom\",\"seen\":\"" + authorization + "\"}");
        } else {
            respond(exchange, 404, "{\"message\":\"no such record\"}");
        }
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
