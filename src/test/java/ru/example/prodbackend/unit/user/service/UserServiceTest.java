package ru.example.prodbackend.unit.user.service;

import ru.example.prodbackend.user.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.auth.service.RefreshTokenService;
import ru.example.prodbackend.exception.UserAlreadyExistsException;
import ru.example.prodbackend.exception.UserNotFoundException;
import ru.example.prodbackend.user.dto.ChangePasswordRequest;
import ru.example.prodbackend.user.dto.UpdateProfileRequest;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import ru.example.prodbackend.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String EMAIL = "user@example.com";
    private static final String NEW_EMAIL = "new@example.com";
    private static final String CURRENT_PASSWORD = "P@ssw0rd!";
    private static final String NEW_PASSWORD = "NewPassword123";
    private static final String ENCODED_PASSWORD = "encoded-current-password";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserEntity user;
    private UserEntity principal;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .id(USER_ID)
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .firstName("Alice")
                .lastName("Smith")
                .role(UserRole.USER)
                .build();
        principal = UserEntity.builder()
                .id(USER_ID)
                .email("outdated@example.com")
                .password("outdated-password")
                .build();
    }

    @Test
    void getProfileReturnsCurrentUserFields() {
        var response = userService.getProfile(user);

        assertAll(
                () -> assertEquals(USER_ID, response.getId()),
                () -> assertEquals(EMAIL, response.getEmail()),
                () -> assertEquals("Alice", response.getFirstName()),
                () -> assertEquals("Smith", response.getLastName()),
                () -> assertEquals(UserRole.USER, response.getRole())
        );
        verifyNoInteractions(userRepository, refreshTokenService, jwtService, passwordEncoder);
    }

    @Test
    void partialUpdateUsesDatabaseUserAndPreservesUnspecifiedFieldsAndSessions() {
        mockCurrentUser();

        var response = userService.updateProfile(principal, UpdateProfileRequest.builder()
                .firstName("Bob")
                .build());

        assertEquals("Bob", user.getFirstName());
        assertEquals("Bob", response.getProfile().getFirstName());
        assertEquals(EMAIL, response.getProfile().getEmail());
        assertEquals("Smith", response.getProfile().getLastName());
        assertEquals(USER_ID, response.getProfile().getId());
        assertEquals(UserRole.USER, response.getProfile().getRole());
        assertEquals(ENCODED_PASSWORD, user.getPassword());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        verify(userRepository).findById(USER_ID);
        verify(userRepository).saveAndFlush(user);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(refreshTokenService, jwtService, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void blankNamesAreClearedWithoutRevokingSessions(String blank) {
        mockCurrentUser();

        var response = userService.updateProfile(principal, UpdateProfileRequest.builder()
                .firstName(blank)
                .lastName(blank)
                .build());

        assertNull(user.getFirstName());
        assertNull(user.getLastName());
        assertNull(response.getProfile().getFirstName());
        assertNull(response.getProfile().getLastName());
        assertEquals(EMAIL, user.getEmail());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        verifyNoInteractions(refreshTokenService, jwtService, passwordEncoder);
    }

    @Test
    void emptyUpdatePreservesProfileAndSessions() {
        mockCurrentUser();

        var response = userService.updateProfile(principal, UpdateProfileRequest.builder().build());

        assertEquals(EMAIL, user.getEmail());
        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals(ENCODED_PASSWORD, user.getPassword());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        verifyNoInteractions(refreshTokenService, jwtService, passwordEncoder);
    }

    @Test
    void unchangedEmailDoesNotCheckDuplicatesOrReplaceTokens() {
        mockCurrentUser();

        var response = userService.updateProfile(principal, UpdateProfileRequest.builder()
                .email(EMAIL)
                .lastName("Brown")
                .build());

        assertEquals(EMAIL, response.getProfile().getEmail());
        assertEquals("Brown", response.getProfile().getLastName());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        verify(userRepository).findById(USER_ID);
        verify(userRepository).saveAndFlush(user);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(refreshTokenService, jwtService, passwordEncoder);
    }

    @Test
    void emailChangeSavesProfileAndReplacesOwnSessionsWithNewTokens() {
        mockCurrentUser();
        when(userRepository.existsByEmail(NEW_EMAIL)).thenReturn(false);
        when(jwtService.generateToken(user)).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("new-refresh-token");

        var response = userService.updateProfile(principal, UpdateProfileRequest.builder()
                .email(NEW_EMAIL)
                .firstName("Bob")
                .build());

        assertEquals(NEW_EMAIL, user.getEmail());
        assertEquals(NEW_EMAIL, response.getProfile().getEmail());
        assertEquals("Bob", response.getProfile().getFirstName());
        assertEquals(USER_ID, response.getProfile().getId());
        assertEquals(ENCODED_PASSWORD, user.getPassword());
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());

        var order = inOrder(userRepository, refreshTokenService, jwtService);
        order.verify(userRepository).findById(USER_ID);
        order.verify(userRepository).existsByEmail(NEW_EMAIL);
        order.verify(userRepository).saveAndFlush(user);
        order.verify(refreshTokenService).deleteAllByUser(user);
        order.verify(jwtService).generateToken(user);
        order.verify(refreshTokenService).createRefreshToken(user);
        verifyNoMoreInteractions(userRepository, refreshTokenService, jwtService);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void duplicateEmailDoesNotChangeProfileOrSessions() {
        mockCurrentUser();
        when(userRepository.existsByEmail(NEW_EMAIL)).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> userService.updateProfile(principal, UpdateProfileRequest.builder()
                        .email(NEW_EMAIL)
                        .firstName("Bob")
                        .build()));

        assertEquals(EMAIL, user.getEmail());
        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals(ENCODED_PASSWORD, user.getPassword());
        verify(userRepository).findById(USER_ID);
        verify(userRepository).existsByEmail(NEW_EMAIL);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(refreshTokenService, jwtService, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile", "password"})
    void missingCurrentUserRejectsChangesWithoutIssuingOrRevokingTokens(String operation) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> {
            if (operation.equals("profile")) {
                userService.updateProfile(principal, UpdateProfileRequest.builder().firstName("Bob").build());
            } else {
                userService.changePassword(principal, new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD));
            }
        });

        verify(userRepository).findById(USER_ID);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(refreshTokenService, jwtService, passwordEncoder);
    }

    @Test
    void passwordChangeChecksStoredPasswordSavesHashAndRevokesOwnSessions() {
        mockCurrentUser();
        when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("encoded-new-password");

        userService.changePassword(principal, new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD));

        assertEquals("encoded-new-password", user.getPassword());
        assertNotEquals(NEW_PASSWORD, user.getPassword());
        assertEquals(EMAIL, user.getEmail());
        assertEquals("Alice", user.getFirstName());
        assertEquals(UserRole.USER, user.getRole());

        var order = inOrder(userRepository, passwordEncoder, refreshTokenService);
        order.verify(userRepository).findById(USER_ID);
        order.verify(passwordEncoder).matches(CURRENT_PASSWORD, ENCODED_PASSWORD);
        order.verify(passwordEncoder).encode(NEW_PASSWORD);
        order.verify(userRepository).saveAndFlush(user);
        order.verify(refreshTokenService).deleteAllByUser(user);
        verifyNoMoreInteractions(userRepository, passwordEncoder, refreshTokenService);
        verifyNoInteractions(jwtService);
    }

    @Test
    void wrongCurrentPasswordDoesNotChangeHashOrRevokeSessions() {
        mockCurrentUser();
        when(passwordEncoder.matches("wrong-password", ENCODED_PASSWORD)).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> userService.changePassword(principal,
                        new ChangePasswordRequest("wrong-password", NEW_PASSWORD)));

        assertEquals(ENCODED_PASSWORD, user.getPassword());
        verify(userRepository).findById(USER_ID);
        verify(passwordEncoder).matches("wrong-password", ENCODED_PASSWORD);
        verifyNoMoreInteractions(userRepository, passwordEncoder);
        verifyNoInteractions(refreshTokenService, jwtService);
    }

    private void mockCurrentUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }
}
