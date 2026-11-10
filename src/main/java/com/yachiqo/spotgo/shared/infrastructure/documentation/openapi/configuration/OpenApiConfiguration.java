package com.yachiqo.spotgo.shared.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.models.GroupedOpenApi;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public GroupedOpenApi profilesApi() {
        return GroupedOpenApi.builder().group("01-profiles-actualizado")
                .pathsToMatch("/api/v1/profiles/**", "/api/v1/vehicles/**",
                        "/api/v1/driver-classifications/**", "/api/v1/authentication/**")
                .build();
    }

    @Bean
    public GroupedOpenApi completeApi() {
        return GroupedOpenApi.builder().group("04-backend-completo").pathsToMatch("/api/**").build();
    }

    @Bean
    public GroupedOpenApi iamApi() {
        return GroupedOpenApi.builder().group("02-iam-actualizado")
                .pathsToMatch("/api/v1/authentication/**", "/api/v1/accounts/**", "/api/v1/sessions/**", "/api/v1/audit-entries/**")
                .build();
    }
    @Bean
    public GroupedOpenApi parkingApi() {
        return GroupedOpenApi.builder().group("03-parking-actualizado")
            .pathsToMatch("/api/v1/tenants/**", "/api/v1/parking-zones/**", "/api/v1/parking-spots/**", "/api/v1/floor-plans/**",
                "/api/v1/digital-parking-maps/**", "/api/v1/reservations/**", "/api/v1/parking-sessions/**", "/api/v1/guest-parking-sessions/**", "/api/v1/authentication/**")
            .build();
    }
    // Properties
    @Value("${spring.application.name}")
    String applicationName;

    @Value("${documentation.application.description}")
    String applicationDescription;

    @Value("${documentation.application.version}")
    String applicationVersion;

    @Bean
    public OpenAPI spotgoOpenApi() {
        final String securitySchemeName = "Bearer Authentication";
        return new OpenAPI()
                .addServersItem(new Server().url("/"))
                .info(new Info()
                        .title("SpotGo Parking API")
                        .description(this.applicationDescription)
                        .version(this.applicationVersion)
                        .contact(new Contact()
                                .name("Yachiqo")
                                .email("support@yachiqo.com")
                                .url("https://www.yachiqo.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .schemaRequirement(securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));
    }
}
