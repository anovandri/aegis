package com.aegisflow.api.infrastructure.temporal;

import com.aegisflow.api.workflow.ProjectLifecycleActivitiesImpl;
import com.aegisflow.api.workflow.ProjectLifecycleWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TemporalProperties.class)
@ConditionalOnProperty(prefix = "aegisflow.temporal", name = "enabled", havingValue = "true")
public class TemporalConfiguration {
    @Bean
    WorkflowServiceStubs workflowServiceStubs(TemporalProperties properties) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(properties.target())
                        .build()
        );
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs, TemporalProperties properties) {
        return WorkflowClient.newInstance(
                serviceStubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(properties.namespace())
                        .build()
        );
    }

    @Bean
    WorkerFactory workerFactory(
            WorkflowClient workflowClient,
            TemporalProperties properties,
            ProjectLifecycleActivitiesImpl activities
    ) {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(properties.taskQueue());
        worker.registerWorkflowImplementationTypes(ProjectLifecycleWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        return factory;
    }
}
