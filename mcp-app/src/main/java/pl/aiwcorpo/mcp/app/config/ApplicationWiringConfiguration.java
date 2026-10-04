package pl.aiwcorpo.mcp.app.config;

import pl.aiwcorpo.mcp.application.service.ExampleService;
import pl.aiwcorpo.mcp.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.core.port.out.ExampleResourcePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns the framework-free use-cases from mcp-application into Spring beans, wiring them to the
 * outbound-port adapters from mcp-integration. This is where the hexagon is assembled: the domain
 * stays Spring-free, and composition happens only in the bootstrap module.
 */
@Configuration
public class ApplicationWiringConfiguration {

    @Bean
    ExampleUseCase exampleUseCase(ExampleResourcePort resource) {
        return new ExampleService(resource);
    }
}
