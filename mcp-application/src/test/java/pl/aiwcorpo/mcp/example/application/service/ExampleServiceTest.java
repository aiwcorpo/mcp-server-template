package pl.aiwcorpo.mcp.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.RecordNotFoundException;

class ExampleServiceTest {

    private final Map<String, ExampleRecord> backend = Map.of("ABC-1", new ExampleRecord("ABC-1", "ABC", "First", "open"));
    private final ExampleService service = new ExampleService(id -> Optional.ofNullable(backend.get(id)));

    @Test
    void returnsTheRecordTheBackendShows() {
        assertThat(service.getRecord("ABC-1").title()).isEqualTo("First");
    }

    @Test
    void aRecordTheBackendDoesNotShowIsNotFoundOrDenied() {
        assertThatThrownBy(() -> service.getRecord("ABC-2")).isInstanceOf(RecordNotFoundException.class);
    }
}
