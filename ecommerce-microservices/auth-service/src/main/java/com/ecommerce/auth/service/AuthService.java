package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthRequest;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.exception.AuthException;
import com.ecommerce.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

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

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AuthException(HttpStatus.CONFLICT, "Username already taken");
        }
        User user = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                "USER");
        userRepository.save(user);
        return buildResponse(user);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return buildResponse(user);
    }

    private AuthResponse buildResponse(User user) {
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getUsername(), jwtService.getExpirationMs());
    }
}
