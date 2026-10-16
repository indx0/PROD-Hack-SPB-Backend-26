package ru.example.prodbackend.integration;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.example.prodbackend.auth.dto.AuthenticationResponse;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileIntegrationTest extends AuthIntegrationTestSupport {

    private static final String PROFILE_URL = "/api/user/me";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void returnsCurrentUserProfileWithoutExposingOtherFields() throws Exception {
        AuthenticationResponse tokens = registerUser();
        UserEntity user = userRepository.findByEmail(EMAIL).orElseThrow();

        assertProfile(tokens.getAccessToken(), user);
    }

    @Test
    void eachUserReceivesTheirOwnProfile() throws Exception {
        AuthenticationResponse firstTokens = registerUser();
        AuthenticationResponse secondTokens = registerUser("second@example.com");

        assertProfile(firstTokens.getAccessToken(), userRepository.findByEmail(EMAIL).orElseThrow());
        assertProfile(secondTokens.getAccessToken(), userRepository.findByEmail("second@example.com").orElseThrow());
    }

    @Test
    void adminCanRetrieveTheirOwnProfile() throws Exception {
        UserEntity admin = userRepository.saveAndFlush(UserEntity.builder()
                .email("admin@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .role(UserRole.ADMIN)
                .build());
        AuthenticationResponse tokens = authenticateUser(admin.getEmail());

        assertProfile(tokens.getAccessToken(), admin);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"missing", "empty", "malformed", "expired", "wrong-signature", "refresh", "unknown-user"})
    void rejectsInvalidAccessToken(String scenario) throws Exception {
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

        MockHttpServletRequestBuilder request = get(PROFILE_URL);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        assertErrorResponse(mockMvc.perform(request), 401, "UNAUTHORIZED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void rejectsAccessTokenAfterUserIsDeleted() throws Exception {
        AuthenticationResponse tokens = registerUser();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        assertErrorResponse(mockMvc.perform(get(PROFILE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.getAccessToken())),
                401, "UNAUTHORIZED")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    private void assertProfile(String accessToken, UserEntity user) throws Exception {
        mockMvc.perform(get(PROFILE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value(user.getRole().name()))
                .andExpect(jsonPath("$.firstName").value(user.getFirstName()))
                .andExpect(jsonPath("$.lastName").value(user.getLastName()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
