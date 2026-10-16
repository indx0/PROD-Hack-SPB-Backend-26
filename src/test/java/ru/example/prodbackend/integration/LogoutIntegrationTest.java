package ru.example.prodbackend.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.example.prodbackend.auth.dto.AuthenticationResponse;
import ru.example.prodbackend.auth.dto.refresh.RefreshTokenRequest;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogoutIntegrationTest extends AuthIntegrationTestSupport {

    @Test
    void logoutRevokesOnlyCurrentSessionAndCanBeRepeated() throws Exception {
        AuthenticationResponse current = registerUser();
        AuthenticationResponse otherDevice = authenticateUser(EMAIL);

        postRefreshToken(LOGOUT_URL, current.getRefreshToken())
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertEquals(1L, userRepository.count());
        assertEquals(1L, refreshTokenRepository.count());
        assertRefreshTokenRejected(current.getRefreshToken());
        readTokenPair(postRefreshToken(REFRESH_URL, otherDevice.getRefreshToken()));
        // В выбранной схеме logout не отзывает уже выданный access-токен.
        assertAccessTokenWorks(current.getAccessToken());

        postRefreshToken(LOGOUT_URL, current.getRefreshToken())
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertEquals(1L, refreshTokenRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "expired"})
    void logoutAcceptsUnknownOrExpiredTokenWithoutAffectingOtherSession(String scenario) throws Exception {
        AuthenticationResponse active = registerUser();
        String rawToken = "00000000-0000-4000-8000-000000000000";
        if (scenario.equals("expired")) {
            rawToken = authenticateUser(EMAIL).getRefreshToken();
            expireRefreshToken(rawToken);
        }

        postRefreshToken(LOGOUT_URL, rawToken)
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertEquals(1L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, active.getRefreshToken()));
    }

    @Test
    void logoutAllRevokesAllUserSessionsAndPreservesAnotherUser() throws Exception {
        AuthenticationResponse firstDevice = registerUser();
        AuthenticationResponse secondDevice = authenticateUser(EMAIL);
        AuthenticationResponse otherUser = registerUser("other@example.com");

        postRefreshToken(LOGOUT_ALL_URL, firstDevice.getRefreshToken())
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertEquals(2L, userRepository.count());
        assertEquals(1L, refreshTokenRepository.count());
        assertRefreshTokenRejected(firstDevice.getRefreshToken());
        assertRefreshTokenRejected(secondDevice.getRefreshToken());
        readTokenPair(postRefreshToken(REFRESH_URL, otherUser.getRefreshToken()));
        assertAccessTokenWorks(firstDevice.getAccessToken());
        assertAccessTokenWorks(secondDevice.getAccessToken());

        assertErrorResponse(postRefreshToken(LOGOUT_ALL_URL, firstDevice.getRefreshToken()),
                401, "INVALID_REFRESH_TOKEN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "expired", "replaced"})
    void logoutAllRejectsInvalidTokenWithoutRevokingOtherSessions(String scenario) throws Exception {
        AuthenticationResponse active = registerUser();
        String rawToken = "00000000-0000-4000-8000-000000000000";
        long expectedSessions = 1L;
        if (!scenario.equals("unknown")) {
            rawToken = authenticateUser(EMAIL).getRefreshToken();
            expectedSessions = 2L;
            if (scenario.equals("expired")) {
                expireRefreshToken(rawToken);
            } else {
                readTokenPair(postRefreshToken(REFRESH_URL, rawToken));
            }
        }

        assertErrorResponse(postRefreshToken(LOGOUT_ALL_URL, rawToken), 401, "INVALID_REFRESH_TOKEN");

        assertEquals(expectedSessions, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, active.getRefreshToken()));
    }

    @ParameterizedTest
    @ValueSource(strings = {LOGOUT_URL, LOGOUT_ALL_URL})
    void logoutWorksWithExpiredAccessTokenInHeader(String endpoint) throws Exception {
        AuthenticationResponse tokens = registerUser();

        mockMvc.perform(post(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedAccessToken(EMAIL, Instant.EPOCH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(RefreshTokenRequest.builder()
                                .refreshToken(tokens.getRefreshToken()).build())))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertEquals(0L, refreshTokenRepository.count());
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("blankLogoutTokens")
    void logoutRejectsBlankToken(String endpoint, String rawToken) throws Exception {
        assertErrorResponse(postRefreshToken(endpoint, rawToken), 422, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("refreshToken")));
        assertDatabaseIsEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {LOGOUT_URL, LOGOUT_ALL_URL})
    void logoutRejectsMissingTokenField(String endpoint) throws Exception {
        assertErrorResponse(postJson(endpoint, Map.of()), 422, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("refreshToken")));
        assertDatabaseIsEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {LOGOUT_URL, LOGOUT_ALL_URL})
    void logoutRejectsMalformedJson(String endpoint) throws Exception {
        assertErrorResponse(mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"broken\"")),
                400, "INVALID_REQUEST_BODY");
        assertDatabaseIsEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {LOGOUT_URL, LOGOUT_ALL_URL})
    void logoutRejectsMissingRequestBody(String endpoint) throws Exception {
        assertErrorResponse(mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)),
                400, "INVALID_REQUEST_BODY");
        assertDatabaseIsEmpty();
    }

    private static Stream<Arguments> blankLogoutTokens() {
        return Stream.of(LOGOUT_URL, LOGOUT_ALL_URL).flatMap(endpoint -> Stream.of(
                Arguments.of(endpoint, null),
                Arguments.of(endpoint, ""),
                Arguments.of(endpoint, "   ")
        ));
    }
}
