package com.aegisflow.api.infrastructure.security;

import com.aegisflow.api.application.identity.EnterpriseAuthenticatedIdentity;
import com.aegisflow.api.application.identity.EnterpriseIdentityException;
import com.aegisflow.api.application.identity.EnterpriseIdentityService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EnterpriseOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService delegate = new OidcUserService();
    private final EnterpriseIdentityService enterpriseIdentityService;

    public EnterpriseOidcUserService(EnterpriseIdentityService enterpriseIdentityService) {
        this.enterpriseIdentityService = enterpriseIdentityService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(userRequest);
        String providerId = userRequest.getClientRegistration().getRegistrationId();
        try {
            EnterpriseAuthenticatedIdentity identity = enterpriseIdentityService.resolveExternalIdentity(
                    providerId,
                    user.getSubject(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getClaims()
            );
            List<SimpleGrantedAuthority> authorities = identity.authorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            Map<String, Object> mappedUserInfoClaims = new LinkedHashMap<>(user.getUserInfo().getClaims());
            mappedUserInfoClaims.put("aegisflow_username", identity.username());
            mappedUserInfoClaims.put("aegisflow_provider_id", identity.providerId());
            return new DefaultOidcUser(
                    authorities,
                    user.getIdToken(),
                    new org.springframework.security.oauth2.core.oidc.OidcUserInfo(mappedUserInfoClaims),
                    "email"
            );
        } catch (EnterpriseIdentityException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("aegisflow_identity_resolution_failed", exception.getMessage(), null),
                    exception
            );
        }
    }
}
