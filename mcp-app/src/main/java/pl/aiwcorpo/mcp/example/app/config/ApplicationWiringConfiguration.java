package pl.aiwcorpo.mcp.example.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.aiwcorpo.mcp.example.application.service.ExampleService;
import pl.aiwcorpo.mcp.example.core.domain.ObservedVersions;
import pl.aiwcorpo.mcp.example.core.port.in.ExampleUseCase;
import pl.aiwcorpo.mcp.example.core.port.out.ExampleResourcePort;

/** Assembles the hexagon: framework-free use cases from mcp-application, adapters from mcp-integration. */
@Configuration
public class ApplicationWiringConfiguration {

    @Bean
    ObservedVersions observedVersions() {
        return new ObservedVersions();
    }

    @Bean
    ExampleUseCase exampleUseCase(ExampleResourcePort resource) {
        return new ExampleService(resource);
    }
}
