package ru.example.prodbackend.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.example.prodbackend.auth.entity.RefreshTokenEntity;
import ru.example.prodbackend.auth.repository.RefreshTokenRepository;
import ru.example.prodbackend.exception.RefreshTokenException;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final int EXPIRATION_SECONDS = 604_800;
    private static final String RAW_TOKEN = "39bdf02d-95dc-43fa-b95e-e75fddc40aef";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private final UserEntity user = UserEntity.builder()
            .email("user@example.com")
            .password("encoded-password")
            .role(UserRole.USER)
            .build();

    @BeforeEach
    void setUp() {
        // Unit-тест не поднимает Spring, поэтому значение @Value задаём вручную.
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationSeconds", EXPIRATION_SECONDS);
    }

    @Test
    void createRefreshTokenReturnsRawTokenAndStoresOnlyItsHash() {
        Instant before = Instant.now();
        String rawToken = refreshTokenService.createRefreshToken(user);
        Instant after = Instant.now();

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshTokenEntity saved = captor.getValue();

        assertEquals(rawToken, UUID.fromString(rawToken).toString());
        assertEquals(hashToken(rawToken), saved.getTokenHash());
        assertNotEquals(rawToken, saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        assertSame(user, saved.getUser());
        assertBetween(saved.getCreatedAt(), before, after);
        assertEquals(saved.getCreatedAt().plusSeconds(EXPIRATION_SECONDS), saved.getExpiryAt());
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @Test
    void createRefreshTokenCreatesIndependentSessionsForSameUser() {
        String firstToken = refreshTokenService.createRefreshToken(user);
        String secondToken = refreshTokenService.createRefreshToken(user);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        var sessions = captor.getAllValues();
        assertNotEquals(firstToken, secondToken);
        assertNotSame(sessions.get(0), sessions.get(1));
        assertEquals(hashToken(firstToken), sessions.get(0).getTokenHash());
        assertEquals(hashToken(secondToken), sessions.get(1).getTokenHash());
        assertSame(user, sessions.get(0).getUser());
        assertSame(user, sessions.get(1).getUser());
        // Создание новой сессии не удаляет сессии другого устройства.
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @Test
    void verifyExpirationReturnsUnexpiredEntityWithoutChangingIt() {
        RefreshTokenEntity entity = session(Instant.now().plusSeconds(60));

        assertSame(entity, refreshTokenService.verifyExpiration(entity));
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void verifyExpirationRejectsExpiredEntityWithoutDeletingIt() {
        RefreshTokenEntity entity = session(Instant.EPOCH);

        assertThrows(RefreshTokenException.class, () -> refreshTokenService.verifyExpiration(entity));
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void rotateRefreshTokenUpdatesSameSessionWithNewHashAndLifetime() {
        RefreshTokenEntity entity = session(Instant.now().plusSeconds(60));
        UUID originalId = entity.getId();
        Instant originalCreatedAt = entity.getCreatedAt();
        when(refreshTokenRepository.findByTokenHash(hashToken(RAW_TOKEN))).thenReturn(Optional.of(entity));

        Instant before = Instant.now();
        String newRawToken = refreshTokenService.rotateRefreshToken(RAW_TOKEN);
        Instant after = Instant.now();

        assertNotEquals(RAW_TOKEN, newRawToken);
        assertEquals(newRawToken, UUID.fromString(newRawToken).toString());
        assertEquals(hashToken(newRawToken), entity.getTokenHash());
        assertBetween(entity.getExpiryAt(), before.plusSeconds(EXPIRATION_SECONDS), after.plusSeconds(EXPIRATION_SECONDS));
        assertEquals(originalId, entity.getId());
        assertEquals(originalCreatedAt, entity.getCreatedAt());
        assertSame(user, entity.getUser());
        verify(refreshTokenRepository).findByTokenHash(hashToken(RAW_TOKEN));
        verify(refreshTokenRepository).save(entity);
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @Test
    void rotateRefreshTokenRejectsUnknownTokenWithoutSavingAnything() {
        when(refreshTokenRepository.findByTokenHash(hashToken(RAW_TOKEN))).thenReturn(Optional.empty());

        assertThrows(RefreshTokenException.class, () -> refreshTokenService.rotateRefreshToken(RAW_TOKEN));

        verify(refreshTokenRepository).findByTokenHash(hashToken(RAW_TOKEN));
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @Test
    void rotateRefreshTokenRejectsExpiredTokenWithoutChangingSession() {
        RefreshTokenEntity entity = session(Instant.EPOCH);
        when(refreshTokenRepository.findByTokenHash(hashToken(RAW_TOKEN))).thenReturn(Optional.of(entity));

        assertThrows(RefreshTokenException.class, () -> refreshTokenService.rotateRefreshToken(RAW_TOKEN));

        assertEquals(hashToken(RAW_TOKEN), entity.getTokenHash());
        assertEquals(Instant.EPOCH, entity.getExpiryAt());
        verify(refreshTokenRepository).findByTokenHash(hashToken(RAW_TOKEN));
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rotateRefreshTokenRejectsMissingTokenBeforeAccessingRepository(String rawToken) {
        assertThrows(RefreshTokenException.class, () -> refreshTokenService.rotateRefreshToken(rawToken));
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void getUserByRefreshTokenReturnsOwnerOfValidSession() {
        RefreshTokenEntity entity = session(Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(hashToken(RAW_TOKEN))).thenReturn(Optional.of(entity));

        assertSame(user, refreshTokenService.getUserByRefreshToken(RAW_TOKEN));

        verify(refreshTokenRepository).findByTokenHash(hashToken(RAW_TOKEN));
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @Test
    void getUserByRefreshTokenRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(hashToken(RAW_TOKEN))).thenReturn(Optional.empty());

        assertThrows(RefreshTokenException.class, () -> refreshTokenService.getUserByRefreshToken(RAW_TOKEN));

        verify(refreshTokenRepository).findByTokenHash(hashToken(RAW_TOKEN));
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @Test
    void getUserByRefreshTokenRejectsExpiredToken() {
        RefreshTokenEntity entity = session(Instant.EPOCH);
        when(refreshTokenRepository.findByTokenHash(hashToken(RAW_TOKEN))).thenReturn(Optional.of(entity));

        assertThrows(RefreshTokenException.class, () -> refreshTokenService.getUserByRefreshToken(RAW_TOKEN));

        verify(refreshTokenRepository).findByTokenHash(hashToken(RAW_TOKEN));
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void getUserByRefreshTokenRejectsMissingTokenBeforeAccessingRepository(String rawToken) {
        assertThrows(RefreshTokenException.class, () -> refreshTokenService.getUserByRefreshToken(rawToken));
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void revokeRefreshTokenDeletesByHashNotRawToken() {
        refreshTokenService.revokeRefreshToken(RAW_TOKEN);

        verify(refreshTokenRepository).deleteByTokenHash(hashToken(RAW_TOKEN));
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void revokeRefreshTokenRejectsMissingTokenBeforeAccessingRepository(String rawToken) {
        assertThrows(RefreshTokenException.class, () -> refreshTokenService.revokeRefreshToken(rawToken));
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void deleteAllByUserDeletesOnlySpecifiedUsersSessions() {
        refreshTokenService.deleteAllByUser(user);

        verify(refreshTokenRepository).deleteAllByUser(user);
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    private RefreshTokenEntity session(Instant expiryAt) {
        return RefreshTokenEntity.builder()
                .id(UUID.randomUUID())
                .tokenHash(hashToken(RAW_TOKEN))
                .user(user)
                .createdAt(Instant.EPOCH)
                .expiryAt(expiryAt)
                .build();
    }

    private void assertBetween(Instant actual, Instant before, Instant after) {
        assertFalse(actual.isBefore(before), "Timestamp must not be before the operation");
        assertFalse(actual.isAfter(after), "Timestamp must not be after the operation");
    }

    private String hashToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
