package ru.example.prodbackend.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.example.prodbackend.auth.dto.AuthenticationResponse;
import ru.example.prodbackend.auth.dto.refresh.RefreshTokenRequest;
import ru.example.prodbackend.auth.entity.RefreshTokenEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class RefreshTokenIntegrationTest extends AuthIntegrationTestSupport {

    @Test
    void refreshRotatesExistingSessionAndReturnsUsableTokens() throws Exception {
        AuthenticationResponse original = registerUser();
        RefreshTokenEntity originalSession = findSession(original.getRefreshToken());
        originalSession.setExpiryAt(Instant.now().plusSeconds(60));
        refreshTokenRepository.saveAndFlush(originalSession);

        AuthenticationResponse updated = readTokenPair(
                postRefreshToken(REFRESH_URL, original.getRefreshToken()));

        assertNotEquals(original.getAccessToken(), updated.getAccessToken());
        assertNotEquals(original.getRefreshToken(), updated.getRefreshToken());
        assertAccessTokenWorks(updated.getAccessToken());
        assertEquals(1L, userRepository.count());
        assertEquals(1L, refreshTokenRepository.count());

        RefreshTokenEntity updatedSession = findSession(updated.getRefreshToken());
        assertEquals(originalSession.getId(), updatedSession.getId());
        assertEquals(originalSession.getCreatedAt(), updatedSession.getCreatedAt());
        assertEquals(hashToken(updated.getRefreshToken()), updatedSession.getTokenHash());
        assertTrue(updatedSession.getExpiryAt().isAfter(originalSession.getExpiryAt()));

        assertRefreshTokenRejected(original.getRefreshToken());
        AuthenticationResponse next = readTokenPair(
                postRefreshToken(REFRESH_URL, updated.getRefreshToken()));
        assertAccessTokenWorks(next.getAccessToken());
        assertRefreshTokenRejected(updated.getRefreshToken());
        assertEquals(1L, refreshTokenRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000-0000-4000-8000-000000000000", "not-a-refresh-token"})
    void invalidRefreshDoesNotChangeExistingSession(String invalidToken) throws Exception {
        AuthenticationResponse original = registerUser();
        RefreshTokenEntity originalSession = findSession(original.getRefreshToken());

        assertRefreshTokenRejected(invalidToken);

        assertEquals(1L, refreshTokenRepository.count());
        assertEquals(originalSession.getId(), findSession(original.getRefreshToken()).getId());
        readTokenPair(postRefreshToken(REFRESH_URL, original.getRefreshToken()));
    }

    @Test
    void expiredRefreshIsRejectedWithoutRotation() throws Exception {
        AuthenticationResponse original = registerUser();
        expireRefreshToken(original.getRefreshToken());

        assertRefreshTokenRejected(original.getRefreshToken());

        assertEquals(1L, refreshTokenRepository.count());
        assertEquals(Instant.EPOCH, findSession(original.getRefreshToken()).getExpiryAt());
    }

    @Test
    void refreshWorksWithExpiredAccessTokenInHeader() throws Exception {
        AuthenticationResponse original = registerUser();

        AuthenticationResponse updated = readTokenPair(mockMvc.perform(post(REFRESH_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedAccessToken(EMAIL, Instant.EPOCH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(RefreshTokenRequest.builder()
                        .refreshToken(original.getRefreshToken()).build()))));

        assertAccessTokenWorks(updated.getAccessToken());
        assertRefreshTokenRejected(original.getRefreshToken());
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() throws Exception {
        AuthenticationResponse original = registerUser();

        assertRefreshTokenRejected(original.getAccessToken());

        assertEquals(1L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, original.getRefreshToken()));
    }

    @Test
    void storedHashCannotBeUsedAsRefreshToken() throws Exception {
        AuthenticationResponse original = registerUser();

        assertRefreshTokenRejected(findSession(original.getRefreshToken()).getTokenHash());

        assertEquals(1L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, original.getRefreshToken()));
    }

    @Test
    void rotationOnOneDevicePreservesOtherDeviceSession() throws Exception {
        AuthenticationResponse firstDevice = registerUser();
        AuthenticationResponse secondDevice = authenticateUser(EMAIL);

        AuthenticationResponse firstUpdated = readTokenPair(
                postRefreshToken(REFRESH_URL, firstDevice.getRefreshToken()));

        findSession(firstUpdated.getRefreshToken());
        findSession(secondDevice.getRefreshToken());
        assertEquals(2L, refreshTokenRepository.count());
        assertRefreshTokenRejected(firstDevice.getRefreshToken());

        AuthenticationResponse secondUpdated = readTokenPair(
                postRefreshToken(REFRESH_URL, secondDevice.getRefreshToken()));

        assertAccessTokenWorks(firstUpdated.getAccessToken());
        assertAccessTokenWorks(secondUpdated.getAccessToken());
        assertEquals(2L, refreshTokenRepository.count());
    }

    @Test
    @Timeout(30)
    void concurrentRefreshRequestsCannotBothRotateSameToken() throws Exception {
        AuthenticationResponse original = registerUser();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<MvcResult> request = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent requests did not start in time");
            }
            return postRefreshToken(REFRESH_URL, original.getRefreshToken()).andReturn();
        };

        try {
            Future<MvcResult> first = executor.submit(request);
            Future<MvcResult> second = executor.submit(request);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            MvcResult firstResult = first.get(10, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertEquals(List.of(200, 401), Stream.of(firstResult, secondResult)
                    .map(result -> result.getResponse().getStatus()).sorted().toList());

            MvcResult successful = firstResult.getResponse().getStatus() == 200
                    ? firstResult : secondResult;
            MvcResult rejected = successful == firstResult ? secondResult : firstResult;
            AuthenticationResponse updated = jsonMapper.readValue(
                    successful.getResponse().getContentAsString(), AuthenticationResponse.class);
            Map<?, ?> error = jsonMapper.readValue(
                    rejected.getResponse().getContentAsString(), Map.class);

            assertEquals("INVALID_REFRESH_TOKEN", error.get("code"));
            assertFalse(updated.getRefreshToken().isBlank());
            assertAccessTokenWorks(updated.getAccessToken());
            assertEquals(1L, refreshTokenRepository.count());
            assertRefreshTokenRejected(original.getRefreshToken());
            readTokenPair(postRefreshToken(REFRESH_URL, updated.getRefreshToken()));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsBlankRefreshToken(String rawToken) throws Exception {
        assertErrorResponse(postRefreshToken(REFRESH_URL, rawToken), 422, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("refreshToken")));
        assertDatabaseIsEmpty();
    }

    @Test
    void rejectsMissingRefreshTokenField() throws Exception {
        assertErrorResponse(postJson(REFRESH_URL, Map.of()), 422, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("refreshToken")));
        assertDatabaseIsEmpty();
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        assertErrorResponse(mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"broken\"")),
                400, "INVALID_REQUEST_BODY");
        assertDatabaseIsEmpty();
    }

    @Test
    void rejectsMissingRequestBody() throws Exception {
        assertErrorResponse(mockMvc.perform(post(REFRESH_URL).contentType(MediaType.APPLICATION_JSON)),
                400, "INVALID_REQUEST_BODY");
        assertDatabaseIsEmpty();
    }
}
