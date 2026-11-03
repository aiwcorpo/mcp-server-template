package pl.aiwcorpo.mcp.example.core.domain;

import java.util.List;

/** One page of comments as the use case returns it; {@code nextStart} is {@code null} on the last page of the backend. */
public record CommentPage(List<RecordComment> comments, Integer nextStart) {

    public CommentPage {
        comments = List.copyOf(comments);
    }
}
