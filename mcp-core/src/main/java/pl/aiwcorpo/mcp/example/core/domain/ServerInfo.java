package pl.aiwcorpo.mcp.example.core.domain;

/**
 * What the backend says about itself without a login. The version is untrusted text: the probe only parses it to
 * numbers, and the starter cleans what it logs.
 *
 * @param version product version, e.g. {@code 2.4.1}; {@code null} when the answer has none
 */
public record ServerInfo(String version) {
}
