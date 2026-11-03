package pl.aiwcorpo.mcp.example.core.domain;

/**
 * A comment of a record. {@code recordId} is what the backend says the comment belongs to: a child id is only served
 * under the record that was judged, so the claim is compared with the record asked about, never trusted.
 */
public record RecordComment(String id, String recordId, String author, String body) {

    public RecordComment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    public boolean belongsTo(String recordId) {
        return recordId != null && recordId.equals(this.recordId);
    }
}
