package com.krishnamouli.chronos.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple token-based authentication for Chronos.
 * Supports multiple authentication tokens for different clients.
 * 
 * SECURITY NOTE: This is basic authentication suitable for internal
 * deployments.
 * For production internet-facing deployments, use TLS + proper OAuth2/JWT.
 */
public class AuthenticationManager {

    private final Map<String, String> tokenHashes; // tokenId -> SHA-256 hash
    private final boolean authEnabled;

    /**
     * Creates authentication manager.
     * 
     * @param authEnabled Whether authentication is enabled
     */
    public AuthenticationManager(boolean authEnabled) {
        this.authEnabled = authEnabled;
        this.tokenHashes = new ConcurrentHashMap<>();
    }

    /**
     * Add an authentication token.
     * 
     * @param tokenId Identifier for the token (e.g., "service-a")
     * @param token   The actual authentication token
     */
    public void addToken(String tokenId, String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        String hash = hashToken(token);
        tokenHashes.put(tokenId, hash);
    }

    /**
     * Verify if a token is valid.
     * 
     * @param token Token to verify
     * @return true if valid, false otherwise
     */
    public boolean verifyToken(String token) {
        if (!authEnabled) {
            return true; // Auth disabled, allow all
        }

        if (token == null || token.isEmpty()) {
            return false;
        }

        String hash = hashToken(token);
        return tokenHashes.containsValue(hash);
    }

    /**
     * Generate a secure random token.
     * 
     * @param length Token length in bytes (will be base64 encoded)
     * @return Base64-encoded random token
     */
    public static String generateToken(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash token using SHA-256 for secure storage.
     * Never store tokens in plaintext!
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Check if authentication is enabled.
     */
    public boolean isAuthEnabled() {
        return authEnabled;
    }

    /**
     * Get count of registered tokens.
     */
    public int getTokenCount() {
        return tokenHashes.size();
    }
}
