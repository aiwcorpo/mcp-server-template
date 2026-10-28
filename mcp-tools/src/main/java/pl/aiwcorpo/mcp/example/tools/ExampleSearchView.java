package pl.aiwcorpo.mcp.example.tools;

import java.util.List;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecordHit;
import pl.aiwcorpo.mcp.example.core.domain.ExampleResourceModel;
import pl.aiwcorpo.mcp.platform.resource.ResourceRef;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * Result of {@code search_records}. The id of a hit is marked {@link ResourceRef}: after the tool runs the starter
 * resolves every hit and drops the ones the policy refuses, so the list never shows more than {@code get_record}
 * would. No count from the backend: it is not corrected after filtering and would disclose what was removed.
 */
public record ExampleSearchView(List<Hit> hits) {

    static final int TITLE_MAX_LENGTH = 200;

    public record Hit(@ResourceRef(type = ExampleResourceModel.RECORD_TYPE) String id, UntrustedText title) {
    }

    static ExampleSearchView of(List<ExampleRecordHit> hits) {
        return new ExampleSearchView(hits.stream()
                .map(hit -> new Hit(hit.id(), UntrustedText.plain(hit.title(), TITLE_MAX_LENGTH)))
                .toList());
    }
}
