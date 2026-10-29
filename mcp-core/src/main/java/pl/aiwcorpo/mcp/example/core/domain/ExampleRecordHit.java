package pl.aiwcorpo.mcp.example.core.domain;

/** One row of a search: enough to recognise the record and open it with {@code get_record}. */
public record ExampleRecordHit(String id, String title) {
}
