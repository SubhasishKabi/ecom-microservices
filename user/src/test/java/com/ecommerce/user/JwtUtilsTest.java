package com.ecommerce.user;

import com.ecommerce.user.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpirationMs", 7200000L);
    }

    @Test
    void testGenerateAndValidateAccessToken() {
        String token = jwtUtils.generateAccessToken(1L, "test@example.com", "USER");
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals("1", jwtUtils.extractUserId(token));
        assertEquals("test@example.com", jwtUtils.extractEmail(token));
    }

    @Test
    void testGenerateAndValidateRefreshToken() {
        String refreshToken = jwtUtils.generateRefreshToken(2L, "admin@example.com");
        assertNotNull(refreshToken);
        assertTrue(jwtUtils.validateToken(refreshToken));
        assertEquals("2", jwtUtils.extractUserId(refreshToken));
    }

    @Test
    void testInvalidToken() {
        assertFalse(jwtUtils.validateToken("invalid.token.here"));
    }
}
