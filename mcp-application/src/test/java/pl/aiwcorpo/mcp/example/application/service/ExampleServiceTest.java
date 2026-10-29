package pl.aiwcorpo.mcp.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
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
                return List.of(new ExampleRecordHit("ABC-1", "ABC", "First"));
            }
        });

        assertThat(service.searchRecords("q", "ABC")).containsExactly(new ExampleRecordHit("ABC-1", "ABC", "First"));
        assertThat(seen).containsExactly("q", String.valueOf(ExampleUseCase.MAX_HITS));
    }

    @Test
    void cutsAnAnswerLongerThanTheLimitWhateverTheBackendDid() {
        List<ExampleRecordHit> many = IntStream.range(0, ExampleUseCase.MAX_HITS + 10)
                .mapToObj(i -> new ExampleRecordHit("ABC-" + i, "ABC", "t")).toList();
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public List<ExampleRecordHit> search(String query, int limit) {
                return many;
            }
        });

        assertThat(service.searchRecords("q", "ABC")).hasSize(ExampleUseCase.MAX_HITS);
    }

    @Test
    void keepsOnlyTheRowsOfTheRequestedProject() {
        ExampleService service = new ExampleService(new StubPort() {
            @Override
            public List<ExampleRecordHit> search(String query, int limit) {
                return List.of(new ExampleRecordHit("ABC-1", "ABC", "a"), new ExampleRecordHit("DEF-1", "DEF", "b"),
                        new ExampleRecordHit("XYZ-1", null, "no project"));
            }
        });

        assertThat(service.searchRecords("q", "ABC")).extracting(ExampleRecordHit::id).containsExactly("ABC-1");
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
    }
}
