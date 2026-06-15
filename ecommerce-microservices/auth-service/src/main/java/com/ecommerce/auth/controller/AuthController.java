package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AuthRequest;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.CreateUserRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Create a user. Protected: the gateway only forwards this request with a
     * valid JWT and injects the caller's role via the X-Auth-Role header.
     */
    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = "X-Auth-Role", required = false) String callerRole) {
        UserResponse created = authService.createUser(request, callerRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
