package com.aegisflow.api.web;

import com.aegisflow.api.application.UserManagementService;
import com.aegisflow.api.application.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserManagementService userManagementService;

    public AuthController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping("/me")
    AuthenticatedUser me(Authentication authentication) {
        return new AuthenticatedUser(
                authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .sorted()
                        .toList()
        );
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    List<UserSummary> users() {
        return userManagementService.listUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    UserSummary createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            return userManagementService.createUser(
                    request.username(),
                    request.password(),
                    request.roles(),
                    request.enabled()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PatchMapping("/users/{username}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    UserSummary setEnabled(@PathVariable String username, @Valid @RequestBody SetUserEnabledRequest request) {
        return userManagementService.setEnabled(username, request.enabled());
    }

    @PutMapping("/users/{username}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void setPassword(@PathVariable String username, @Valid @RequestBody SetPasswordRequest request) {
        try {
            userManagementService.setPassword(username, request.password());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeOwnPassword(Authentication authentication, @Valid @RequestBody ChangeOwnPasswordRequest request) {
        try {
            userManagementService.changeOwnPassword(authentication.getName(), request.currentPassword(), request.newPassword());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    record AuthenticatedUser(
            String username,
            List<String> authorities
    ) {
    }

    record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotEmpty List<String> roles,
            boolean enabled
    ) {
        CreateUserRequest {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    record SetUserEnabledRequest(
            @NotNull Boolean enabled
    ) {
    }

    record SetPasswordRequest(
            @NotBlank String password
    ) {
    }

    record ChangeOwnPasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword
    ) {
    }
}
