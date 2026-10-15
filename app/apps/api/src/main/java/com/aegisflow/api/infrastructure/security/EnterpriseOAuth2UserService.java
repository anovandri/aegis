package com.aegisflow.api.infrastructure.security;

import com.aegisflow.api.application.identity.EnterpriseAuthenticatedIdentity;
import com.aegisflow.api.application.identity.EnterpriseIdentityException;
import com.aegisflow.api.application.identity.EnterpriseIdentityService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EnterpriseOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final EnterpriseIdentityService enterpriseIdentityService;

    public EnterpriseOAuth2UserService(EnterpriseIdentityService enterpriseIdentityService) {
        this.enterpriseIdentityService = enterpriseIdentityService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        String providerId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = user.getAttributes();
        String subject = firstAttribute(attributes, "sub", "id", "login");
        String email = firstAttribute(attributes, "email");
        String displayName = firstAttribute(attributes, "name", "login");
        try {
            EnterpriseAuthenticatedIdentity identity = enterpriseIdentityService.resolveExternalIdentity(
                    providerId,
                    subject,
                    email,
                    displayName,
                    attributes
            );
            List<SimpleGrantedAuthority> authorities = identity.authorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            Map<String, Object> mappedAttributes = new LinkedHashMap<>(attributes);
            mappedAttributes.put("aegisflow_username", identity.username());
            mappedAttributes.put("aegisflow_provider_id", identity.providerId());
            return new DefaultOAuth2User(authorities, mappedAttributes, "aegisflow_username");
        } catch (EnterpriseIdentityException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("aegisflow_identity_resolution_failed", exception.getMessage(), null),
                    exception
            );
        }
    }

    private String firstAttribute(Map<String, Object> attributes, String... names) {
        for (String name : names) {
            Object value = attributes.get(name);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
