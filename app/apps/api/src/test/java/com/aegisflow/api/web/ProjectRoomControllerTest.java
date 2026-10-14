package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.WorkflowState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectRoomControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ReviewService reviewService;

    @Test
    void projectRoomTechnicalReviewDecisionResumesProjectThroughReviewWorkflow() throws Exception {
        var project = projectService.submitBrd(
                "Project Room Decision",
                "Payments Business Team",
                "Payments",
                document("decision-brd.md", "BRD")
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();

        mockMvc.perform(post("/api/project-rooms/{projectId}/technical-review/decision", project.projectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE_WITH_CHANGE",
                                  "actor": "architect-001",
                                  "correction": "Reuse the payment orchestration capability and add explicit issuer timeout handling."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(review.reviewId().toString()))
                .andExpect(jsonPath("$.decision").value("APPROVE_WITH_CHANGE"))
                .andExpect(jsonPath("$.nextState").value("ESTIMATION"))
                .andExpect(jsonPath("$.generatedArtifactId").exists());

        mockMvc.perform(get("/api/projects/{projectId}", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("ESTIMATION"));
    }

    @Test
    void projectRoomUploadsReviewAttachmentToStorageAndRegistersEvidence() throws Exception {
        var project = projectService.submitBrd(
                "Project Room Evidence",
                "Architecture Team",
                "Payments",
                document("evidence-brd.md", "BRD")
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "timeout-analysis.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "timeout evidence".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/project-rooms/{projectId}/reviews/{reviewId}/attachments", project.projectId(), review.reviewId())
                        .file(file)
                        .param("actor", "architect-001")
                        .param("description", "Issuer timeout reference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(review.reviewId().toString()))
                .andExpect(jsonPath("$.fileName").value("timeout-analysis.pdf"))
                .andExpect(jsonPath("$.storageUri", startsWith("aegisflow-storage://")));
    }

    @Test
    void projectRoomUploadsRevisedBrdAndMarksProjectForReanalysis() throws Exception {
        var project = projectService.submitBrd(
                "Project Room BRD Revision",
                "Business Team",
                "Payments",
                document("revision-brd-v1.md", "BRD v1")
        );
        MockMultipartFile file = new MockMultipartFile(
                "brdFile",
                "revision-brd-v2.md",
                MediaType.TEXT_MARKDOWN_VALUE,
                "BRD v2".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/project-rooms/{projectId}/brd-versions", project.projectId())
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactType").value("BRD"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/projects/{projectId}", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value(WorkflowState.BRD_SUBMITTED.name()));
    }

    @Test
    void projectRoomExposesArtifactVersionDetailAndDownloadableContent() throws Exception {
        var project = projectService.submitBrd(
                "Project Room Artifact Detail",
                "Business Team",
                "Payments",
                document("artifact-detail-brd.md", "BRD")
        );
        var artifact = projectService.addGeneratedArtifact(
                project.projectId(),
                ArtifactType.ARCHITECTURE_ANALYSIS,
                "architecture-v1.md",
                MediaType.TEXT_MARKDOWN_VALUE,
                "# Architecture\\nUse existing payment service.".getBytes(StandardCharsets.UTF_8)
        ).orElseThrow();

        mockMvc.perform(get(
                        "/api/project-rooms/{projectId}/artifacts/{artifactId}/versions/{version}",
                        project.projectId(),
                        artifact.artifactId(),
                        artifact.version()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifact.artifactId").value(artifact.artifactId().toString()))
                .andExpect(jsonPath("$.artifact.artifactType").value("ARCHITECTURE_ANALYSIS"))
                .andExpect(jsonPath("$.downloadable").value(true))
                .andExpect(jsonPath("$.downloadPath").exists());

        mockMvc.perform(get(
                        "/api/project-rooms/{projectId}/artifacts/{artifactId}/versions/{version}/content",
                        project.projectId(),
                        artifact.artifactId(),
                        artifact.version()
                ))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"architecture-v1.md\""))
                .andExpect(content().string("# Architecture\\nUse existing payment service."));
    }

    private SubmittedDocument document(String fileName, String content) {
        return new SubmittedDocument(
                fileName,
                MediaType.TEXT_MARKDOWN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
