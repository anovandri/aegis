package com.aegisflow.api.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

@ActivityInterface
public interface ProjectLifecycleActivities {
    @ActivityMethod
    BrdIntakeActivityResult runBrdIntake(UUID projectId);

    @ActivityMethod
    RequirementAnalysisActivityResult runRequirementAnalysis(UUID projectId);

    @ActivityMethod
    void runArchitectureAnalysis(UUID projectId);

    @ActivityMethod
    void runSystemAnalysis(UUID projectId);

    @ActivityMethod
    void createTechnicalReview(UUID projectId);

    @ActivityMethod
    void runEstimation(UUID projectId);

    @ActivityMethod
    void runJiraPlanning(UUID projectId);

    @ActivityMethod
    void createPmReview(UUID projectId);

    @ActivityMethod
    void publishJira(UUID projectId);
}
