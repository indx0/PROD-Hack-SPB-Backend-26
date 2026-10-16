package ru.example.prodbackend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.entity.RefreshTokenEntity;
import ru.example.prodbackend.auth.repository.RefreshTokenRepository;
import ru.example.prodbackend.exception.RefreshTokenException;
import ru.example.prodbackend.user.entity.UserEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token.expiration.sec}")
    private Integer refreshTokenExpirationSeconds;

    @Transactional
    public String createRefreshToken(UserEntity user) {
        Instant now = Instant.now();
        String rawToken = UUID.randomUUID().toString();

        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .tokenHash(hashToken(rawToken))
                .createdAt(now)
                .expiryAt(now.plusSeconds(refreshTokenExpirationSeconds))
                .user(user)
                .build();

        refreshTokenRepository.save(entity);

        return rawToken;
    }

    @Transactional
    public void deleteAllByUser(UserEntity user) {
        refreshTokenRepository.deleteAllByUser(user);
    }

    @Transactional(readOnly = true)
    public RefreshTokenEntity verifyExpiration(RefreshTokenEntity refreshTokenEntity) {
        if(!refreshTokenEntity.getExpiryAt().isAfter(Instant.now())) {
            throw new RefreshTokenException("Refresh token expired");
        }
        return refreshTokenEntity;
    }

    @Transactional
    public String rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new RefreshTokenException("Refresh token is missing");

        String oldTokenHash = hashToken(rawToken);

        RefreshTokenEntity entity = refreshTokenRepository
                .findByTokenHash(oldTokenHash)
                .orElseThrow(() -> new RefreshTokenException("Refresh token is invalid"));
        verifyExpiration(entity);

        String newRawToken = UUID.randomUUID().toString();
        Instant now = Instant.now();

        entity.setTokenHash(hashToken(newRawToken));
        entity.setExpiryAt(now.plusSeconds(refreshTokenExpirationSeconds));

        refreshTokenRepository.save(entity);

        return newRawToken;
    }

    @Transactional
    public UserEntity getUserByRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new RefreshTokenException("Refresh token is missing");

        RefreshTokenEntity entity = refreshTokenRepository
                .findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new RefreshTokenException("Refresh token is invalid"));
        verifyExpiration(entity);

        return entity.getUser();
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new RefreshTokenException("Refresh token is missing");
        refreshTokenRepository.deleteByTokenHash(hashToken(rawToken));
    }

    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
