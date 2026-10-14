package com.aegisflow.api.application.projectroom;

import com.aegisflow.api.application.ProjectLifecycleStarter;
import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.HumanReview;
import com.aegisflow.api.domain.HumanReviewAttachment;
import com.aegisflow.api.domain.HumanReviewDecision;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.ports.DocumentStoragePort;
import com.aegisflow.api.ports.StoredDocument;
import com.aegisflow.api.workflow.ReviewDecisionSignal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectRoomCommandService {
    private final ProjectService projectService;
    private final ReviewService reviewService;
    private final ProjectLifecycleStarter projectLifecycleStarter;
    private final DocumentStoragePort documentStoragePort;

    public ProjectRoomCommandService(
            ProjectService projectService,
            ReviewService reviewService,
            ProjectLifecycleStarter projectLifecycleStarter,
            DocumentStoragePort documentStoragePort
    ) {
        this.projectService = projectService;
        this.reviewService = reviewService;
        this.projectLifecycleStarter = projectLifecycleStarter;
        this.documentStoragePort = documentStoragePort;
    }

    @Transactional
    public Optional<HumanReviewDecision> decideTechnicalReview(
            UUID projectId,
            HumanDecision decision,
            String actor,
            String correction,
            List<String> clarificationQuestions,
            String rejectionReason
    ) {
        return activeReview(projectId, ReviewType.TECHNICAL_REVIEW)
                .map(review -> {
                    HumanReviewDecision reviewDecision = reviewService.decide(
                                    review.reviewId(),
                                    decision,
                                    actor,
                                    correction,
                                    clarificationQuestions,
                                    rejectionReason
                            )
                            .orElseThrow(() -> new IllegalStateException("Review not found: " + review.reviewId()));
                    projectLifecycleStarter.signalReviewDecision(
                            projectId,
                            new ReviewDecisionSignal(
                                    reviewDecision.projectId(),
                                    reviewDecision.reviewId(),
                                    review.reviewType(),
                                    reviewDecision.decision(),
                                    reviewDecision.nextState(),
                                    reviewDecision.actor()
                            )
                    );
                    return reviewDecision;
                });
    }

    @Transactional
    public Optional<HumanReviewAttachment> uploadReviewAttachment(
            UUID projectId,
            UUID reviewId,
            String actor,
            SubmittedDocument file,
            String description
    ) {
        return reviewService.findReview(reviewId)
                .filter(review -> review.projectId().equals(projectId))
                .flatMap(review -> {
                    String objectKey = "projects/%s/reviews/%s/attachments/%s".formatted(
                            projectId,
                            reviewId,
                            sanitizeFileName(file.fileName())
                    );
                    StoredDocument storedDocument = documentStoragePort.store(objectKey, file);
                    return reviewService.addAttachment(
                            reviewId,
                            actor,
                            file.fileName(),
                            file.mediaType(),
                            storageUri(storedDocument),
                            description
                    );
                });
    }

    @Transactional
    public Optional<ArtifactVersion> uploadRevisedBrd(UUID projectId, SubmittedDocument brdDocument, boolean restartWorkflow) {
        Optional<ArtifactVersion> artifact = projectService.submitBrdVersion(projectId, brdDocument);
        artifact.ifPresent(value -> {
            projectService.transitionProject(projectId, WorkflowState.BRD_SUBMITTED);
            if (restartWorkflow) {
                projectLifecycleStarter.start(projectId);
            }
        });
        return artifact;
    }

    private Optional<HumanReview> activeReview(UUID projectId, ReviewType reviewType) {
        return reviewService.listReviews(Optional.empty(), Optional.of(reviewType), Optional.empty()).stream()
                .filter(review -> review.projectId().equals(projectId))
                .filter(review -> review.status() != ReviewStatus.DECIDED)
                .findFirst();
    }

    private String storageUri(StoredDocument storedDocument) {
        String version = storedDocument.storageVersionId() == null ? "" : "?versionId=" + storedDocument.storageVersionId();
        return "aegisflow-storage://%s/%s%s".formatted(storedDocument.bucket(), storedDocument.objectKey(), version);
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
