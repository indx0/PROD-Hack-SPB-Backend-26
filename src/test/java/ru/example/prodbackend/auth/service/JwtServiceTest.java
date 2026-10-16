package ru.example.prodbackend.auth.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final SecretKey TEST_KEY = Jwts.SIG.HS256.key().build();

    private static final String TEST_SECRET =
            Encoders.BASE64.encode(TEST_KEY.getEncoded());

    private static final long EXPIRATION_MS = 1_200_000;

    private final JwtService jwtService = new JwtService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                jwtService,
                "secretKey",
                TEST_SECRET
        );

        ReflectionTestUtils.setField(
                jwtService,
                "accessTokenExpirationMs",
                EXPIRATION_MS
        );
    }

    @Test
    void generatedTokenContainsUserRoleAndExpiration() {
        var user = User.withUsername("user@example.com")
                .password("unused")
                .authorities("USER")
                .build();

        String token = jwtService.generateToken(user);
        var claims = jwtService.extractAllClaims(token);

        assertEquals("user@example.com", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
        assertNotNull(claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());

        assertEquals(
                EXPIRATION_MS,
                claims.getExpiration().getTime()
                        - claims.getIssuedAt().getTime()
        );
    }

    @Test
    void rejectsExpiredToken() {
        String token = Jwts.builder()
                .subject("user@example.com")
                .expiration(new Date(0))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();

        assertThrows(
                ExpiredJwtException.class,
                () -> jwtService.extractAllClaims(token)
        );
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        SecretKey otherKey = Jwts.SIG.HS256.key().build();

        String token = Jwts.builder()
                .subject("user@example.com")
                .expiration(
                        new Date(System.currentTimeMillis() + EXPIRATION_MS)
                )
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThrows(
                SignatureException.class,
                () -> jwtService.extractAllClaims(token)
        );
    }

    @Test
    void rejectsTokenWithoutSubject() {
        String token = Jwts.builder()
                .expiration(
                        new Date(System.currentTimeMillis() + EXPIRATION_MS)
                )
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();

        assertThrows(
                MalformedJwtException.class,
                () -> jwtService.extractAllClaims(token)
        );
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        String token = Jwts.builder()
                .subject("user@example.com")
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();

        assertThrows(
                MalformedJwtException.class,
                () -> jwtService.extractAllClaims(token)
        );
    }

    @Test
    void rejectsMalformedOrEmptyToken() {
        assertThrows(
                JwtException.class,
                () -> jwtService.extractAllClaims("not-a-jwt")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> jwtService.extractAllClaims("")
        );
    }

    @Test
    void rejectsWeakSigningKey() {
        String weakSecret = Encoders.BASE64.encode(new byte[8]);

        ReflectionTestUtils.setField(
                jwtService,
                "secretKey",
                weakSecret
        );

        var user = User.withUsername("user@example.com")
                .password("unused")
                .authorities("USER")
                .build();

        assertThrows(
                WeakKeyException.class,
                () -> jwtService.generateToken(user)
        );
    }
}