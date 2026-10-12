package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.application.SubmittedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "aegisflow.security.enabled=true")
@AutoConfigureMockMvc
class SecurityRbacControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ReviewService reviewService;

    @Test
    void authenticationAndUserManagementUsePersistentUsersAndEncodedPasswords() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.authorities", hasItem("ROLE_ADMIN")));

        mockMvc.perform(post("/api/auth/users")
                        .with(httpBasic("admin", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "architect-sec-001",
                                  "password": "architect-pass-001",
                                  "roles": ["TECHNICAL_REVIEWER"],
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("architect-sec-001"))
                .andExpect(jsonPath("$.authorities", hasItem("ROLE_TECHNICAL_REVIEWER")));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("architect-sec-001", "architect-pass-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("architect-sec-001"));

        mockMvc.perform(put("/api/auth/me/password")
                        .with(httpBasic("architect-sec-001", "architect-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "architect-pass-001",
                                  "newPassword": "architect-pass-002"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("architect-sec-001", "architect-pass-002")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/auth/users/{username}/enabled", "architect-sec-001")
                        .with(httpBasic("admin", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/auth/me").with(httpBasic("architect-sec-001", "architect-pass-002")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reviewRbacUsesAuthenticatedAuthoritiesInsteadOfRequestHeaders() throws Exception {
        createUser("business-sec-001", "business-pass-001", "BUSINESS_USER");
        createUser("architect-rbac-001", "architect-pass-001", "TECHNICAL_REVIEWER");
        createUser("pm-rbac-001", "pm-pass-001", "PM_APPROVER");

        var project = projectService.submitBrd(
                "Security RBAC Technical Review",
                "Payments Business Team",
                "Payments",
                new SubmittedDocument("security-rbac.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();

        mockMvc.perform(post("/api/reviews/{reviewId}/comments", review.reviewId())
                        .with(httpBasic("business-sec-001", "business-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "spoofed-actor",
                                  "body": "Please confirm timeout ownership with @architect-rbac-001."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actor").value("business-sec-001"));

        mockMvc.perform(post("/api/reviews/{reviewId}/attachments", review.reviewId())
                        .with(httpBasic("business-sec-001", "business-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "business-sec-001",
                                  "fileName": "business-note.pdf",
                                  "mediaType": "application/pdf",
                                  "storageUri": "s3://aegisflow-review-evidence/business-note.pdf"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reviews/{reviewId}/attachments", review.reviewId())
                        .with(httpBasic("architect-rbac-001", "architect-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "spoofed-actor",
                                  "fileName": "timeout-analysis.pdf",
                                  "mediaType": "application/pdf",
                                  "storageUri": "s3://aegisflow-review-evidence/timeout-analysis.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actor").value("architect-rbac-001"));

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .with(httpBasic("business-sec-001", "business-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "actor": "business-sec-001"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .with(httpBasic("architect-rbac-001", "architect-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "actor": "spoofed-actor"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actor").value("architect-rbac-001"));

        var pmProject = projectService.submitBrd(
                "Security RBAC PM Review",
                "Operations Business Team",
                "Operations",
                new SubmittedDocument("security-rbac-pm.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var pmReview = reviewService.createPmReview(pmProject.projectId()).orElseThrow();

        mockMvc.perform(get("/api/reviews/{reviewId}/timeline", pmReview.reviewId())
                        .with(httpBasic("business-sec-001", "business-pass-001")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reviews/{reviewId}/attachments", pmReview.reviewId())
                        .with(httpBasic("pm-rbac-001", "pm-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "pm-rbac-001",
                                  "fileName": "capacity-plan.pdf",
                                  "mediaType": "application/pdf",
                                  "storageUri": "s3://aegisflow-review-evidence/capacity-plan.pdf"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private void createUser(String username, String password, String role) throws Exception {
        mockMvc.perform(post("/api/auth/users")
                        .with(httpBasic("admin", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "roles": ["%s"],
                                  "enabled": true
                                }
                                """.formatted(username, password, role)))
                .andExpect(status().isCreated());
    }
}
