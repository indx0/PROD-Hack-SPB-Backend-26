package ru.example.prodbackend.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.example.prodbackend.auth.dto.AuthenticationRequest;
import ru.example.prodbackend.auth.dto.AuthenticationResponse;
import ru.example.prodbackend.auth.dto.RegisterRequest;
import ru.example.prodbackend.auth.dto.refresh.RefreshTokenRequest;
import ru.example.prodbackend.auth.entity.RefreshTokenEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Общие запросы и проверки для тестов авторизации. */
public abstract class AuthIntegrationTestSupport extends BaseIntegrationTest {

    protected static final String REGISTER_URL = "/api/auth/register";
    protected static final String AUTHENTICATE_URL = "/api/auth/authenticate";
    protected static final String REFRESH_URL = "/api/auth/refresh";
    protected static final String LOGOUT_URL = "/api/auth/logout";
    protected static final String LOGOUT_ALL_URL = "/api/auth/logout-all";
    protected static final String EMAIL = "user@example.com";
    protected static final String PASSWORD = "P@ssw0rd!";

    @Value("${SECRET_KEY}")
    private String testSecretKey;

    protected AuthenticationResponse registerUser() throws Exception {
        return registerUser(EMAIL);
    }

    protected AuthenticationResponse registerUser(String email) throws Exception {
        return readTokenPair(postJson(REGISTER_URL, RegisterRequest.builder()
                        .firstName("Alice")
                        .lastName("Smith")
                .email(email)
                .password(PASSWORD)
                .build()));
    }

    protected AuthenticationResponse authenticateUser(String email) throws Exception {
        return readTokenPair(postJson(AUTHENTICATE_URL, AuthenticationRequest.builder()
                .email(email)
                .password(PASSWORD)
                .build()));
    }

    protected ResultActions postJson(String endpoint, Object request) throws Exception {
        return mockMvc.perform(post(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }

    protected ResultActions postRefreshToken(String endpoint, String rawToken) throws Exception {
        return postJson(endpoint, RefreshTokenRequest.builder()
                .refreshToken(rawToken)
                .build());
    }

    protected AuthenticationResponse readTokenPair(ResultActions result) throws Exception {
        String body = result
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refresh_token", not(emptyOrNullString())))
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readValue(body, AuthenticationResponse.class);
    }

    protected ResultActions assertErrorResponse(
            ResultActions result,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        result
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())))
                .andExpect(jsonPath("$.traceId", not(emptyOrNullString())))
                .andExpect(jsonPath("$.time", not(emptyOrNullString())));

        Map<?, ?> error = jsonMapper.readValue(
                result.andReturn().getResponse().getContentAsString(), Map.class);
        assertDoesNotThrow(() -> UUID.fromString((String) error.get("traceId")));
        assertDoesNotThrow(() -> Instant.parse((String) error.get("time")));

        return result;
    }

    protected void assertRefreshTokenRejected(String rawToken) throws Exception {
        assertErrorResponse(postRefreshToken(REFRESH_URL, rawToken), 401, "INVALID_REFRESH_TOKEN");
    }

    protected void assertAccessTokenWorks(String accessToken) throws Exception {
        mockMvc.perform(get("/api/demo/authping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string("auth pong"));
    }

    protected RefreshTokenEntity findSession(String rawToken) throws Exception {
        String tokenHash = hashToken(rawToken);
        return refreshTokenRepository.findAll().stream()
                .filter(session -> session.getTokenHash().equals(tokenHash))
                .findFirst().orElseThrow();
    }

    protected void expireRefreshToken(String rawToken) throws Exception {
        RefreshTokenEntity session = findSession(rawToken);
        session.setExpiryAt(Instant.EPOCH);
        refreshTokenRepository.saveAndFlush(session);
    }

    protected String signedAccessToken(String email, Instant expiration) {
        return Jwts.builder()
                .subject(email)
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(testSecretKey)), Jwts.SIG.HS256)
                .compact();
    }

    protected void assertDatabaseIsEmpty() {
        assertEquals(0L, userRepository.count());
        assertEquals(0L, refreshTokenRepository.count());
    }

    protected String hashToken(String rawToken) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }
}
