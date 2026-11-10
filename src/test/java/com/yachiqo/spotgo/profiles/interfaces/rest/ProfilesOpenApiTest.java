package com.yachiqo.spotgo.profiles.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProfilesOpenApiTest {
    @Value("${local.server.port}") int port;

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
    }
    private JsonNode api(String group) throws Exception {
        var response = get("/v3/api-docs/" + group);
        assertEquals(200, response.statusCode(), response.body());
        return new ObjectMapper().readTree(response.body());
    }

    @Test void updatedGroupExcludesLegacyControllersButKeepsAuthentication() throws Exception {
        JsonNode paths = api("01-profiles-actualizado").path("paths");
        assertTrue(paths.has("/api/v1/profiles/me"));
        assertTrue(paths.has("/api/v1/vehicles"));
        assertTrue(paths.has("/api/v1/driver-classifications/me"));
        assertTrue(paths.has("/api/v1/authentication/sign-in"));
        assertFalse(paths.has("/api/v1/receipts"));
        assertTrue(api("03-backend-completo").path("paths").has("/api/v1/receipts"));
    }

    @Test void generatedDocumentationDescribesOperationsCodesAndDistinctUuidExamples() throws Exception {
        JsonNode document = api("01-profiles-actualizado");
        JsonNode paths = document.path("paths");
        var endpoints = paths.fields();
        int checked = 0;
        while (endpoints.hasNext()) {
            var endpoint = endpoints.next();
            if (endpoint.getKey().startsWith("/api/v1/authentication")) continue;
            var operations = endpoint.getValue().fields();
            while (operations.hasNext()) {
                var operation = operations.next();
                if (!java.util.Set.of("get", "post", "put", "patch", "delete").contains(operation.getKey())) continue;
                JsonNode detail = operation.getValue();
                assertFalse(detail.path("summary").asText().isBlank(), endpoint.getKey());
                assertFalse(detail.path("description").asText().isBlank(), endpoint.getKey());
                for (String code : new String[]{"400", "403", "404", "409", "500"})
                    assertFalse(detail.path("responses").path(code).path("description").asText().isBlank(), endpoint.getKey() + " " + code);
                checked++;
            }
        }
        assertEquals(10, checked);
        assertTrue(paths.path("/api/v1/vehicles").path("post").path("responses").has("201"));
        assertTrue(paths.path("/api/v1/vehicles/{vehicleId}").path("delete").path("responses").has("204"));
        assertTrue(paths.path("/api/v1/profiles/me").path("get").path("responses").path("200").path("content").has("application/json"));
        JsonNode properties = document.path("components").path("schemas").path("ProfileResource").path("properties");
        assertNotEquals(properties.path("id").path("example").asText(), properties.path("accountId").path("example").asText());
        assertFalse(properties.path("accountId").path("description").asText().isBlank());
    }

    @Test void unauthenticatedProfileRequestMatchesDocumented401() throws Exception {
        assertEquals(401, get("/api/v1/profiles/me").statusCode());
    }
}
