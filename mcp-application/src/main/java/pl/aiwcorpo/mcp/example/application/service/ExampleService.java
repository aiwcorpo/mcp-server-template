package pl.aiwcorpo.mcp.example.application.service;

import java.util.List;
import pl.aiwcorpo.mcp.example.core.domain.CommentPage;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.NotFoundOrDeniedException;
import pl.aiwcorpo.mcp.example.core.domain.RecordComment;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;

/** Use cases of the server. A plain class: mcp-app makes it a bean, so this module stays free of Spring. */
public class ExampleService implements ExampleUseCase {

    private final ExampleResourcePort resource;

    public ExampleService(ExampleResourcePort resource) {
        this.resource = resource;
    }

    @Override
    public List<ExampleRecordHit> searchRecords(String query) {
        List<ExampleRecordHit> hits = resource.search(query, MAX_HITS);
        return hits.size() > MAX_HITS ? hits.subList(0, MAX_HITS) : hits;
    }

    @Override
    public CommentPage listComments(String recordId, int start) {
        CommentPage page = resource.listComments(recordId, start, COMMENT_PAGE_SIZE);
        // The endpoint of the backend is asked for the comments of this record, but what it answers is checked: a
        // comment filed under another record must not be shown as a comment of the judged one.
        return new CommentPage(page.comments().stream().filter(comment -> comment.belongsTo(recordId)).toList(),
                page.nextStart());
    }

    @Override
    public RecordComment getComment(String recordId, String commentId) {
        return resource.findComment(recordId, commentId)
                .filter(comment -> commentId.equals(comment.id()) && comment.belongsTo(recordId))
                .orElseThrow(NotFoundOrDeniedException::new);
    }

    @Override
    public ExampleRecord updateTitle(String recordId, String title) {
        return resource.updateTitle(recordId, title).orElseThrow(NotFoundOrDeniedException::new);
    }
}
