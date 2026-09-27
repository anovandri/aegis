package com.aegisflow.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void submitBrdCreatesProjectInSubmittedState() throws Exception {
        MockMultipartFile brdFile = new MockMultipartFile(
                "brdFile",
                "dynamic-qris-brd-v1.md",
                MediaType.TEXT_PLAIN_VALUE,
                "Enable dynamic QR payment with reversal and reconciliation requirements.".getBytes()
        );

        mockMvc.perform(multipart("/api/projects")
                        .file(brdFile)
                        .param("name", "Dynamic QRIS Payment")
                        .param("businessOwner", "Payments Business Team")
                        .param("domain", "Merchant Payments"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dynamic QRIS Payment"))
                .andExpect(jsonPath("$.currentState").value("BRD_SUBMITTED"));
    }

    @Test
    void projectContextIncludesBrdArtifactVersion() throws Exception {
        MockMultipartFile brdFile = new MockMultipartFile(
                "brdFile",
                "dynamic-qris-brd-v1.md",
                MediaType.TEXT_PLAIN_VALUE,
                "Initial BRD".getBytes()
        );

        String response = mockMvc.perform(multipart("/api/projects")
                        .file(brdFile)
                        .param("name", "Dynamic QRIS Payment")
                        .param("businessOwner", "Payments Business Team")
                        .param("domain", "Merchant Payments"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String projectId = response.replaceAll(".*\\\"projectId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/projects/{projectId}/context", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("BRD_SUBMITTED"))
                .andExpect(jsonPath("$.artifacts", hasSize(1)))
                .andExpect(jsonPath("$.artifacts[0].artifactType").value("BRD"))
                .andExpect(jsonPath("$.artifacts[0].version").value(1))
                .andExpect(jsonPath("$.artifacts[0].fileName").value("dynamic-qris-brd-v1.md"))
                .andExpect(jsonPath("$.artifacts[0].storageBucket").value("aegisflow-documents"))
                .andExpect(jsonPath("$.artifacts[0].storageObjectKey").exists())
                .andExpect(jsonPath("$.retrievalPolicyId").value("mvp-default-policy"));
    }

    @Test
    void submitBrdVersionAppendsImmutableArtifactVersion() throws Exception {
        MockMultipartFile initialBrd = new MockMultipartFile(
                "brdFile",
                "dynamic-qris-brd-v1.md",
                MediaType.TEXT_PLAIN_VALUE,
                "Initial BRD".getBytes()
        );
        String response = mockMvc.perform(multipart("/api/projects")
                        .file(initialBrd)
                        .param("name", "Dynamic QRIS Payment")
                        .param("businessOwner", "Payments Business Team")
                        .param("domain", "Merchant Payments"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String projectId = response.replaceAll(".*\\\"projectId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        MockMultipartFile revisedBrd = new MockMultipartFile(
                "brdFile",
                "dynamic-qris-brd-v2.md",
                MediaType.TEXT_PLAIN_VALUE,
                "Revised BRD".getBytes()
        );

        mockMvc.perform(multipart("/api/projects/{projectId}/brd-versions", projectId)
                        .file(revisedBrd))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artifactType").value("BRD"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.fileName").value("dynamic-qris-brd-v2.md"))
                .andExpect(jsonPath("$.storageBucket").value("aegisflow-documents"))
                .andExpect(jsonPath("$.storageObjectKey").exists());

        mockMvc.perform(get("/api/projects/{projectId}/context", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts", hasSize(2)))
                .andExpect(jsonPath("$.artifacts[0].version").value(1))
                .andExpect(jsonPath("$.artifacts[1].version").value(2));
    }

    @Test
    void workflowProgressEndpointReturnsCurrentLifecycleProgress() throws Exception {
        MockMultipartFile initialBrd = new MockMultipartFile(
                "brdFile",
                "dynamic-qris-brd-v1.md",
                MediaType.TEXT_PLAIN_VALUE,
                "Initial BRD".getBytes()
        );
        String response = mockMvc.perform(multipart("/api/projects")
                        .file(initialBrd)
                        .param("name", "Dynamic QRIS Payment")
                        .param("businessOwner", "Payments Business Team")
                        .param("domain", "Merchant Payments"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String projectId = response.replaceAll(".*\\\"projectId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/projects/{projectId}/workflow", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.workflowId").value("temporal-disabled-" + projectId))
                .andExpect(jsonPath("$.currentState").value("BRD_SUBMITTED"))
                .andExpect(jsonPath("$.status").value("TEMPORAL_DISABLED"))
                .andExpect(jsonPath("$.pendingSteps", hasSize(9)));
    }
}
