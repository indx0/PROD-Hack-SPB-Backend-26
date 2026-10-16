package ru.example.prodbackend.integration;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.example.prodbackend.auth.dto.AuthenticationRequest;
import ru.example.prodbackend.user.dto.ChangePasswordRequest;
import ru.example.prodbackend.user.dto.UpdateProfileResponse;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserEditingIntegrationTest extends AuthIntegrationTestSupport {

    private static final String PROFILE_URL = "/api/user/me";
    private static final String CHANGE_PASSWORD_URL = "/api/user/change-password";
    private static final String NEW_PASSWORD = "NewPassword123";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void partialUpdateChangesOnlyProvidedFieldsAndPreservesAllSessions() throws Exception {
        var first = registerUser();
        var second = authenticateUser(EMAIL);
        var other = registerUser("other@example.com");
        var before = userRepository.findByEmail(EMAIL).orElseThrow();

        update(first.getAccessToken(), "{\"firstName\":\"Bob\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value(before.getId().toString()))
                .andExpect(jsonPath("$.profile.email").value(EMAIL))
                .andExpect(jsonPath("$.profile.firstName").value("Bob"))
                .andExpect(jsonPath("$.profile.lastName").value("Smith"))
                .andExpect(jsonPath("$.profile.role").value("USER"))
                .andExpect(jsonPath("$.profile.password").doesNotExist())
                .andExpect(jsonPath("$.access_token").isEmpty())
                .andExpect(jsonPath("$.refresh_token").isEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        var updated = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals("Bob", updated.getFirstName());
        assertEquals("Smith", updated.getLastName());
        assertEquals(before.getPassword(), updated.getPassword());
        assertEquals("Alice", userRepository.findByEmail("other@example.com").orElseThrow().getFirstName());
        assertEquals(3L, refreshTokenRepository.count());
        mockMvc.perform(get(PROFILE_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + second.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Bob"));
        readTokenPair(postRefreshToken(REFRESH_URL, first.getRefreshToken()));
        readTokenPair(postRefreshToken(REFRESH_URL, second.getRefreshToken()));
        readTokenPair(postRefreshToken(REFRESH_URL, other.getRefreshToken()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":null,\"firstName\":null,\"lastName\":null}"})
    void omittedAndNullFieldsPreserveProfileAndSession(String json) throws Exception {
        var tokens = registerUser();

        update(tokens.getAccessToken(), json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(EMAIL))
                .andExpect(jsonPath("$.profile.firstName").value("Alice"))
                .andExpect(jsonPath("$.profile.lastName").value("Smith"))
                .andExpect(jsonPath("$.access_token").isEmpty())
                .andExpect(jsonPath("$.refresh_token").isEmpty());

        var user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals(1L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, tokens.getRefreshToken()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankNamesAreClearedWithoutChangingEmailOrSessions(String blank) throws Exception {
        var tokens = registerUser();

        update(tokens.getAccessToken(), jsonMapper.writeValueAsString(Map.of(
                "firstName", blank,
                "lastName", blank
        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(EMAIL))
                .andExpect(jsonPath("$.profile.firstName").isEmpty())
                .andExpect(jsonPath("$.profile.lastName").isEmpty())
                .andExpect(jsonPath("$.access_token").isEmpty())
                .andExpect(jsonPath("$.refresh_token").isEmpty());

        var user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertNull(user.getFirstName());
        assertNull(user.getLastName());
        assertEquals(1L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, tokens.getRefreshToken()));
    }

    @Test
    void unchangedEmailDoesNotReplaceSessionsOrReturnTokens() throws Exception {
        var first = registerUser();
        var second = authenticateUser(EMAIL);

        update(first.getAccessToken(), "{\"email\":\"user@example.com\",\"lastName\":\"Brown\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.lastName").value("Brown"))
                .andExpect(jsonPath("$.access_token").isEmpty())
                .andExpect(jsonPath("$.refresh_token").isEmpty());

        assertEquals(2L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, first.getRefreshToken()));
        readTokenPair(postRefreshToken(REFRESH_URL, second.getRefreshToken()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"new@example.com", "new-user"})
    void changingEmailReplacesOwnSessionsAndReturnsUsableTokens(String newEmail) throws Exception {
        var first = registerUser();
        var second = authenticateUser(EMAIL);
        var other = registerUser("other@example.com");
        var before = userRepository.findByEmail(EMAIL).orElseThrow();

        String body = update(first.getAccessToken(), jsonMapper.writeValueAsString(Map.of(
                "email", newEmail,
                "firstName", "Bob"
        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(newEmail))
                .andExpect(jsonPath("$.access_token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refresh_token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        var response = jsonMapper.readValue(body, UpdateProfileResponse.class);
        var updated = userRepository.findByEmail(newEmail).orElseThrow();
        assertEquals(before.getId(), updated.getId());
        assertEquals(before.getPassword(), updated.getPassword());
        assertEquals("Bob", updated.getFirstName());
        assertEquals("Smith", updated.getLastName());
        assertEquals(before.getRole(), updated.getRole());
        assertTrue(userRepository.findByEmail(EMAIL).isEmpty());
        assertEquals(2L, refreshTokenRepository.count());

        mockMvc.perform(get(PROFILE_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + response.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
        assertAccessTokenWorks(response.getAccessToken());
        assertErrorResponse(mockMvc.perform(get(PROFILE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.getAccessToken())), 401, "UNAUTHORIZED");
        assertRefreshTokenRejected(first.getRefreshToken());
        assertRefreshTokenRejected(second.getRefreshToken());
        readTokenPair(postRefreshToken(REFRESH_URL, response.getRefreshToken()));
        readTokenPair(postRefreshToken(REFRESH_URL, other.getRefreshToken()));
        assertErrorResponse(postJson(AUTHENTICATE_URL, new AuthenticationRequest(EMAIL, PASSWORD)),
                401, "UNAUTHORIZED");
        authenticateUser(newEmail);
    }

    @Test
    void duplicateEmailLeavesProfileAndAllSessionsUnchanged() throws Exception {
        var first = registerUser();
        var second = authenticateUser(EMAIL);
        var other = registerUser("other@example.com");
        var before = userRepository.findByEmail(EMAIL).orElseThrow();

        assertErrorResponse(update(first.getAccessToken(),
                "{\"email\":\"other@example.com\",\"firstName\":\"Bob\",\"lastName\":\"Brown\"}"),
                409, "USER_ALREADY_EXISTS");

        var unchanged = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(before.getId(), unchanged.getId());
        assertEquals(before.getPassword(), unchanged.getPassword());
        assertEquals("Alice", unchanged.getFirstName());
        assertEquals("Smith", unchanged.getLastName());
        assertEquals(3L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, first.getRefreshToken()));
        readTokenPair(postRefreshToken(REFRESH_URL, second.getRefreshToken()));
        readTokenPair(postRefreshToken(REFRESH_URL, other.getRefreshToken()));
    }

    @ParameterizedTest(name = "invalid {0}: {1}")
    @MethodSource("invalidProfileFields")
    void invalidProfileFieldsLeaveProfileAndSessionUnchanged(String field, String value) throws Exception {
        var tokens = registerUser();
        var before = userRepository.findByEmail(EMAIL).orElseThrow();

        assertErrorResponse(update(tokens.getAccessToken(),
                jsonMapper.writeValueAsString(Map.of(field, value))), 422, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(field)));

        var unchanged = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(before.getPassword(), unchanged.getPassword());
        assertEquals("Alice", unchanged.getFirstName());
        assertEquals("Smith", unchanged.getLastName());
        assertEquals(1L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, tokens.getRefreshToken()));
    }

    @Test
    void passwordChangeStoresHashRevokesOwnSessionsAndPreservesExistingAccessTokens() throws Exception {
        var first = registerUser();
        var second = authenticateUser(EMAIL);
        var other = registerUser("other@example.com");

        changePassword(first.getAccessToken(), passwordJson(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        var user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertNotEquals(NEW_PASSWORD, user.getPassword());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertFalse(passwordEncoder.matches(PASSWORD, user.getPassword()));
        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals(1L, refreshTokenRepository.count());
        assertRefreshTokenRejected(first.getRefreshToken());
        assertRefreshTokenRejected(second.getRefreshToken());
        assertAccessTokenWorks(first.getAccessToken());
        assertAccessTokenWorks(second.getAccessToken());
        readTokenPair(postRefreshToken(REFRESH_URL, other.getRefreshToken()));
        assertErrorResponse(postJson(AUTHENTICATE_URL, new AuthenticationRequest(EMAIL, PASSWORD)),
                401, "UNAUTHORIZED");
        readTokenPair(postJson(AUTHENTICATE_URL, new AuthenticationRequest(EMAIL, NEW_PASSWORD)));
    }

    @Test
    void passwordOfExactlyEightCharactersIsAccepted() throws Exception {
        var tokens = registerUser();
        String newPassword = "Pass123!";

        changePassword(tokens.getAccessToken(), passwordJson(PASSWORD, newPassword))
                .andExpect(status().isNoContent());

        assertTrue(passwordEncoder.matches(newPassword,
                userRepository.findByEmail(EMAIL).orElseThrow().getPassword()));
        readTokenPair(postJson(AUTHENTICATE_URL, new AuthenticationRequest(EMAIL, newPassword)));
    }

    @Test
    void wrongCurrentPasswordLeavesCredentialsAndSessionUnchanged() throws Exception {
        var tokens = registerUser();
        String before = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();

        assertErrorResponse(changePassword(tokens.getAccessToken(), passwordJson("wrong-password", NEW_PASSWORD)),
                401, "UNAUTHORIZED");

        assertEquals(before, userRepository.findByEmail(EMAIL).orElseThrow().getPassword());
        assertEquals(1L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, tokens.getRefreshToken()));
    }

    @ParameterizedTest(name = "invalid {2}: {0}, {1}")
    @MethodSource("invalidPasswords")
    void invalidPasswordFieldsLeaveCredentialsAndSessionUnchanged(
            String currentPassword, String newPassword, String field
    ) throws Exception {
        var tokens = registerUser();
        String before = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();

        assertErrorResponse(changePassword(tokens.getAccessToken(), passwordJson(currentPassword, newPassword)),
                422, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(field)));

        assertEquals(before, userRepository.findByEmail(EMAIL).orElseThrow().getPassword());
        assertEquals(1L, refreshTokenRepository.count());
        readTokenPair(postRefreshToken(REFRESH_URL, tokens.getRefreshToken()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null"})
    void bothEndpointsRejectMalformedOrMissingBodiesWithoutChangingData(String json) throws Exception {
        var tokens = registerUser();
        String before = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();

        assertErrorResponse(update(tokens.getAccessToken(), json), 400, "INVALID_REQUEST_BODY");
        assertErrorResponse(changePassword(tokens.getAccessToken(), json), 400, "INVALID_REQUEST_BODY");

        var user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(before, user.getPassword());
        assertEquals("Alice", user.getFirstName());
        assertEquals(1L, refreshTokenRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty", "malformed", "expired", "wrong-signature", "refresh", "unknown-user"})
    void bothEditingEndpointsRequireValidAccessToken(String scenario) throws Exception {
        var tokens = registerUser();
        String token = switch (scenario) {
            case "missing" -> null;
            case "empty" -> "";
            case "expired" -> signedAccessToken(EMAIL, Instant.EPOCH);
            case "wrong-signature" -> Jwts.builder()
                    .subject(EMAIL)
                    .expiration(Date.from(Instant.now().plusSeconds(60)))
                    .signWith(Jwts.SIG.HS256.key().build(), Jwts.SIG.HS256)
                    .compact();
            case "refresh" -> tokens.getRefreshToken();
            case "unknown-user" -> signedAccessToken("unknown@example.com", Instant.now().plusSeconds(60));
            default -> "bad-token";
        };

        assertErrorResponse(update(token, "{\"firstName\":\"Bob\"}"), 401, "UNAUTHORIZED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        assertErrorResponse(changePassword(token, passwordJson(PASSWORD, NEW_PASSWORD)), 401, "UNAUTHORIZED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        var user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals("Alice", user.getFirstName());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPassword()));
        assertEquals(1L, refreshTokenRepository.count());
    }

    private ResultActions update(String token, String json) throws Exception {
        return performAuthenticated(patch(PROFILE_URL), token, json);
    }

    private ResultActions changePassword(String token, String json) throws Exception {
        return performAuthenticated(post(CHANGE_PASSWORD_URL), token, json);
    }

    private ResultActions performAuthenticated(
            MockHttpServletRequestBuilder request, String token, String json
    ) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String passwordJson(String currentPassword, String newPassword) {
        return jsonMapper.writeValueAsString(new ChangePasswordRequest(currentPassword, newPassword));
    }

    private static Stream<Arguments> invalidProfileFields() {
        return Stream.of(
                Arguments.of("email", ""),
                Arguments.of("email", "   "),
                Arguments.of("email", "\t\n"),
                Arguments.of("email", "a".repeat(256)),
                Arguments.of("firstName", "a".repeat(256)),
                Arguments.of("lastName", "a".repeat(256))
        );
    }

    private static Stream<Arguments> invalidPasswords() {
        return Stream.of(
                Arguments.of(null, NEW_PASSWORD, "currentPassword"),
                Arguments.of("", NEW_PASSWORD, "currentPassword"),
                Arguments.of("   ", NEW_PASSWORD, "currentPassword"),
                Arguments.of(PASSWORD, null, "newPassword"),
                Arguments.of(PASSWORD, "", "newPassword"),
                Arguments.of(PASSWORD, "        ", "newPassword"),
                Arguments.of(PASSWORD, "short", "newPassword")
        );
    }
}
