package pl.aiwcorpo.mcp.example.core.domain;

/**
 * One row of a search: enough to recognise the record and open it with {@code get_record}. {@code project} is the
 * container the system reports for the row; it may be {@code null}.
 */
public record ExampleRecordHit(String id, String project, String title) {
}
