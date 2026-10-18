package com.ms.hrmanagement.config;

import com.ms.hrmanagement.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements CommandLineRunner {

    private final AuthService authService;

    public AdminAccountInitializer(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void run(String... args) throws Exception {
        authService.createAdminIfMissing();
    }
}
