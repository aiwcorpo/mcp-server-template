package pl.aiwcorpo.mcp.example.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.resource.ResourceContext;
import pl.aiwcorpo.mcp.platform.test.TestResourceContext;

/** What the tool class checks itself; the schemas, the policy and the wire are tested in mcp-app, where the policies are. */
class ExampleToolTest {

    private final ExampleTool tool = new ExampleTool(new NoBackendUseCase(), null, null, null);

    @Test
    void searchRejectsABlankOrTooLongTextWithAFixedMessage() {
        for (String text : new String[] {"", "  ", "a".repeat(201)}) {
            assertThatThrownBy(() -> tool.searchRecords("ABC", text))
                    .isInstanceOf(InvalidToolArgumentException.class)
                    .hasMessage("text must be 1-200 characters");
        }
        assertThatThrownBy(() -> tool.searchRecords("ABC", null)).isInstanceOf(InvalidToolArgumentException.class);
    }

    @Test
    void getRecordReturnsTheRecordTheResolverFetched() {
        ExampleRecord record = new ExampleRecord("ABC-1", "ABC", "", "Quarterly report", "open");
        try (ResourceContext context = TestResourceContext.open("get_record", Map.of("recordId", "ABC-1"))
                .withFetched(ExampleResourceModel.RECORD_TYPE, "ABC-1", record, null)) {
            ExampleRecordView view = tool.getRecord("ABC-1");

            assertThat(view.id()).isEqualTo("ABC-1");
            assertThat(view.project()).isEqualTo("ABC");
            assertThat(view.status()).isEqualTo("open");
        }
    }

    /** A use case that must not be called: these tests only look at what the tool class checks itself. */
    private static final class NoBackendUseCase implements pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase {

        @Override
        public List<pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit> searchRecords(String query) {
            return List.of();
        }

        @Override
        public pl.aiwcorpo.mcp.example.core.domain.CommentPage listComments(String recordId, int start) {
            throw new UnsupportedOperationException();
        }

        @Override
        public pl.aiwcorpo.mcp.example.core.domain.RecordComment getComment(String recordId, String commentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExampleRecord updateTitle(String recordId, String title) {
            throw new UnsupportedOperationException();
        }
    }
}
