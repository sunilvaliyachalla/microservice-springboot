package com.ecommerce.gateway.filter;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Functional tests of the gateway middleware: a real gateway instance routes
 * to a live WireMock backend, so these tests verify actual request flow,
 * header rewriting, and rejection behaviour end to end.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gateway.routes[0].id=protected-backend",
                "spring.cloud.gateway.routes[0].uri=http://localhost:${wiremock.server.port}",
                "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/test/**",
                "spring.cloud.gateway.routes[1].id=auth-backend",
                "spring.cloud.gateway.routes[1].uri=http://localhost:${wiremock.server.port}",
                "spring.cloud.gateway.routes[1].predicates[0]=Path=/api/auth/**",
                "spring.cloud.gateway.routes[2].id=unused",
                "spring.cloud.gateway.routes[2].uri=http://localhost:${wiremock.server.port}",
                "spring.cloud.gateway.routes[2].predicates[0]=Path=/api/unused/**",
                "jwt.secret=test-secret-key-for-jwt-signing-0123456789-abcdefghijklmnop",
                "security.public-paths=/api/auth/login",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
        })
@AutoConfigureWireMock(port = 0)
class JwtAuthenticationFilterIntegrationTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-0123456789-abcdefghijklmnop";

    @Autowired
    private WebTestClient webTestClient;

    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void resetStubs() {
        WireMock.reset();
        stubFor(WireMock.get(urlEqualTo("/api/test/ping"))
                .willReturn(aResponse().withStatus(200).withBody("pong")));
        stubFor(WireMock.post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"issued\"}")));
        stubFor(WireMock.post(urlEqualTo("/api/auth/users"))
                .willReturn(aResponse().withStatus(201)));
    }

    private String token(String username, String role, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("role", role))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    // --- rejection cases ---

    @Test
    void requestWithoutTokenIsRejectedWith401AndNeverReachesBackend() {
        webTestClient.get().uri("/api/test/ping")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class)
                .value(body -> org.junit.jupiter.api.Assertions.assertTrue(body.contains("Unauthorized")));

        WireMock.verify(0, getRequestedFor(urlEqualTo("/api/test/ping")));
    }

    @Test
    void malformedTokenIsRejected() {
        webTestClient.get().uri("/api/test/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void nonBearerAuthorizationIsRejected() {
        webTestClient.get().uri("/api/test/ping")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void expiredTokenIsRejected() {
        webTestClient.get().uri("/api/test/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice", "USER", -60_000))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-that-is-also-long-enough-0123456789".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("alice")
                .claims(Map.of("role", "SUPERADMIN"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        webTestClient.get().uri("/api/test/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .exchange()
                .expectStatus().isUnauthorized();

        WireMock.verify(0, getRequestedFor(urlEqualTo("/api/test/ping")));
    }

    // --- pass-through cases ---

    @Test
    void validTokenIsForwardedWithTrustedIdentityHeaders() {
        webTestClient.get().uri("/api/test/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice", "ADMIN", 60_000))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("pong");

        WireMock.verify(getRequestedFor(urlEqualTo("/api/test/ping"))
                .withHeader("X-Auth-User", equalTo("alice"))
                .withHeader("X-Auth-Role", equalTo("ADMIN")));
    }

    @Test
    void clientForgedIdentityHeadersAreReplacedWithTokenIdentity() {
        webTestClient.get().uri("/api/test/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice", "USER", 60_000))
                .header("X-Auth-User", "hacker")
                .header("X-Auth-Role", "SUPERADMIN")
                .exchange()
                .expectStatus().isOk();

        // The backend must see the token's identity, not the forged headers.
        WireMock.verify(getRequestedFor(urlEqualTo("/api/test/ping"))
                .withHeader("X-Auth-User", equalTo("alice"))
                .withHeader("X-Auth-Role", equalTo("USER")));
    }

    @Test
    void loginPathIsPublicAndForwardedWithoutToken() {
        webTestClient.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().isOk();

        WireMock.verify(postRequestedFor(urlEqualTo("/api/auth/login")));
    }

    @Test
    void publicPathDoesNotReceiveTrustedIdentityHeaders() {
        webTestClient.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().isOk();

        WireMock.verify(postRequestedFor(urlEqualTo("/api/auth/login"))
                .withHeader("X-Auth-User", absent()));
    }

    @Test
    void userCreationEndpointIsNotPublic() {
        webTestClient.post().uri("/api/auth/users")
                .exchange()
                .expectStatus().isUnauthorized();

        WireMock.verify(0, postRequestedFor(urlEqualTo("/api/auth/users")));
    }

    @Test
    void userCreationEndpointWorksWithValidToken() {
        webTestClient.post().uri("/api/auth/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("root", "SUPERADMIN", 60_000))
                .exchange()
                .expectStatus().isCreated();

        WireMock.verify(postRequestedFor(urlEqualTo("/api/auth/users"))
                .withHeader("X-Auth-Role", equalTo("SUPERADMIN")));
    }

    // --- CORS preflight ---

    @Test
    void corsPreflightFromAllowedOriginBypassesAuthentication() {
        webTestClient.options().uri("/api/test/ping")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");

        WireMock.verify(0, getRequestedFor(anyUrl()));
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejectedByCorsPolicy() {
        webTestClient.options().uri("/api/test/ping")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }
}
