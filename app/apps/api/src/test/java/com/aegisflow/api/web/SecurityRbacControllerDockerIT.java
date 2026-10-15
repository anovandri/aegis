package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.application.identity.EnterpriseIdentityService;
import com.aegisflow.api.domain.WorkflowState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "aegisflow.security.enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class SecurityRbacControllerDockerIT {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("aegisflow")
            .withUsername("aegisflow")
            .withPassword("aegisflow-secret");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private EnterpriseIdentityService enterpriseIdentityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void dockerBackedProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/vendor/postgresql");
        registry.add("aegisflow.storage.provider", () -> "in-memory");
        registry.add("aegisflow.temporal.enabled", () -> "false");
        registry.add("aegisflow.llm.provider", () -> "local");
        registry.add("aegisflow.knowledge.embedding.provider", () -> "local");
        registry.add("aegisflow.security.enabled", () -> "true");
    }

    @Test
    void reviewQueueRbacUsesPostgresBackedUsersAuthoritiesAndPersistsOnlyAuthorizedActions() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.authorities", hasItem("ROLE_ADMIN")));

        assertUserAuthorityWasPersisted("admin", "ROLE_ADMIN");

        createUser("business-docker-rbac", "business-pass-001", "BUSINESS_USER");
        createUser("architect-docker-rbac", "architect-pass-001", "TECHNICAL_REVIEWER");
        createUser("pm-docker-rbac", "pm-pass-001", "PM_APPROVER");
        createUser("analyst-docker-rbac", "analyst-pass-001", "SYSTEM_ANALYST");

        var project = projectService.submitBrd(
                "Docker Security RBAC Technical Review",
                "Payments Business Team",
                "Payments",
                new SubmittedDocument("docker-security-rbac.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();

        mockMvc.perform(get("/api/reviews/{reviewId}/timeline", review.reviewId())
                        .with(httpBasic("business-docker-rbac", "business-pass-001")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reviews/{reviewId}/comments", review.reviewId())
                        .with(httpBasic("business-docker-rbac", "business-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "spoofed-business",
                                  "body": "Please confirm timeout ownership with @architect-docker-rbac."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actor").value("business-docker-rbac"));
        assertCommentActorWasPersisted(review.reviewId(), "business-docker-rbac");

        mockMvc.perform(post("/api/reviews/{reviewId}/attachments", review.reviewId())
                        .with(httpBasic("business-docker-rbac", "business-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "business-docker-rbac",
                                  "fileName": "business-note.pdf",
                                  "mediaType": "application/pdf",
                                  "storageUri": "s3://aegisflow-review-evidence/business-note.pdf"
                                }
                                """))
                .andExpect(status().isForbidden());
        assertAttachmentCount(review.reviewId(), "business-note.pdf", 0);

        mockMvc.perform(post("/api/reviews/{reviewId}/attachments", review.reviewId())
                        .with(httpBasic("analyst-docker-rbac", "analyst-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "spoofed-analyst",
                                  "fileName": "timeout-analysis.pdf",
                                  "mediaType": "application/pdf",
                                  "storageUri": "s3://aegisflow-review-evidence/timeout-analysis.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actor").value("analyst-docker-rbac"));
        assertAttachmentActorWasPersisted(review.reviewId(), "timeout-analysis.pdf", "analyst-docker-rbac");

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .with(httpBasic("business-docker-rbac", "business-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "actor": "business-docker-rbac"
                                }
                                """))
                .andExpect(status().isForbidden());
        assertDecisionCount(review.reviewId(), 0);
        assertProjectState(project.projectId(), WorkflowState.HUMAN_TECHNICAL_REVIEW);

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .with(httpBasic("architect-docker-rbac", "architect-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "actor": "spoofed-architect"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actor").value("architect-docker-rbac"))
                .andExpect(jsonPath("$.nextState").value("ESTIMATION"));
        assertDecisionActorWasPersisted(review.reviewId(), "architect-docker-rbac");
        assertProjectState(project.projectId(), WorkflowState.ESTIMATION);

        var pmProject = projectService.submitBrd(
                "Docker Security RBAC PM Review",
                "Operations Business Team",
                "Operations",
                new SubmittedDocument("docker-security-rbac-pm.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var pmReview = reviewService.createPmReview(pmProject.projectId()).orElseThrow();

        mockMvc.perform(get("/api/reviews/{reviewId}/timeline", pmReview.reviewId())
                        .with(httpBasic("business-docker-rbac", "business-pass-001")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", pmReview.reviewId())
                        .with(httpBasic("architect-docker-rbac", "architect-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "actor": "architect-docker-rbac"
                                }
                                """))
                .andExpect(status().isForbidden());
        assertDecisionCount(pmReview.reviewId(), 0);
        assertProjectState(pmProject.projectId(), WorkflowState.HUMAN_PM_APPROVAL);

        mockMvc.perform(post("/api/reviews/{reviewId}/attachments", pmReview.reviewId())
                        .with(httpBasic("pm-docker-rbac", "pm-pass-001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "spoofed-pm",
                                  "fileName": "capacity-plan.pdf",
                                  "mediaType": "application/pdf",
                                  "storageUri": "s3://aegisflow-review-evidence/capacity-plan.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actor").value("pm-docker-rbac"));
        assertAttachmentActorWasPersisted(pmReview.reviewId(), "capacity-plan.pdf", "pm-docker-rbac");
    }

    @Test
    void enterpriseIdentityResolutionPersistsProviderAuditAndInternalAuthoritiesInPostgres() throws Exception {
        mockMvc.perform(get("/api/auth/identity-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.providerId == 'google')].authorizationPath", hasItem("/oauth2/authorization/google")));

        jdbcTemplate.update(
                """
                        UPDATE identity_provider_configs
                        SET enabled = TRUE, allowed_email_domains = ?
                        WHERE provider_id = 'github'
                        """,
                "mnc.co.id"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO role_mapping_rules (
                            rule_id, provider_id, claim_name, claim_value, authority,
                            priority, enabled, created_at, updated_at
                        )
                        VALUES (?, 'github', 'organizations', 'aegis-platform',
                                'ROLE_SYSTEM_ANALYST', 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                UUID.randomUUID()
        );

        var identity = enterpriseIdentityService.resolveExternalIdentity(
                "github",
                "github-user-001",
                "analyst@mnc.co.id",
                "GitHub Analyst",
                Map.of("organizations", java.util.List.of("aegis-platform"))
        );

        assertThat(identity.username()).isEqualTo("analyst@mnc.co.id");
        assertPersistedCount(
                "SELECT COUNT(*) FROM external_identities WHERE provider_id = 'github' AND username = 'analyst@mnc.co.id'",
                1
        );
        assertPersistedCount(
                "SELECT COUNT(*) FROM authorities WHERE username = 'analyst@mnc.co.id' AND authority = 'ROLE_SYSTEM_ANALYST'",
                1
        );
        assertPersistedCount(
                "SELECT COUNT(*) FROM auth_audit_events WHERE provider_id = 'github' AND event_type = 'LOGIN_SUCCESS'",
                1
        );
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
        assertUserAuthorityWasPersisted(username, "ROLE_" + role);
    }

    private void assertUserAuthorityWasPersisted(String username, String authority) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authorities WHERE username = ? AND authority = ?",
                Integer.class,
                username,
                authority
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertCommentActorWasPersisted(UUID reviewId, String actor) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM human_review_comments WHERE review_id = ? AND actor = ?",
                Integer.class,
                reviewId,
                actor
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertAttachmentCount(UUID reviewId, String fileName, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM human_review_attachments WHERE review_id = ? AND file_name = ?",
                Integer.class,
                reviewId,
                fileName
        );
        assertThat(count).isEqualTo(expectedCount);
    }

    private void assertAttachmentActorWasPersisted(UUID reviewId, String fileName, String actor) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM human_review_attachments WHERE review_id = ? AND file_name = ? AND actor = ?",
                Integer.class,
                reviewId,
                fileName,
                actor
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertDecisionCount(UUID reviewId, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM human_review_decisions WHERE review_id = ?",
                Integer.class,
                reviewId
        );
        assertThat(count).isEqualTo(expectedCount);
    }

    private void assertDecisionActorWasPersisted(UUID reviewId, String actor) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM human_review_decisions WHERE review_id = ? AND actor = ?",
                Integer.class,
                reviewId,
                actor
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertProjectState(UUID projectId, WorkflowState expectedState) {
        String actualState = jdbcTemplate.queryForObject(
                "SELECT current_state FROM projects WHERE project_id = ?",
                String.class,
                projectId
        );
        assertThat(actualState).isEqualTo(expectedState.name());
    }

    private void assertPersistedCount(String sql, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        assertThat(count).isEqualTo(expectedCount);
    }
}
