package com.aegisflow.api.workflow;

import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.agent.RequirementAnalysis;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProjectLifecycleActivitiesImplTest {
    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectLifecycleActivitiesImpl activities;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void brdIntakeRetrievesDocumentAnalyzesCompletenessAndPersistsAnalysisArtifact() {
        var project = projectService.submitBrd(
                "Dynamic QRIS Payment",
                "Payments Business Team",
                "Merchant Payments",
                new SubmittedDocument(
                        "dynamic-qris-brd-v1.md",
                        "text/markdown",
                        """
                                # Dynamic QRIS Payment

                                Objective: allow merchants to accept dynamic QRIS payments.
                                Actors: merchant, customer, payment switch.
                                The system must generate a dynamic QR payload.
                                SLA: payment confirmation should return within 3 seconds.
                                Timeout and reversal handling are required.
                                Audit logs are required for compliance.
                                """
                                .getBytes(StandardCharsets.UTF_8)
                )
        );

        BrdIntakeActivityResult result = activities.runBrdIntake(project.projectId());

        assertThat(result.nextState()).isEqualTo(WorkflowState.REQUIREMENT_ANALYSIS);
        assertThat(result.brdVersion()).isEqualTo(1);
        assertThat(result.analysisVersion()).isEqualTo(1);
        assertThat(result.completenessScore()).isGreaterThan(0.8);

        var context = projectService.assembleContext(project.projectId()).orElseThrow();
        assertThat(context.currentState()).isEqualTo(WorkflowState.REQUIREMENT_ANALYSIS);
        assertThat(context.artifacts())
                .anySatisfy(artifact -> {
                    assertThat(artifact.artifactType()).isEqualTo(ArtifactType.BRD_ANALYSIS);
                    assertThat(artifact.fileName()).isEqualTo("brd-analysis-v1.json");
                    assertThat(artifact.mediaType()).isEqualTo("application/json");
                });
    }

    @Test
    void requirementAnalysisConsumesBrdAndBrdAnalysisThenPersistsRequirementArtifact() throws Exception {
        var project = projectService.submitBrd(
                "Dynamic QRIS Payment",
                "Payments Business Team",
                "Merchant Payments",
                new SubmittedDocument(
                        "dynamic-qris-brd-v1.md",
                        "text/markdown",
                        """
                                # Dynamic QRIS Payment

                                Objective: allow merchants to accept dynamic QRIS payments.
                                Actors: merchant, customer, payment switch.
                                The system must generate a dynamic QR payload.
                                SLA: payment confirmation should return within 3 seconds.
                                Timeout and reversal handling are required.
                                Audit logs are required for compliance.
                                """
                                .getBytes(StandardCharsets.UTF_8)
                )
        );
        activities.runBrdIntake(project.projectId());

        RequirementAnalysisActivityResult result = activities.runRequirementAnalysis(project.projectId());

        assertThat(result.nextState()).isEqualTo(WorkflowState.ARCHITECTURE_ANALYSIS);
        assertThat(result.analysisVersion()).isEqualTo(1);
        assertThat(result.readinessScore()).isGreaterThan(0.3);

        var context = projectService.assembleContext(project.projectId()).orElseThrow();
        assertThat(context.currentState()).isEqualTo(WorkflowState.ARCHITECTURE_ANALYSIS);
        assertThat(context.artifacts())
                .anySatisfy(artifact -> {
                    assertThat(artifact.artifactType()).isEqualTo(ArtifactType.REQUIREMENT_ANALYSIS);
                    assertThat(artifact.fileName()).isEqualTo("requirement-analysis-v1.json");
                    assertThat(artifact.mediaType()).isEqualTo("application/json");
                });

        var requirementArtifact = projectService.latestArtifact(project.projectId(), ArtifactType.REQUIREMENT_ANALYSIS).orElseThrow();
        RequirementAnalysis persistedAnalysis = objectMapper.readValue(
                projectService.retrieveArtifactContent(requirementArtifact),
                RequirementAnalysis.class
        );
        assertThat(persistedAnalysis.sourceRefs())
                .contains("previous-project-qris-static-to-dynamic", "standard-payment-nfr-checklist");
    }
}
