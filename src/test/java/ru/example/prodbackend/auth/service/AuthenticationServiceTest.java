package ru.example.prodbackend.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.example.prodbackend.auth.dto.AuthenticationRequest;
import ru.example.prodbackend.auth.dto.AuthenticationResponse;
import ru.example.prodbackend.auth.dto.RegisterRequest;
import ru.example.prodbackend.auth.dto.refresh.RefreshTokenRequest;
import ru.example.prodbackend.exception.RefreshTokenException;
import ru.example.prodbackend.exception.UserAlreadyExistsException;
import ru.example.prodbackend.exception.UserNotFoundException;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import ru.example.prodbackend.user.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "P@ssw0rd!";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void registerSavesUserWithEncodedPasswordAndReturnsTokens() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(jwtService.generateToken(any(UserEntity.class))).thenReturn(ACCESS_TOKEN);
        when(refreshTokenService.createRefreshToken(any(UserEntity.class))).thenReturn(REFRESH_TOKEN);

        AuthenticationResponse response = authenticationService.register(RegisterRequest.builder().email(EMAIL).firstName("Alice").lastName("Smith").password(PASSWORD).build());

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        var order = inOrder(userRepository, jwtService, refreshTokenService);
        order.verify(userRepository).existsByEmail(EMAIL);
        order.verify(userRepository).saveAndFlush(userCaptor.capture());
        UserEntity savedUser = userCaptor.getValue();
        order.verify(jwtService).generateToken(savedUser);
        order.verify(refreshTokenService).createRefreshToken(savedUser);

        assertEquals(EMAIL, savedUser.getUsername());
        assertEquals("Alice", savedUser.getFirstName());
        assertEquals("Smith", savedUser.getLastName());
        assertEquals(ENCODED_PASSWORD, savedUser.getPassword());
        assertEquals("USER", savedUser.getAuthorities().iterator().next().getAuthority());
        verify(passwordEncoder).encode(PASSWORD);
        assertTokenPair(response, ACCESS_TOKEN, REFRESH_TOKEN);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void registerRejectsDuplicateWithoutSavingUserOrIssuingTokens() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> authenticationService.register(RegisterRequest.builder().email(EMAIL).firstName("Alice").lastName("Smith").password(PASSWORD).build()));

        verify(userRepository).existsByEmail(EMAIL);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder, jwtService, refreshTokenService, authenticationManager);
    }

    @Test
    void authenticateChecksCredentialsBeforeIssuingTokensForExistingUser() {
        UserEntity user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn(ACCESS_TOKEN);
        when(refreshTokenService.createRefreshToken(user)).thenReturn(REFRESH_TOKEN);

        AuthenticationResponse response = authenticationService.authenticate(new AuthenticationRequest(EMAIL, PASSWORD));

        ArgumentCaptor<Authentication> credentials = ArgumentCaptor.forClass(Authentication.class);
        var order = inOrder(authenticationManager, userRepository, jwtService, refreshTokenService);
        order.verify(authenticationManager).authenticate(credentials.capture());
        order.verify(userRepository).findByEmail(EMAIL);
        order.verify(jwtService).generateToken(user);
        order.verify(refreshTokenService).createRefreshToken(user);
        assertEquals(EMAIL, credentials.getValue().getPrincipal());
        assertEquals(PASSWORD, credentials.getValue().getCredentials());
        assertFalse(credentials.getValue().isAuthenticated());
        assertTokenPair(response, ACCESS_TOKEN, REFRESH_TOKEN);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void authenticateRejectsBadCredentialsWithoutIssuingTokens() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThrows(BadCredentialsException.class,
                () -> authenticationService.authenticate(new AuthenticationRequest(EMAIL, PASSWORD)));

        verifyNoInteractions(userRepository, jwtService, refreshTokenService, passwordEncoder);
    }

    @Test
    void authenticateRejectsUserMissingAfterCredentialsCheck() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> authenticationService.authenticate(new AuthenticationRequest(EMAIL, PASSWORD)));

        verify(authenticationManager).authenticate(any(Authentication.class));
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void updateTokensUsesRefreshOwnerAndRotatesProvidedToken() {
        UserEntity user = user();
        when(refreshTokenService.getUserByRefreshToken(REFRESH_TOKEN)).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn(ACCESS_TOKEN);
        when(refreshTokenService.rotateRefreshToken(REFRESH_TOKEN)).thenReturn("rotated-refresh-token");

        AuthenticationResponse response = authenticationService.updateTokens(new RefreshTokenRequest(REFRESH_TOKEN));

        var order = inOrder(refreshTokenService, jwtService);
        order.verify(refreshTokenService).getUserByRefreshToken(REFRESH_TOKEN);
        order.verify(jwtService).generateToken(user);
        order.verify(refreshTokenService).rotateRefreshToken(REFRESH_TOKEN);
        verifyNoMoreInteractions(refreshTokenService);
        verifyNoInteractions(userRepository, authenticationManager, passwordEncoder);
        assertTokenPair(response, ACCESS_TOKEN, "rotated-refresh-token");
    }

    @Test
    void updateTokensRejectsInvalidRefreshWithoutIssuingOrRotatingTokens() {
        when(refreshTokenService.getUserByRefreshToken(REFRESH_TOKEN))
                .thenThrow(new RefreshTokenException("Invalid refresh token"));

        assertThrows(RefreshTokenException.class,
                () -> authenticationService.updateTokens(new RefreshTokenRequest(REFRESH_TOKEN)));

        verify(refreshTokenService).getUserByRefreshToken(REFRESH_TOKEN);
        verifyNoMoreInteractions(refreshTokenService);
        verifyNoInteractions(jwtService);
    }

    @Test
    void logoutRevokesOnlyProvidedRefreshToken() {
        authenticationService.logout(new RefreshTokenRequest(REFRESH_TOKEN));

        verify(refreshTokenService).revokeRefreshToken(REFRESH_TOKEN);
        verifyNoMoreInteractions(refreshTokenService);
        verifyNoInteractions(jwtService, userRepository, authenticationManager, passwordEncoder);
    }

    @Test
    void logoutAllDeletesSessionsOfRefreshTokenOwner() {
        UserEntity user = user();
        when(refreshTokenService.getUserByRefreshToken(REFRESH_TOKEN)).thenReturn(user);

        authenticationService.logoutAll(new RefreshTokenRequest(REFRESH_TOKEN));

        var order = inOrder(refreshTokenService);
        order.verify(refreshTokenService).getUserByRefreshToken(REFRESH_TOKEN);
        order.verify(refreshTokenService).deleteAllByUser(user);
        verifyNoMoreInteractions(refreshTokenService);
        verifyNoInteractions(jwtService, userRepository, authenticationManager, passwordEncoder);
    }

    @Test
    void logoutAllRejectsInvalidRefreshWithoutDeletingSessions() {
        when(refreshTokenService.getUserByRefreshToken(REFRESH_TOKEN))
                .thenThrow(new RefreshTokenException("Invalid refresh token"));

        assertThrows(RefreshTokenException.class,
                () -> authenticationService.logoutAll(new RefreshTokenRequest(REFRESH_TOKEN)));

        verify(refreshTokenService).getUserByRefreshToken(REFRESH_TOKEN);
        verifyNoMoreInteractions(refreshTokenService);
    }

    private UserEntity user() {
        return UserEntity.builder()
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .role(UserRole.USER)
                .build();
    }

    private void assertTokenPair(AuthenticationResponse response, String accessToken, String refreshToken) {
        assertEquals(accessToken, response.getAccessToken());
        assertEquals(refreshToken, response.getRefreshToken());
    }
}
