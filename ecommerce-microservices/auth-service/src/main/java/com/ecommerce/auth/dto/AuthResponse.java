package com.ecommerce.auth.dto;

public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private String username;
    private String role;
    private long expiresInMs;

    public AuthResponse(String token, String username, String role, long expiresInMs) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.expiresInMs = expiresInMs;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }
}
