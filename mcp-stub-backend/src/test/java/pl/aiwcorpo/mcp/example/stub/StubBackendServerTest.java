package pl.aiwcorpo.mcp.example.stub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The stub answers the way the adapter of the server expects; it is what a local run and a test stack rely on. */
class StubBackendServerTest {

    private static StubBackendServer stub;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws IOException {
        stub = StubBackendServer.start("127.0.0.1", 0);
    }

    @AfterAll
    static void stop() {
        stub.stop();
    }

    private static HttpResponse<String> send(String method, String path, String authorization, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + stub.port() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void theVersionNeedsNoCredentialAndRecordsNeedOne() throws Exception {
        assertThat(send("GET", "/api/server-info", null, null).body()).contains("\"version\":\"2.4.1\"");
        assertThat(send("GET", "/api/records/ABC-1", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/records/ABC-1", "Bearer expired-token", null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/records/ABC-1", "Bearer any-token", null).statusCode()).isEqualTo(200);
    }

    @Test
    void theWhoamiNamesTheUserForAnyTokenAndRefusesMissingAndExpiredOnes() throws Exception {
        assertThat(send("GET", "/api/me", "Bearer any-token", null).body()).contains("\"username\":\"stub-user\"");
        assertThat(send("GET", "/api/me", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/me", "Bearer expired-token", null).statusCode()).isEqualTo(401);
    }

    @Test
    void aRecordCarriesTheClassificationOfItsProject() throws Exception {
        assertThat(send("GET", "/api/records/HR-5", "Bearer t", null).body())
                .contains("\"project\":\"HR\"", "\"projectClassification\":\"Restricted\"");
        assertThat(send("GET", "/api/records/ABC-1", "Bearer t", null).body()).contains("\"projectClassification\":\"\"");
        assertThat(send("GET", "/api/records/ABC-404", "Bearer t", null).statusCode()).isEqualTo(404);
    }

    @Test
    void aSearchReadsTheProjectAndTheTextOfTheQueryTheServerBuilds() throws Exception {
        String query = URLEncoder.encode("project in (\"ABC\") AND text ~ \"report\"", StandardCharsets.UTF_8);
        String body = send("GET", "/api/records?query=" + query + "&limit=10", "Bearer t", null).body();

        assertThat(body).contains("ABC-1", "ABC-2").doesNotContain("DEF-7", "HR-5");
    }

    @Test
    void theRecordsOfAListOfIdsComeInOneAnswer() throws Exception {
        String body = send("GET", "/api/records?ids=ABC-1,HR-5,ABC-404", "Bearer t", null).body();

        assertThat(body).contains("ABC-1", "HR-5").doesNotContain("ABC-404");
    }

    @Test
    void theCommentsOfTheFirstRecordArePaged() throws Exception {
        String first = send("GET", "/api/records/ABC-1/comments?start=0&limit=10", "Bearer t", null).body();
        String second = send("GET", "/api/records/ABC-1/comments?start=10&limit=10", "Bearer t", null).body();

        assertThat(first).contains("\"hasMore\":true", "\"id\":\"10\"").doesNotContain("\"id\":\"11\"");
        assertThat(second).contains("\"hasMore\":false", "\"id\":\"12\"");
        assertThat(send("GET", "/api/records/ABC-1/comments/13", "Bearer t", null).statusCode()).isEqualTo(404);
    }

    @Test
    void aTitleIsSetAndStaysSetWhenTheCallIsRepeated() throws Exception {
        String once = send("PUT", "/api/records/DEF-7", "Bearer t", "{\"title\":\"Other team \\\"Q3\\\"\"}").body();
        String twice = send("PUT", "/api/records/DEF-7", "Bearer t", "{\"title\":\"Other team \\\"Q3\\\"\"}").body();

        assertThat(once).contains("\"title\":\"Other team \\\"Q3\\\"\"").isEqualTo(twice);
        assertThat(send("GET", "/api/records/DEF-7", "Bearer t", null).body()).isEqualTo(once);
    }
}
