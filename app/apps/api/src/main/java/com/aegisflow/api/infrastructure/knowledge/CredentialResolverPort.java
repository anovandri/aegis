package com.aegisflow.api.infrastructure.knowledge;

import java.util.Optional;

public interface CredentialResolverPort {
    Optional<String> resolve(String credentialRef);
}
