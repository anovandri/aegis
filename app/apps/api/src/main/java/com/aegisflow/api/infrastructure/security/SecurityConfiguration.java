package com.aegisflow.api.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "aegisflow.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfiguration {
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;
    private final ObjectProvider<EnterpriseOAuth2UserService> enterpriseOAuth2UserService;
    private final ObjectProvider<EnterpriseOidcUserService> enterpriseOidcUserService;

    public SecurityConfiguration(
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
            ObjectProvider<EnterpriseOAuth2UserService> enterpriseOAuth2UserService,
            ObjectProvider<EnterpriseOidcUserService> enterpriseOidcUserService
    ) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.enterpriseOAuth2UserService = enterpriseOAuth2UserService;
        this.enterpriseOidcUserService = enterpriseOidcUserService;
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/identity-providers").permitAll()
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        .anyRequest().authenticated()
                );
        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(enterpriseOAuth2UserService.getIfAvailable())
                            .oidcUserService(enterpriseOidcUserService.getIfAvailable())
                    )
            );
        }
        return http.build();
    }
}
