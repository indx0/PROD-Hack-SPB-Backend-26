package ru.example.prodbackend.integration;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.example.prodbackend.auth.dto.AuthenticationResponse;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends AuthIntegrationTestSupport {

    @Test
    void metricsAreClosedWhenScrapeTokenIsNotConfigured() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validAccessTokenAuthenticatesUser() throws Exception {
        AuthenticationResponse tokens = registerUser();
        assertAccessTokenWorks(tokens.getAccessToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty", "malformed", "expired", "wrong-signature", "refresh", "unknown-user"})
    void protectedEndpointRejectsInvalidAccessToken(String scenario) throws Exception {
        AuthenticationResponse tokens = registerUser();
        String token = switch (scenario) {
            case "missing" -> null;
            case "empty" -> "";
            case "malformed" -> "not-a-jwt";
            case "expired" -> signedAccessToken(EMAIL, Instant.EPOCH);
            case "wrong-signature" -> Jwts.builder()
                    .subject(EMAIL)
                    .expiration(Date.from(Instant.now().plusSeconds(60)))
                    .signWith(Jwts.SIG.HS256.key().build(), Jwts.SIG.HS256)
                    .compact();
            case "refresh" -> tokens.getRefreshToken();
            case "unknown-user" -> signedAccessToken("unknown@example.com", Instant.now().plusSeconds(60));
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };

        MockHttpServletRequestBuilder request = get("/api/demo/authping");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        assertErrorResponse(mockMvc.perform(request), 401, "UNAUTHORIZED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        assertEquals(1L, refreshTokenRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {REGISTER_URL, AUTHENTICATE_URL})
    void publicAuthEndpointsAcceptExpiredAccessToken(String endpoint) throws Exception {
        registerUser();
        String email = endpoint.equals(REGISTER_URL) ? "new@example.com" : EMAIL;

        AuthenticationResponse tokens = readTokenPair(mockMvc.perform(post(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedAccessToken(EMAIL, Instant.EPOCH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", PASSWORD,
                        "firstName", "Alice",
                        "lastName", "Smith"
                )))));

        assertAccessTokenWorks(tokens.getAccessToken());
        assertEquals(2L, refreshTokenRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui/index.html",
            "/js/openapi-to-markdown.js"})
    void documentationIsAccessibleWithoutAccessToken(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint)).andExpect(status().isOk());
        assertDatabaseIsEmpty();
    }

    @Test
    void docsRedirectIsAccessibleWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/docs")).andExpect(status().is3xxRedirection());
        assertDatabaseIsEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {REGISTER_URL, AUTHENTICATE_URL, REFRESH_URL, LOGOUT_URL, LOGOUT_ALL_URL})
    void publicAuthRoutesRequireAuthenticationForGetRequests(String endpoint) throws Exception {
        assertErrorResponse(mockMvc.perform(get(endpoint)), 401, "UNAUTHORIZED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        assertDatabaseIsEmpty();
    }

    @Test
    void openApiDescribesUserEndpointsAtTheirNewAddresses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/user/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/user/me'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/user/change-password'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/profile']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/change-password']").doesNotExist());
    }

    @Test
    void openApiDescribesMoveProjectResponsesAndErrors() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        String collection = "$.paths['/api/move-projects']";
        String project = "$.paths['/api/move-projects/{projectId}']";

        response.andExpect(jsonPath(collection + ".post.responses['201'].content['application/json'].schema['$ref']").value("#/components/schemas/MoveProjectResponse"))
                .andExpect(jsonPath(collection + ".get.responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/PageResponseMoveProjectResponse"))
                .andExpect(jsonPath(project + ".delete.responses['204'].content").doesNotExist());

        for (String operation : new String[]{collection + ".post", collection + ".get", project + ".get", project + ".patch", project + ".delete"}) {
            response.andExpect(jsonPath(operation + ".responses['401'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"));
        }
        for (String operation : new String[]{project + ".get", project + ".patch", project + ".delete"}) {
            response.andExpect(jsonPath(operation + ".responses['400'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"))
                    .andExpect(jsonPath(operation + ".responses['404'].description").value("MOVE_PROJECT_NOT_FOUND: проект не существует или принадлежит другому пользователю"));
        }
        response.andExpect(jsonPath(collection + ".get.responses['422'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(project + ".patch.responses['422'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "LIST", "GET", "PATCH", "DELETE"})
    void moveProjectEndpointsRequireAccessToken(String operation) throws Exception {
        String path = "/api/move-projects";
        if (!operation.equals("POST") && !operation.equals("LIST")) {
            path += "/550e8400-e29b-41d4-a716-446655440000";
        }
        var method = org.springframework.http.HttpMethod.valueOf(operation.equals("LIST") ? "GET" : operation);
        var requestBuilder = request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}");

        assertErrorResponse(mockMvc.perform(requestBuilder), 401, "UNAUTHORIZED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        assertDatabaseIsEmpty();
    }

    @Test
    void openApiDescribesGenericPagesWithTypedItemsAndSharedParameters() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String item : new String[]{"MoveProjectResponse", "BoxResponse", "RoomResponse", "MoveTaskResponse", "TaskTemplateResponse", "ChecklistItemResponse", "ProjectEventResponse"}) {
            String schema = "$.components.schemas.PageResponse" + item;
            response.andExpect(jsonPath(schema + ".properties.content.items['$ref']").value("#/components/schemas/" + item))
                    .andExpect(jsonPath(schema + ".properties.page.type").value("integer"))
                    .andExpect(jsonPath(schema + ".properties.size.type").value("integer"))
                    .andExpect(jsonPath(schema + ".properties.totalElements.type").value("integer"))
                    .andExpect(jsonPath(schema + ".properties.totalPages.type").value("integer"));
        }
        String boxes = "$.paths['/api/projects/{projectId}/boxes'].get";
        response.andExpect(jsonPath(boxes + ".responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/PageResponseBoxResponse"))
                .andExpect(jsonPath(boxes + ".responses['422'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"));
        for (String path : new String[]{"/api/move-projects", "/api/projects/{projectId}/boxes"}) {
            String parameters = "$.paths['" + path + "'].get.parameters";
            response.andExpect(jsonPath(parameters + "[?(@.name == 'page')].schema.default").value(org.hamcrest.Matchers.contains(0)))
                    .andExpect(jsonPath(parameters + "[?(@.name == 'size')].schema.default").value(org.hamcrest.Matchers.contains(20)))
                    .andExpect(jsonPath(parameters + "[?(@.name == 'size')].schema.maximum").value(org.hamcrest.Matchers.contains(100)))
                    .andExpect(jsonPath(parameters + "[?(@.name == 'sort')]").isEmpty());
        }
    }

    @Test
    void openApiDescribesUnifiedBoxErrors() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        String box = "$.paths['/api/projects/{projectId}/boxes/{boxId}']";
        for (String operation : new String[]{box + ".get", box + ".patch", box + ".delete", "$.paths['/api/projects/{projectId}/boxes/{boxId}/status'].patch"}) {
            response.andExpect(jsonPath(operation + ".responses['404'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"));
        }
        response.andExpect(jsonPath(box + ".patch.responses['409'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath(box + ".patch.responses['422'].content['application/json'].schema['$ref']").value("#/components/schemas/ErrorResponse"));
    }
}
