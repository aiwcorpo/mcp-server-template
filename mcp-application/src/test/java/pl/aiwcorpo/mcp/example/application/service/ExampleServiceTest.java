package pl.aiwcorpo.mcp.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;

class ExampleServiceTest {

    @Test
    void asksTheBackendForAtMostTheLimitAndPassesProjectAndText() {
        String[] seen = new String[3];
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public List<ExampleRecordHit> search(String project, String text, int limit) {
                seen[0] = project;
                seen[1] = text;
                seen[2] = String.valueOf(limit);
                return List.of(new ExampleRecordHit("ABC-1", "First"));
            }
        });

        assertThat(service.searchRecords("ABC", "first")).containsExactly(new ExampleRecordHit("ABC-1", "First"));
        assertThat(seen).containsExactly("ABC", "first", String.valueOf(ExampleUseCase.MAX_HITS));
    }

    @Test
    void cutsAnAnswerLongerThanTheLimitWhateverTheBackendDid() {
        List<ExampleRecordHit> many = IntStream.range(0, ExampleUseCase.MAX_HITS + 10)
                .mapToObj(i -> new ExampleRecordHit("ABC-" + i, "t")).toList();
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public List<ExampleRecordHit> search(String project, String text, int limit) {
                return many;
            }
        });

        assertThat(service.searchRecords("ABC", "t")).hasSize(ExampleUseCase.MAX_HITS);
    }

    private abstract static class StubPort implements pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort {

        @Override
        public java.util.Optional<pl.aiwcorpo.mcp.example.core.domain.ExampleRecord> findById(String recordId) {
            throw new UnsupportedOperationException();
        }
    }
}
