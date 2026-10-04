package pl.aiwcorpo.mcp.core.domain;

/**
 * Domain model (value object). Pure Java, no framework annotations — encapsulates the data and
 * invariants the use-cases operate on. Prefer immutable records.
 *
 * <p>Replace with your real domain (e.g. {@code JiraIssue}, {@code Employee}).
 */
public record ExampleRecord(String id, String name, String status) {

    public ExampleRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
