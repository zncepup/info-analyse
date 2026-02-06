package com.infoanalyse.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.nio.file.Path;

@Configuration
public class StaticResourceConfig implements WebFluxConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String outputLocation = Path.of("output").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/output/**")
                .addResourceLocations(outputLocation);
    }
}
