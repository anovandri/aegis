package com.aegisflow.api.application;

import java.util.List;

public record UserSummary(
        String username,
        boolean enabled,
        List<String> authorities
) {
    public UserSummary {
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }
}
