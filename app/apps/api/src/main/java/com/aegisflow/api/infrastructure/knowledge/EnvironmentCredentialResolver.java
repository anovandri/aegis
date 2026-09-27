package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EnvironmentCredentialResolver implements CredentialResolverPort {
    private static final String ENV_PREFIX = "env://";

    private final Environment environment;

    public EnvironmentCredentialResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Optional.empty();
        }
        if (!credentialRef.startsWith(ENV_PREFIX)) {
            return Optional.empty();
        }
        String variableName = credentialRef.substring(ENV_PREFIX.length());
        return Optional.ofNullable(environment.getProperty(variableName))
                .filter(value -> !value.isBlank());
    }
}
