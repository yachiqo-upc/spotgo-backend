package com.yachiqo.spotgo.shared.infrastructure.configuration;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        var origins = new java.util.ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://spotgo-frontend.vercel.app"
        ));
        String extraOrigin = System.getenv("CORS_ALLOWED_ORIGIN");
        if (extraOrigin != null && !extraOrigin.isBlank()) {
            origins.add(extraOrigin);
        }
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
