package pl.aiwcorpo.mcp.example.tools;

import org.springframework.lang.Nullable;
import pl.aiwcorpo.mcp.example.core.domain.ExampleRecord;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * Result of {@code get_record}. Text written in the backend system is an {@link UntrustedText}; a field the
 * system may leave empty is {@code @Nullable}, otherwise the result schema makes it required and a record
 * without it ends as {@code internal_error}. No Jackson annotations on the components: the result schema is
 * built from their names.
 */
public record ExampleRecordView(String id, @Nullable String project, UntrustedText title, @Nullable String status) {

    static final int TITLE_MAX_LENGTH = 500;

    /**
     * Without a server filter the factory does the whole job. A server that strips markup from the text goes
     * conversion -> {@code TextHygiene.normalize} -> {@code UntrustedText.cut} -> filter -> {@code plainCut}.
     */
    static ExampleRecordView of(ExampleRecord record) {
        return new ExampleRecordView(record.id(), record.project(),
                UntrustedText.plain(record.title(), TITLE_MAX_LENGTH), record.status());
    }
}
