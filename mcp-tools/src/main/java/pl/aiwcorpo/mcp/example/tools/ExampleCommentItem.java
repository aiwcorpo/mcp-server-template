package pl.aiwcorpo.mcp.example.tools;

import org.springframework.lang.Nullable;
import pl.aiwcorpo.mcp.example.core.domain.RecordComment;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** One comment in a result. The text is written by the users of the backend, so it is marked as data. */
public record ExampleCommentItem(String id, @Nullable String author, UntrustedText body) {

    static final int BODY_MAX_LENGTH = 2000;

    static ExampleCommentItem of(RecordComment comment) {
        return new ExampleCommentItem(comment.id(), comment.author(), UntrustedText.plain(comment.body(), BODY_MAX_LENGTH));
    }
}
