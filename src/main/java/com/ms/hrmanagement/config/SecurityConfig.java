package com.ms.hrmanagement.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login",
                    "/register",
                    "/auth/check-username",
                    "/auth/check-email",
                    "/css/**",
                    "/js/**",
                    "/images/**"
                ).permitAll()

                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/hr/**").hasRole("HR")
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers("/employee/dashboard").hasRole("EMPLOYEE")
                .requestMatchers("/intern/dashboard").hasRole("INTERN")
                .requestMatchers(HttpMethod.POST, "/account/password").authenticated()

                .requestMatchers(HttpMethod.GET, "/employees/add", "/employees/edit/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.POST, "/employees/add", "/employees/edit/**", "/employees/delete/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.GET, "/employees", "/employees/**").hasAnyRole("ADMIN", "HR", "MANAGER")

                .requestMatchers(HttpMethod.GET, "/interns/add").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.POST, "/interns/add", "/interns/delete/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.GET, "/interns/edit/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.POST, "/interns/edit/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.GET, "/interns", "/interns/**").hasAnyRole("ADMIN", "HR", "MANAGER")

                .requestMatchers(HttpMethod.GET, "/attendance/add").hasAnyRole("MANAGER", "EMPLOYEE", "INTERN")
                .requestMatchers(HttpMethod.POST, "/attendance/add").hasAnyRole("MANAGER", "EMPLOYEE", "INTERN")
                .requestMatchers(HttpMethod.POST, "/attendance/check-out").hasAnyRole("MANAGER", "EMPLOYEE", "INTERN")
                .requestMatchers(HttpMethod.POST, "/attendance/submit-department").hasRole("MANAGER")
                .requestMatchers(HttpMethod.GET, "/attendance/edit/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.POST, "/attendance/edit/**", "/attendance/delete/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.GET, "/attendance", "/attendance/**").hasAnyRole("ADMIN", "HR", "MANAGER", "EMPLOYEE", "INTERN")

                .requestMatchers(HttpMethod.POST, "/leave-requests/edit/**", "/leave-requests/delete/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.GET, "/leave-requests/edit/**").hasAnyRole("ADMIN", "HR", "MANAGER")
                .requestMatchers(HttpMethod.GET, "/leave-requests/add").hasAnyRole("MANAGER", "EMPLOYEE", "INTERN")
                .requestMatchers(HttpMethod.POST, "/leave-requests/add").hasAnyRole("MANAGER", "EMPLOYEE", "INTERN")
                .requestMatchers(HttpMethod.GET, "/leave-requests", "/leave-requests/**").hasAnyRole("ADMIN", "HR", "MANAGER", "EMPLOYEE", "INTERN")

                .requestMatchers("/portal").authenticated()
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(roleBasedSuccessHandler())
                .permitAll()
            )

            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler roleBasedSuccessHandler() {

        return (request, response, authentication) -> {
            String targetUrl = resolveTargetUrl(authentication);
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.sendRedirect(targetUrl);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private String resolveTargetUrl(Authentication authentication) {

        if (hasRole(authentication, "ROLE_ADMIN")) {
            return "/admin/dashboard";
        }

        if (hasRole(authentication, "ROLE_HR")) {
            return "/hr/dashboard";
        }

        if (hasRole(authentication, "ROLE_MANAGER")) {
            return "/manager/dashboard";
        }

        if (hasRole(authentication, "ROLE_EMPLOYEE")) {
            return "/employee/dashboard";
        }

        if (hasRole(authentication, "ROLE_INTERN")) {
            return "/intern/dashboard";
        }

        return "/portal";
    }

    private boolean hasRole(Authentication authentication, String role) {

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
