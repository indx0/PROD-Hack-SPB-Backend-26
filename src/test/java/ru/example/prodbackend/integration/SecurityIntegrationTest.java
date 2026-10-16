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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends AuthIntegrationTestSupport {

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
}
