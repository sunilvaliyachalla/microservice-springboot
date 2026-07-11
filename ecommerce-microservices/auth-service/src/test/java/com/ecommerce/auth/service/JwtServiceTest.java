package com.ecommerce.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-0123456789-abcdefghijklmnop";
    private static final long EXPIRATION_MS = 3_600_000L;

    private final JwtService jwtService = new JwtService(SECRET, EXPIRATION_MS);

    private Claims parse(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void generatedTokenCarriesSubjectAndRole() {
        String token = jwtService.generateToken("alice", "ADMIN");

        Claims claims = parse(token, SECRET);
        assertEquals("alice", claims.getSubject());
        assertEquals("ADMIN", claims.get("role"));
    }

    @Test
    void generatedTokenExpiresAtConfiguredTime() {
        long before = System.currentTimeMillis();
        String token = jwtService.generateToken("alice", "USER");
        long after = System.currentTimeMillis();

        Date expiration = parse(token, SECRET).getExpiration();
        // JWT timestamps have second precision; allow rounding slack.
        assertTrue(expiration.getTime() >= before + EXPIRATION_MS - 1000);
        assertTrue(expiration.getTime() <= after + EXPIRATION_MS + 1000);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(SECRET, -1000);
        String token = shortLived.generateToken("alice", "USER");

        assertThrows(ExpiredJwtException.class, () -> parse(token, SECRET));
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String token = jwtService.generateToken("alice", "USER");

        assertThrows(JwtException.class,
                () -> parse(token, "another-secret-key-that-is-also-long-enough-0123456789"));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken("alice", "USER");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThrows(JwtException.class, () -> parse(tampered, SECRET));
    }

    @Test
    void exposesConfiguredExpiration() {
        assertEquals(EXPIRATION_MS, jwtService.getExpirationMs());
    }
}
