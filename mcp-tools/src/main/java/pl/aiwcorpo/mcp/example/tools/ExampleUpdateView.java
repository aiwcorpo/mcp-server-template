package pl.aiwcorpo.mcp.example.tools;

import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/** Result of {@code update_record}: the record as the backend has it after the change. */
public record ExampleUpdateView(String id, UntrustedText title) {

    static ExampleUpdateView of(ExampleRecord record) {
        return new ExampleUpdateView(record.id(), UntrustedText.plain(record.title(), ExampleRecordView.TITLE_MAX_LENGTH));
    }
}
