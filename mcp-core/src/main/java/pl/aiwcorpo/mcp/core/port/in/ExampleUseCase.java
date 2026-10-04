package pl.aiwcorpo.mcp.core.port.in;

import pl.aiwcorpo.mcp.core.domain.ExampleRecord;

/**
 * Inbound port (use-case interface). Implemented in mcp-application, called by mcp-tools.
 * Defines WHAT the system can do in business terms, independent of MCP/HTTP/Spring.
 *
 * <p>The three operations are deliberately one of each kind — read, reversible change, irreversible
 * change — so the seed shows a tool at every risk level.
 */
public interface ExampleUseCase {

    /**
     * Look up a single record by its id.
     *
     * @throws IllegalArgumentException if no record exists for the id
     */
    ExampleRecord getById(String id);

    /**
     * Change a record's name. Reversible: rename it back.
     *
     * @throws IllegalArgumentException if no record exists for the id or the name is not acceptable
     */
    ExampleRecord rename(String id, String newName);

    /**
     * Remove every record. Irreversible.
     *
     * @return how many records were removed
     */
    int purgeAll();
}
