package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthRequest;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.CreateUserRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.exception.AuthException;
import com.ecommerce.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests using a REAL BCrypt encoder and REAL JWT signing — only the
 * repository is mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-0123456789-abcdefghijklmnop";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, new JwtService(SECRET, 3_600_000L));
    }

    private User user(String username, String rawPassword, String role) {
        return new User(username, passwordEncoder.encode(rawPassword), role);
    }

    private AuthRequest authRequest(String username, String password) {
        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private CreateUserRequest createRequest(String username, String password, String role) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setRole(role);
        return request;
    }

    // --- login ---

    @Test
    void loginWithValidCredentialsReturnsTokenAndRole() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user("alice", "Password123", "ADMIN")));

        AuthResponse response = authService.login(authRequest("alice", "Password123"));

        assertNotNull(response.getToken());
        assertEquals("alice", response.getUsername());
        assertEquals("ADMIN", response.getRole());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    void loginWithWrongPasswordThrows401() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user("alice", "Password123", "USER")));

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(authRequest("alice", "WrongPassword")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void loginWithUnknownUserThrows401WithSameMessageAsWrongPassword() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user("alice", "Password123", "USER")));

        AuthException unknownUser = assertThrows(AuthException.class,
                () -> authService.login(authRequest("ghost", "Password123")));
        AuthException wrongPassword = assertThrows(AuthException.class,
                () -> authService.login(authRequest("alice", "WrongPassword")));

        // Identical responses prevent username enumeration.
        assertEquals(unknownUser.getMessage(), wrongPassword.getMessage());
        assertEquals(unknownUser.getStatus(), wrongPassword.getStatus());
    }

    // --- createUser role hierarchy ---

    @ParameterizedTest
    @CsvSource({
            "SUPERADMIN, ADMIN",
            "SUPERADMIN, MANAGER",
            "SUPERADMIN, USER",
            "ADMIN, MANAGER",
            "ADMIN, USER",
            "MANAGER, USER",
    })
    void higherRoleMayCreateStrictlyLowerRole(String caller, String target) {
        when(userRepository.existsByUsername("newbie")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponse response = authService.createUser(
                createRequest("newbie", "Password123", target), caller);

        assertEquals(target, response.getRole());
    }

    @ParameterizedTest
    @CsvSource({
            "SUPERADMIN, SUPERADMIN",
            "ADMIN, ADMIN",
            "ADMIN, SUPERADMIN",
            "MANAGER, MANAGER",
            "MANAGER, ADMIN",
            "USER, USER",
            "USER, ADMIN",
    })
    void equalOrHigherTargetRoleIsForbidden(String caller, String target) {
        AuthException ex = assertThrows(AuthException.class,
                () -> authService.createUser(createRequest("newbie", "Password123", target), caller));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void missingCallerRoleIsForbidden() {
        AuthException ex = assertThrows(AuthException.class,
                () -> authService.createUser(createRequest("newbie", "Password123", "USER"), null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void unknownTargetRoleIsBadRequest() {
        AuthException ex = assertThrows(AuthException.class,
                () -> authService.createUser(createRequest("newbie", "Password123", "WIZARD"), "SUPERADMIN"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void duplicateUsernameIsConflict() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.createUser(createRequest("taken", "Password123", "USER"), "SUPERADMIN"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void createdUserPasswordIsStoredAsBcryptHashNotPlaintext() {
        when(userRepository.existsByUsername("newbie")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.createUser(createRequest("newbie", "Password123", "USER"), "SUPERADMIN");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String stored = captor.getValue().getPasswordHash();
        assertNotEquals("Password123", stored);
        assertTrue(stored.startsWith("$2"), "must be a BCrypt hash");
        assertTrue(passwordEncoder.matches("Password123", stored));
    }

    // --- superadmin seeding ---

    @Test
    void seedSuperAdminCreatesAccountWhenMissing() {
        when(userRepository.existsByUsername("root")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.seedSuperAdmin("root", "RootPass123!");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("SUPERADMIN", captor.getValue().getRole());
        assertTrue(passwordEncoder.matches("RootPass123!", captor.getValue().getPasswordHash()));
    }

    @Test
    void seedSuperAdminIsIdempotent() {
        when(userRepository.existsByUsername("root")).thenReturn(true);

        authService.seedSuperAdmin("root", "RootPass123!");

        verify(userRepository, never()).save(any());
    }
}
