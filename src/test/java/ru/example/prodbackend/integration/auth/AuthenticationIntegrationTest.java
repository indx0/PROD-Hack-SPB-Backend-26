package ru.example.prodbackend.integration.auth;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.example.prodbackend.auth.dto.AuthenticationRequest;
import ru.example.prodbackend.auth.dto.AuthenticationResponse;
import ru.example.prodbackend.auth.dto.RegisterRequest;
import ru.example.prodbackend.auth.entity.RefreshTokenEntity;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AuthenticationIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"a", "short", PASSWORD})
    void registrationReturnsTokensAndStoresHashedSecrets(String password) throws Exception {
        AuthenticationResponse tokens = readTokenPair(postJson(REGISTER_URL, RegisterRequest.builder()
                .email(EMAIL).firstName("Alice").lastName("Smith").password(password).build()));
        UserEntity user = userRepository.findByEmail(EMAIL).orElseThrow();

        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals(1L, userRepository.count());
        assertNotEquals(password, user.getPassword());
        assertTrue(passwordEncoder.matches(password, user.getPassword()));
        assertAccessTokenBelongsToUser(tokens.getAccessToken());

        var sessions = refreshTokenRepository.findAllByUser(user);
        assertEquals(1L, refreshTokenRepository.count());
        assertEquals(1, sessions.size());

        RefreshTokenEntity session = sessions.get(0);
        assertEquals(hashToken(tokens.getRefreshToken()), session.getTokenHash());
        assertNotEquals(tokens.getRefreshToken(), session.getTokenHash());
        assertNotNull(session.getCreatedAt());
        assertTrue(session.getExpiryAt().isAfter(Instant.now()));
        assertTrue(session.getExpiryAt().isAfter(session.getCreatedAt()));
        AuthenticationResponse loginTokens = readTokenPair(
                postJson(AUTHENTICATE_URL, new AuthenticationRequest(EMAIL, password)));
        assertAccessTokenBelongsToUser(loginTokens.getAccessToken());
    }

    @Test
    void duplicateRegistrationDoesNotChangeUserOrCreateAnotherSession() throws Exception {
        AuthenticationResponse tokens = registerUser();

        assertErrorResponse(
                postJson(REGISTER_URL, RegisterRequest.builder()
                        .firstName("Alice")
                        .lastName("Smith")
                        .email(EMAIL)
                        .password("AnotherPassword!")
                        .build()),
                409,
                "USER_ALREADY_EXISTS"
        );

        UserEntity user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(1L, userRepository.count());
        assertEquals(1L, refreshTokenRepository.count());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPassword()));
        assertEquals(
                hashToken(tokens.getRefreshToken()),
                refreshTokenRepository.findAllByUser(user).get(0).getTokenHash()
        );
    }

    @Test
    void authenticationReturnsNewTokensAndPreservesPreviousSession() throws Exception {
        AuthenticationResponse originalTokens = registerUser();

        AuthenticationResponse newTokens = readTokenPair(
                postJson(AUTHENTICATE_URL, AuthenticationRequest.builder()
                        .email(EMAIL)
                        .password(PASSWORD)
                        .build())
        );

        assertAccessTokenBelongsToUser(newTokens.getAccessToken());
        assertNotEquals(originalTokens.getAccessToken(), newTokens.getAccessToken());
        assertNotEquals(originalTokens.getRefreshToken(), newTokens.getRefreshToken());
        assertEquals(1L, userRepository.count());
        assertEquals(2L, refreshTokenRepository.count());

        UserEntity user = userRepository.findByEmail(EMAIL).orElseThrow();
        Set<String> storedHashes = refreshTokenRepository.findAllByUser(user)
                .stream()
                .map(RefreshTokenEntity::getTokenHash)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        hashToken(originalTokens.getRefreshToken()),
                        hashToken(newTokens.getRefreshToken())
                ),
                storedHashes
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCredentials")
    void authenticationRejectsInvalidCredentialsWithoutCreatingSession(
            String scenario,
            String email,
            String password
    ) throws Exception {
        AuthenticationResponse tokens = registerUser();

        assertErrorResponse(
                postJson(AUTHENTICATE_URL, AuthenticationRequest.builder()
                        .email(email)
                        .password(password)
                        .build()),
                401,
                "UNAUTHORIZED"
        ).andExpect(jsonPath("$.message").value("Invalid email or password"));

        assertEquals(1L, userRepository.count());
        assertEquals(1L, refreshTokenRepository.count());
        UserEntity user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals(
                hashToken(tokens.getRefreshToken()),
                refreshTokenRepository.findAllByUser(user).get(0).getTokenHash()
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrationRequests")
    void registrationRejectsInvalidFields(
            String scenario,
            String email,
            String password,
            String invalidField
    ) throws Exception {
        assertErrorResponse(
                postJson(REGISTER_URL, RegisterRequest.builder()
                        .firstName("Alice")
                        .lastName("Smith")
                        .email(email)
                        .password(password)
                        .build()),
                422,
                "VALIDATION_ERROR"
        )
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(invalidField)))
                .andExpect(jsonPath("$.fieldErrors[0].issue", not(emptyOrNullString())));

        assertDatabaseIsEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAuthenticationRequests")
    void authenticationRejectsInvalidFields(
            String scenario,
            String email,
            String password,
            String invalidField
    ) throws Exception {
        assertErrorResponse(
                postJson(AUTHENTICATE_URL, AuthenticationRequest.builder()
                        .email(email)
                        .password(password)
                        .build()),
                422,
                "VALIDATION_ERROR"
        )
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(invalidField)))
                .andExpect(jsonPath("$.fieldErrors[0].issue", not(emptyOrNullString())));

        assertDatabaseIsEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {REGISTER_URL, AUTHENTICATE_URL})
    void rejectsMalformedJson(String endpoint) throws Exception {
        assertErrorResponse(
                mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com" "password":"P@ssw0rd!"}
                                """)),
                400,
                "INVALID_REQUEST_BODY"
        );

        assertDatabaseIsEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {REGISTER_URL, AUTHENTICATE_URL})
    void rejectsMissingRequestBody(String endpoint) throws Exception {
        assertErrorResponse(
                mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)),
                400,
                "INVALID_REQUEST_BODY"
        );

        assertDatabaseIsEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void registrationRejectsInvalidNames(String field, String value) throws Exception {
        RegisterRequest request = RegisterRequest.builder().email(EMAIL).password(PASSWORD)
                .firstName("Alice").lastName("Smith").build();
        if (field.equals("firstName")) request.setFirstName(value);
        else request.setLastName(value);
        assertErrorResponse(postJson(REGISTER_URL, request), 422, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(field)));
        assertDatabaseIsEmpty();
    }

    private static Stream<Arguments> invalidNames() {
        return Stream.of("firstName", "lastName").flatMap(field ->
                Stream.of(null, "", "   ", "a".repeat(256)).map(value -> Arguments.of(field, value)));
    }

    private static Stream<Arguments> invalidCredentials() {
        return Stream.of(
                Arguments.of("wrong password", EMAIL, "WrongPassword!"),
                Arguments.of("unknown user", "unknown@example.com", PASSWORD)
        );
    }

    private static Stream<Arguments> invalidRegistrationRequests() {
        return Stream.of(
                Arguments.of("null email", null, PASSWORD, "email"),
                Arguments.of("empty email", "", PASSWORD, "email"),
                Arguments.of("blank email", "   ", PASSWORD, "email"),
                Arguments.of("email exceeds 255 characters", "a".repeat(256), PASSWORD, "email"),
                Arguments.of("null password", EMAIL, null, "password"),
                Arguments.of("blank password", EMAIL, "   ", "password"),
                Arguments.of("empty password", EMAIL, "", "password")
        );
    }

    private static Stream<Arguments> invalidAuthenticationRequests() {
        return Stream.of(
                Arguments.of("null email", null, PASSWORD, "email"),
                Arguments.of("empty email", "", PASSWORD, "email"),
                Arguments.of("blank email", "   ", PASSWORD, "email"),
                Arguments.of("null password", EMAIL, null, "password"),
                Arguments.of("empty password", EMAIL, "", "password"),
                Arguments.of("blank password", EMAIL, "   ", "password")
        );
    }

    private void assertAccessTokenBelongsToUser(String accessToken) {
        var claims = jwtService.extractAllClaims(accessToken);
        assertEquals(EMAIL, claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
    }

}
