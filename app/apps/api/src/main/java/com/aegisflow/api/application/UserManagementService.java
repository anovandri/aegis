package com.aegisflow.api.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserManagementService {
    private final JdbcTemplate jdbcTemplate;
    private final UserDetailsManager userDetailsManager;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(JdbcTemplate jdbcTemplate, UserDetailsManager userDetailsManager, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDetailsManager = userDetailsManager;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserSummary> listUsers() {
        java.util.Map<String, MutableUserSummary> users = new java.util.LinkedHashMap<>();
        jdbcTemplate.query(
                """
                        SELECT u.username, u.enabled, a.authority
                        FROM users u
                        LEFT JOIN authorities a ON a.username = u.username
                        ORDER BY u.username, a.authority
                        """,
                resultSet -> {
                    String username = resultSet.getString("username");
                    boolean enabled = resultSet.getBoolean("enabled");
                    MutableUserSummary user = users.computeIfAbsent(
                            username,
                            ignored -> new MutableUserSummary(username, enabled)
                    );
                    String authority = resultSet.getString("authority");
                    if (authority != null && !authority.isBlank()) {
                        user.authorities().add(authority);
                    }
                }
        );
        return users.values().stream()
                .map(user -> new UserSummary(user.username(), user.enabled(), user.authorities()))
                .toList();
    }

    @Transactional
    public UserSummary createUser(String username, String rawPassword, List<String> roles, boolean enabled) {
        requireText(username, "Username is required");
        requireText(rawPassword, "Password is required");
        if (userDetailsManager.userExists(username)) {
            throw new IllegalArgumentException("User already exists: " + username);
        }
        UserDetails user = User.withUsername(username)
                .password(passwordEncoder.encode(rawPassword))
                .authorities(normalizeAuthorities(roles).toArray(String[]::new))
                .disabled(!enabled)
                .build();
        userDetailsManager.createUser(user);
        return findUser(username);
    }

    @Transactional
    public UserSummary ensureExternalUser(String username, List<String> roles, boolean enabled) {
        requireText(username, "Username is required");
        List<String> authorities = normalizeAuthorities(roles);
        if (!userDetailsManager.userExists(username)) {
            UserDetails user = User.withUsername(username)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .authorities(authorities.toArray(String[]::new))
                    .disabled(!enabled)
                    .build();
            userDetailsManager.createUser(user);
            return findUser(username);
        }

        UserDetails existing = userDetailsManager.loadUserByUsername(username);
        UserDetails updated = User.withUserDetails(existing)
                .authorities(authorities.toArray(String[]::new))
                .disabled(!enabled)
                .build();
        userDetailsManager.updateUser(updated);
        return findUser(username);
    }

    @Transactional
    public UserSummary setEnabled(String username, boolean enabled) {
        UserDetails existing = userDetailsManager.loadUserByUsername(username);
        UserDetails updated = User.withUserDetails(existing)
                .disabled(!enabled)
                .build();
        userDetailsManager.updateUser(updated);
        return findUser(username);
    }

    @Transactional
    public void setPassword(String username, String rawPassword) {
        requireText(rawPassword, "Password is required");
        UserDetails existing = userDetailsManager.loadUserByUsername(username);
        UserDetails updated = User.withUserDetails(existing)
                .password(passwordEncoder.encode(rawPassword))
                .build();
        userDetailsManager.updateUser(updated);
    }

    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        requireText(currentPassword, "Current password is required");
        requireText(newPassword, "New password is required");
        UserDetails existing = userDetailsManager.loadUserByUsername(username);
        if (!passwordEncoder.matches(currentPassword, existing.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        setPassword(username, newPassword);
    }

    private UserSummary findUser(String username) {
        UserDetails user = userDetailsManager.loadUserByUsername(username);
        return new UserSummary(
                user.getUsername(),
                user.isEnabled(),
                user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .sorted()
                        .toList()
        );
    }

    private List<String> normalizeAuthorities(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("ROLE_BUSINESS_USER");
        }
        return roles.stream()
                .map(String::strip)
                .filter(role -> !role.isBlank())
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private record MutableUserSummary(
            String username,
            boolean enabled,
            List<String> authorities
    ) {
        MutableUserSummary(String username, boolean enabled) {
            this(username, enabled, new java.util.ArrayList<>());
        }
    }
}
