package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthRequest;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.CreateUserRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.exception.AuthException;
import com.ecommerce.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getUsername(), user.getRole(), jwtService.getExpirationMs());
    }

    /**
     * Create a new user. Only callers whose role outranks the requested role
     * may do this (e.g. SUPERADMIN can create ADMIN/MANAGER/USER).
     *
     * @param callerRole the authenticated caller's role, injected by the gateway
     */
    public UserResponse createUser(CreateUserRequest request, String callerRole) {
        Role creator = Role.from(callerRole);
        Role target = Role.from(request.getRole());

        if (creator == null) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Your role is not permitted to create users");
        }
        if (target == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Unknown role: " + request.getRole());
        }
        if (creator.getRank() <= target.getRank()) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "You may only create users with a lower privilege than your own");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AuthException(HttpStatus.CONFLICT, "Username already taken");
        }

        User user = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                target.name());
        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getUsername(), saved.getRole());
    }

    /**
     * Seed the initial SUPERADMIN account if none exists.
     */
    public void seedSuperAdmin(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User admin = new User(username, passwordEncoder.encode(rawPassword), Role.SUPERADMIN.name());
        userRepository.save(admin);
        log.warn("Seeded initial SUPERADMIN '{}'. Change its password immediately.", username);
    }
}
