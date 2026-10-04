package pl.aiwcorpo.mcp.integration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.aiwcorpo.mcp.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.core.port.out.ExampleResourcePort;

/**
 * Static mock of the backend, active with {@code example.backend.mode=mock}. Lets the whole server
 * — identity, risk flags, confirmation of destructive calls — be run and demonstrated with no
 * backend and no PAT. The data lives in memory and comes back on restart.
 *
 * <p>Not for production: there is no per-user authorization here, because there is no backend to
 * enforce it. The {@code prod} profile does not enable this adapter.
 */
@Component
@ConditionalOnProperty(name = "example.backend.mode", havingValue = "mock")
public class InMemoryExampleResourceAdapter implements ExampleResourcePort {

    private final Map<String, ExampleRecord> records = new ConcurrentHashMap<>();

    public InMemoryExampleResourceAdapter() {
        seed();
    }

    /** Restores the fixed demo data set. */
    public final void seed() {
        records.clear();
        save(new ExampleRecord("demo-1", "Quarterly report", "active"));
        save(new ExampleRecord("demo-2", "Customer onboarding checklist", "active"));
        save(new ExampleRecord("demo-3", "Decommissioned service runbook", "archived"));
    }

    @Override
    public Optional<ExampleRecord> findById(String id) {
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public ExampleRecord save(ExampleRecord record) {
        records.put(record.id(), record);
        return record;
    }

    @Override
    public int deleteAll() {
        int count = records.size();
        records.clear();
        return count;
    }
}
