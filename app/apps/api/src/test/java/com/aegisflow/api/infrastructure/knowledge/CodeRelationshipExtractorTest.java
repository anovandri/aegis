package com.aegisflow.api.infrastructure.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeRelationshipExtractorTest {
    private final CodeRelationshipExtractor extractor = new CodeRelationshipExtractor();

    @Test
    void infersTypeInterfaceAndInternalFunctionRelationshipsAcrossUnits() {
        CodeKnowledgeUnit repositoryInterface = unit(
                "repository.go",
                "type",
                "PaymentRepository",
                """
                        type PaymentRepository interface {
                            Save(reference string) error
                        }
                        """
        );
        CodeKnowledgeUnit serviceType = unit(
                "service.go",
                "type",
                "PaymentService",
                """
                        type PaymentService struct {
                            repository PaymentRepository
                        }
                        """
        );
        CodeKnowledgeUnit serviceMethod = unit(
                "service.go",
                "method",
                "PaymentService.CreateDynamicQr",
                """
                        func (service PaymentService) CreateDynamicQr(reference string) error {
                            normalized := normalizeReference(reference)
                            return service.repository.Save(normalized)
                        }
                        """
        );
        CodeKnowledgeUnit helperFunction = unit(
                "service.go",
                "function",
                "normalizeReference",
                """
                        func normalizeReference(reference string) string {
                            return "QRIS-" + reference
                        }
                        """
        );

        List<CodeRelationship> relationships = extractor.inferRelationships(List.of(
                repositoryInterface,
                serviceType,
                serviceMethod,
                helperFunction
        ));

        assertThat(relationships).contains(
                new CodeRelationship("service.go", "PaymentService", "DEPENDS_ON_TYPE", "repository.go", "PaymentRepository"),
                new CodeRelationship("service.go", "PaymentService.CreateDynamicQr", "DEPENDS_ON_TYPE", "service.go", "PaymentService"),
                new CodeRelationship("service.go", "PaymentService.CreateDynamicQr", "CALLS_INTERFACE_METHOD", "repository.go", "PaymentRepository.Save"),
                new CodeRelationship("service.go", "PaymentService.CreateDynamicQr", "CALLS_FUNCTION", "service.go", "normalizeReference")
        );
    }

    private CodeKnowledgeUnit unit(String fileName, String symbolType, String symbolName, String text) {
        return new CodeKnowledgeUnit(
                "go",
                fileName,
                symbolType,
                symbolName,
                1,
                text.split("\\R").length,
                0,
                text.length(),
                text,
                Map.of()
        );
    }
}
