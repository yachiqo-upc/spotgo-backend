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
        return GroupedOpenApi.builder().group("01-profiles")
                .pathsToMatch("/api/v1/profiles/**", "/api/v1/vehicles/**",
                        "/api/v1/driver-classifications/**", "/api/v1/authentication/**")
                .build();
    }

    @Bean
    public GroupedOpenApi completeApi() {
        return GroupedOpenApi.builder().group("06-complete-backend").pathsToMatch("/api/v1/accounts/**", "/api/v1/authentication/**", "/api/v1/sessions/**", "/api/v1/audit-entries/**", "/api/v1/profiles/**", "/api/v1/vehicles/**", "/api/v1/driver-classifications/**", "/api/v1/tenants/**", "/api/v1/parking-zones/**", "/api/v1/parking-spots/**", "/api/v1/floor-plans/**", "/api/v1/digital-parking-maps/**", "/api/v1/reservations/**", "/api/v1/parking-sessions/**", "/api/v1/guest-parking-sessions/**", "/api/v1/payment-tokens/**", "/api/v1/digital-payments/**", "/api/v1/payment-attempts/**", "/api/v1/subscriptions/**", "/api/v1/additional-charges/**", "/api/v1/outstanding-balances/**", "/api/v1/refunds/**", "/api/v1/billing-documents/**", "/api/v1/occupancy-sensors/**", "/api/v1/occupancy-readings/**", "/api/v1/sensor-health-events/**", "/api/v1/occupancy-conflicts/**", "/api/v1/occupancy-alerts/**", "/api/v1/capacity-snapshots/**", "/api/v1/occupancy-reports/**").build();
    }

    @Bean
    public GroupedOpenApi iamApi() {
        return GroupedOpenApi.builder().group("02-identity-access")
                .pathsToMatch("/api/v1/authentication/**", "/api/v1/accounts/**", "/api/v1/sessions/**", "/api/v1/audit-entries/**")
                .build();
    }
    @Bean
    public GroupedOpenApi parkingApi() {
        return GroupedOpenApi.builder().group("03-parking")
            .pathsToMatch("/api/v1/tenants/**", "/api/v1/parking-zones/**", "/api/v1/parking-spots/**", "/api/v1/floor-plans/**",
                "/api/v1/digital-parking-maps/**", "/api/v1/reservations/**", "/api/v1/parking-sessions/**", "/api/v1/guest-parking-sessions/**", "/api/v1/authentication/**")
            .build();
    }
    @Bean
    public GroupedOpenApi paymentsApi() {
        return GroupedOpenApi.builder().group("04-payments-billing")
            .pathsToMatch("/api/v1/payment-tokens/**", "/api/v1/digital-payments/**", "/api/v1/payment-attempts/**", "/api/v1/subscriptions/**",
                "/api/v1/additional-charges/**", "/api/v1/outstanding-balances/**", "/api/v1/refunds/**", "/api/v1/billing-documents/**", "/api/v1/authentication/**")
            .build();
    }
    @Bean
    public GroupedOpenApi monitoringApi(){
        return GroupedOpenApi.builder().group("05-monitoring")
            .pathsToMatch("/api/v1/occupancy-sensors/**","/api/v1/occupancy-readings/**","/api/v1/sensor-health-events/**","/api/v1/occupancy-conflicts/**","/api/v1/occupancy-alerts/**","/api/v1/capacity-snapshots/**","/api/v1/occupancy-reports/**","/api/v1/authentication/**").build();
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
