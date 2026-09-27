package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;

public class ConfiguredKnowledgeSourceAdapter implements KnowledgeSourceAdapter {
    private final KnowledgeAdapterDescriptor descriptor;

    public ConfiguredKnowledgeSourceAdapter(KnowledgeAdapterDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public KnowledgeAdapterDescriptor descriptor() {
        return descriptor;
    }

    public static ConfiguredKnowledgeSourceAdapter manualUpload() {
        return new ConfiguredKnowledgeSourceAdapter(new KnowledgeAdapterDescriptor(
                "MANUAL_UPLOAD",
                "Manual Upload",
                "User-uploaded files stored in AegisFlow document storage.",
                List.of("NONE"),
                List.of("DOCUMENT"),
                List.of(),
                true
        ));
    }

    public static ConfiguredKnowledgeSourceAdapter googleDrive() {
        return new ConfiguredKnowledgeSourceAdapter(new KnowledgeAdapterDescriptor(
                "GOOGLE_DRIVE",
                "Google Drive",
                "Read-only pull from a governed Drive folder or shared drive.",
                List.of("OAUTH", "SERVICE_ACCOUNT"),
                List.of("DOCUMENT", "SPREADSHEET", "PDF"),
                List.of("folderId"),
                true
        ));
    }

    public static ConfiguredKnowledgeSourceAdapter jira() {
        return new ConfiguredKnowledgeSourceAdapter(new KnowledgeAdapterDescriptor(
                "JIRA",
                "Jira",
                "Read-only pull from Jira projects, JQL filters, epics, and historical delivery records.",
                List.of("OAUTH", "PAT"),
                List.of("ISSUE", "EPIC", "STORY", "TASK"),
                List.of("baseUrl", "jql"),
                false
        ));
    }

    public static ConfiguredKnowledgeSourceAdapter gitLab() {
        return new ConfiguredKnowledgeSourceAdapter(new KnowledgeAdapterDescriptor(
                "GITLAB",
                "GitLab",
                "Read-only pull from repositories, documentation paths, merge requests, and code ownership files.",
                List.of("OAUTH", "PAT"),
                List.of("REPOSITORY_FILE", "MERGE_REQUEST", "README"),
                List.of("baseUrl", "projectPath"),
                true
        ));
    }
}
