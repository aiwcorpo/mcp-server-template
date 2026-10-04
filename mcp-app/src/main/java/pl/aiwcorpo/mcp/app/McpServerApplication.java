package pl.aiwcorpo.mcp.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point — the ONLY deployable artifact (Spring Boot fat-jar).
 *
 * <p>{@code scanBasePackages = "pl.aiwcorpo.mcp"} picks up the {@code @Service}/{@code @Component}
 * beans from mcp-tools and mcp-integration. Cross-cutting beans (security, audit, error) come from
 * mcp-platform-starter via auto-configuration — no local wiring needed. The app serves plain HTTP;
 * TLS is terminated at the nginx gateway (no in-app SSL).
 */
@SpringBootApplication(scanBasePackages = "pl.aiwcorpo.mcp")
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
