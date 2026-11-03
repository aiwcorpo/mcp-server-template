package pl.aiwcorpo.mcp.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.example.core.domain.CommentPage;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.NotFoundOrDeniedException;
import pl.aiwcorpo.mcp.example.core.domain.RecordComment;
import pl.aiwcorpo.mcp.example.core.domain.ServerInfo;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;

class ExampleServiceTest {

    @Test
    void passesTheQueryAndTheLimitToTheBackend() {
        String[] seen = new String[2];
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public List<ExampleRecordHit> search(String query, int limit) {
                seen[0] = query;
                seen[1] = String.valueOf(limit);
                return List.of(new ExampleRecordHit("ABC-1", "First"));
            }
        });

        assertThat(service.searchRecords("q")).containsExactly(new ExampleRecordHit("ABC-1", "First"));
        assertThat(seen).containsExactly("q", String.valueOf(ExampleUseCase.MAX_HITS));
    }

    @Test
    void cutsAnAnswerLongerThanTheLimitWhateverTheBackendDid() {
        List<ExampleRecordHit> many = IntStream.range(0, ExampleUseCase.MAX_HITS + 10)
                .mapToObj(i -> new ExampleRecordHit("ABC-" + i, "t")).toList();
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public List<ExampleRecordHit> search(String query, int limit) {
                return many;
            }
        });

        assertThat(service.searchRecords("q")).hasSize(ExampleUseCase.MAX_HITS);
    }

    @Test
    void aCommentFiledUnderAnotherRecordIsDroppedFromAPageButTheBackendPositionStays() {
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public CommentPage listComments(String recordId, int start, int limit) {
                return new CommentPage(List.of(new RecordComment("1", "ABC-1", "a", "mine"),
                        new RecordComment("2", "ABC-2", "a", "another record's")), 10);
            }
        });

        CommentPage page = service.listComments("ABC-1", 0);

        assertThat(page.comments()).extracting(RecordComment::id).containsExactly("1");
        assertThat(page.nextStart()).isEqualTo(10);
    }

    @Test
    void aCommentIsServedOnlyUnderTheRecordItBelongsToAndWithTheIdAsked() {
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public Optional<RecordComment> findComment(String recordId, String commentId) {
                return Optional.of(new RecordComment("7", "ABC-2", "a", "text"));
            }
        });

        assertThatThrownBy(() -> service.getComment("ABC-1", "7")).isInstanceOf(NotFoundOrDeniedException.class);
        assertThatThrownBy(() -> service.getComment("ABC-2", "8")).isInstanceOf(NotFoundOrDeniedException.class);
        assertThat(service.getComment("ABC-2", "7").body()).isEqualTo("text");
    }

    private abstract static class StubPort implements pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort {

        @Override
        public java.util.Map<String, pl.aiwcorpo.mcp.example.core.domain.ExampleRecord> findAllById(java.util.Collection<String> recordIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<pl.aiwcorpo.mcp.example.core.domain.ExampleRecord> findById(String recordId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ExampleRecordHit> search(String query, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerInfo serverInfo() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommentPage listComments(String recordId, int start, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RecordComment> findComment(String recordId, String commentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ExampleRecord> updateTitle(String recordId, String title) {
            throw new UnsupportedOperationException();
        }
    }
}
