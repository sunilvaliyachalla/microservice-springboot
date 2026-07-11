package com.ecommerce.auth;

import com.ecommerce.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Functional tests running the full auth stack (controller -> service ->
 * repository -> H2) with the real BCrypt encoder, real JWT signing, and the
 * SUPERADMIN seeded by the startup runner. Nothing is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode login(String username, String password, int expectedStatus) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode createUser(String callerRole, String username, String password, String role,
                                int expectedStatus) throws Exception {
        var request = post("/api/auth/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("username", username, "password", password, "role", role)));
        if (callerRole != null) {
            request = request.header("X-Auth-Role", callerRole);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    // --- login ---

    @Test
    void seededSuperadminCanLoginAndReceivesValidJwt() throws Exception {
        JsonNode response = login("root", "RootPass123!", 200);

        assertEquals("root", response.get("username").asText());
        assertEquals("SUPERADMIN", response.get("role").asText());
        assertEquals("Bearer", response.get("tokenType").asText());

        // The issued token is verifiable with the shared secret and carries
        // the identity claims the gateway relies on.
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(response.get("token").asText())
                .getPayload();
        assertEquals("root", claims.getSubject());
        assertEquals("SUPERADMIN", claims.get("role"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        JsonNode response = login("root", "WrongPassword1", 401);
        assertEquals("Invalid credentials", response.get("message").asText());
    }

    @Test
    void loginWithUnknownUserReturns401WithSameMessage() throws Exception {
        JsonNode response = login("nobody-here", "Password123", 401);
        assertEquals("Invalid credentials", response.get("message").asText());
    }

    @Test
    void loginValidationRejectsShortPassword() throws Exception {
        login("root", "short", 400);
    }

    // --- user creation & role hierarchy, full chain ---

    @Test
    void superadminCreatesAdminWhoCreatesManagerWhoCreatesUser() throws Exception {
        String admin = unique("admin");
        String manager = unique("manager");
        String user = unique("user");

        // SUPERADMIN -> ADMIN
        JsonNode created = createUser("SUPERADMIN", admin, "AdminPass123", "ADMIN", 201);
        assertEquals("ADMIN", created.get("role").asText());

        // The created admin can actually log in (full functional chain).
        JsonNode adminLogin = login(admin, "AdminPass123", 200);
        assertEquals("ADMIN", adminLogin.get("role").asText());

        // ADMIN -> MANAGER, MANAGER -> USER
        createUser("ADMIN", manager, "ManagerPass123", "MANAGER", 201);
        createUser("MANAGER", user, "UserPass1234", "USER", 201);

        // The chain ends: USER may not create anyone.
        createUser("USER", unique("blocked"), "Password123", "USER", 403);
    }

    @Test
    void equalRoleCreationIsForbidden() throws Exception {
        createUser("ADMIN", unique("admin2"), "Password123", "ADMIN", 403);
        createUser("SUPERADMIN", unique("root2"), "Password123", "SUPERADMIN", 403);
    }

    @Test
    void missingRoleHeaderIsForbidden() throws Exception {
        createUser(null, unique("anon"), "Password123", "USER", 403);
    }

    @Test
    void forgedNonsenseRoleHeaderIsForbidden() throws Exception {
        createUser("WIZARD", unique("wiz"), "Password123", "USER", 403);
    }

    @Test
    void unknownTargetRoleIsBadRequest() throws Exception {
        createUser("SUPERADMIN", unique("odd"), "Password123", "WIZARD", 400);
    }

    @Test
    void duplicateUsernameIsConflict() throws Exception {
        String name = unique("dupe");
        createUser("SUPERADMIN", name, "Password123", "USER", 201);
        JsonNode response = createUser("SUPERADMIN", name, "Password123", "USER", 409);
        assertEquals("Username already taken", response.get("message").asText());
    }

    @Test
    void createUserValidationRejectsWeakInput() throws Exception {
        createUser("SUPERADMIN", "ab", "Password123", "USER", 400);      // username too short
        createUser("SUPERADMIN", unique("ok"), "short", "USER", 400);    // password too short
    }

    @Test
    void passwordsArePersistedAsBcryptHashes() throws Exception {
        String name = unique("hashed");
        createUser("SUPERADMIN", name, "Password123", "USER", 201);

        String stored = userRepository.findByUsername(name).orElseThrow().getPasswordHash();
        assertNotEquals("Password123", stored);
        assertTrue(stored.startsWith("$2"), "must be a BCrypt hash");
    }

    @Test
    void seededSuperadminExistsExactlyOnce() {
        assertTrue(userRepository.existsByUsername("root"));
        assertEquals(1, userRepository.findAll().stream()
                .filter(u -> "SUPERADMIN".equals(u.getRole()) && "root".equals(u.getUsername()))
                .count());
    }
}
