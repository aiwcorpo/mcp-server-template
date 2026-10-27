package pl.aiwcorpo.mcp.example.core.port.in;

import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;

/** Inbound port: what the server can do, in business terms. Implemented in mcp-application, called by mcp-tools. */
public interface ExampleUseCase {

    /**
     * @throws pl.aiwcorpo.mcp.example.core.domain.RecordNotFoundException when the record does not exist or
     *         the caller's backend account may not see it
     */
    ExampleRecord getRecord(String recordId);
}
