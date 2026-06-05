package com.dduk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.frontend.path:}")
    private String configuredFrontendPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (configuredFrontendPath != null && !configuredFrontendPath.trim().isEmpty()) {
            if (configuredFrontendPath.startsWith("classpath:")) {
                registry.addResourceHandler("/**")
                        .addResourceLocations(configuredFrontendPath);
                return;
            } else {
                String normalizedPath = Paths.get(configuredFrontendPath).toAbsolutePath().toUri().toString();
                if (!normalizedPath.endsWith("/")) {
                    normalizedPath += "/";
                }
                registry.addResourceHandler("/**")
                        .addResourceLocations(normalizedPath);
                return;
            }
        }

        Path frontendPath = resolveFrontendPath();
        String defaultLocation = frontendPath.toUri().toString();
        if (!defaultLocation.endsWith("/")) {
            defaultLocation += "/";
        }
        registry.addResourceHandler("/**")
                .addResourceLocations(defaultLocation);
    }

    private Path resolveFrontendPath() {
        Path currentPath = Paths.get("").toAbsolutePath().normalize();

        for (Path path = currentPath; path != null; path = path.getParent()) {
            Path frontendPath = path.resolve("frontend");
            if (Files.isRegularFile(frontendPath.resolve("index.html"))) {
                return frontendPath;
            }
        }

        return currentPath.resolve("frontend");
    }
}
