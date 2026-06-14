package com.ecommerce.auth.config;

import com.ecommerce.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public ApplicationRunner seedSuperAdmin(
            AuthService authService,
            @Value("${superadmin.username:superadmin}") String username,
            @Value("${superadmin.password:}") String password) {
        return args -> {
            if (password == null || password.isBlank()) {
                // No password configured: skip seeding so we never create an
                // account with a blank/guessable password.
                return;
            }
            authService.seedSuperAdmin(username, password);
        };
    }
}
