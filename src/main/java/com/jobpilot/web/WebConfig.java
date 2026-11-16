package com.jobpilot.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the Chrome extension and the bundled UI (same port only) call the API. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${server.port:8765}")
    private int port;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("chrome-extension://*", "http://127.0.0.1:" + port, "http://localhost:" + port)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
