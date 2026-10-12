package com.aegisflow.api.application;

import com.aegisflow.api.domain.ReviewType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("reviewAuthorization")
public class ReviewAuthorizationService {
    private final ReviewService reviewService;

    public ReviewAuthorizationService(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    public boolean canViewReviews(Authentication authentication) {
        return isAuthenticated(authentication);
    }

    public boolean canAssign(Authentication authentication) {
        return hasAnyRole(authentication, "ADMIN", "TECHNICAL_REVIEWER", "PM_APPROVER");
    }

    public boolean canDecide(Authentication authentication, UUID reviewId) {
        return reviewService.findReview(reviewId)
                .map(review -> canDecide(authentication, review.reviewType()))
                .orElse(true);
    }

    public boolean canParticipate(Authentication authentication, UUID reviewId) {
        return reviewService.findReview(reviewId)
                .map(review -> canParticipate(authentication, review.reviewType()))
                .orElse(true);
    }

    public boolean canAttach(Authentication authentication, UUID reviewId) {
        return reviewService.findReview(reviewId)
                .map(review -> canAttach(authentication, review.reviewType()))
                .orElse(true);
    }

    private boolean canDecide(Authentication authentication, ReviewType reviewType) {
        if (hasRole(authentication, "ADMIN")) {
            return true;
        }
        return switch (reviewType) {
            case TECHNICAL_REVIEW -> hasRole(authentication, "TECHNICAL_REVIEWER");
            case PM_APPROVAL -> hasRole(authentication, "PM_APPROVER");
            case CLARIFICATION -> hasAnyRole(authentication, "BUSINESS_USER", "TECHNICAL_REVIEWER", "PM_APPROVER");
        };
    }

    private boolean canParticipate(Authentication authentication, ReviewType reviewType) {
        if (hasRole(authentication, "ADMIN")) {
            return true;
        }
        return switch (reviewType) {
            case TECHNICAL_REVIEW -> hasAnyRole(authentication, "TECHNICAL_REVIEWER", "SYSTEM_ANALYST", "PM_APPROVER", "BUSINESS_USER");
            case PM_APPROVAL -> hasAnyRole(authentication, "PM_APPROVER", "TECHNICAL_REVIEWER", "SYSTEM_ANALYST");
            case CLARIFICATION -> hasAnyRole(authentication, "BUSINESS_USER", "TECHNICAL_REVIEWER", "SYSTEM_ANALYST", "PM_APPROVER");
        };
    }

    private boolean canAttach(Authentication authentication, ReviewType reviewType) {
        if (hasRole(authentication, "ADMIN")) {
            return true;
        }
        return switch (reviewType) {
            case TECHNICAL_REVIEW -> hasAnyRole(authentication, "TECHNICAL_REVIEWER", "SYSTEM_ANALYST");
            case PM_APPROVAL -> hasRole(authentication, "PM_APPROVER");
            case CLARIFICATION -> hasAnyRole(authentication, "BUSINESS_USER", "TECHNICAL_REVIEWER", "PM_APPROVER", "SYSTEM_ANALYST");
        };
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authorities(authentication).contains("ROLE_" + role);
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        Set<String> authorities = authorities(authentication);
        return java.util.Arrays.stream(roles)
                .map(role -> "ROLE_" + role)
                .anyMatch(authorities::contains);
    }

    private Set<String> authorities(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
