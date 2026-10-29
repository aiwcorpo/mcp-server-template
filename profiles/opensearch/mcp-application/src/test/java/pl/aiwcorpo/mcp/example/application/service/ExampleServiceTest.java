package pl.aiwcorpo.mcp.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;

class ExampleServiceTest {

    @Test
    void passesTheIndexNamesTheTextAndTheLimitToTheBackend() {
        Object[] seen = new Object[3];
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public List<ExampleRecordHit> search(List<String> indices, String text, int limit) {
                seen[0] = indices;
                seen[1] = text;
                seen[2] = limit;
                return List.of(new ExampleRecordHit("logs-a-1", "First"));
            }
        });

        assertThat(service.searchRecords(List.of("logs-a-1"), "\"q\"")).containsExactly(new ExampleRecordHit("logs-a-1", "First"));
        assertThat(seen).containsExactly(List.of("logs-a-1"), "\"q\"", ExampleUseCase.MAX_HITS);
    }

    @Test
    void cutsAnAnswerLongerThanTheLimitWhateverTheBackendDid() {
        List<ExampleRecordHit> many = IntStream.range(0, ExampleUseCase.MAX_HITS + 10)
                .mapToObj(i -> new ExampleRecordHit("logs-a-" + i, "t")).toList();
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public List<ExampleRecordHit> search(List<String> indices, String text, int limit) {
                return many;
            }
        });

        assertThat(service.searchRecords(List.of("logs-a-1"), "\"q\"")).hasSize(ExampleUseCase.MAX_HITS);
    }

    private abstract static class StubPort implements ExampleResourcePort {

        @Override
        public Map<String, ExampleRecord> findAllById(Collection<String> recordIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ExampleRecord> findById(String recordId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String resolveIndices(String expression) {
            throw new UnsupportedOperationException();
        }
    }
}
