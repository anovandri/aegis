package com.aegisflow.api.infrastructure.knowledge;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterGoCodeKnowledgeParserTest {
    private final TreeSitterGoCodeKnowledgeParser parser = new TreeSitterGoCodeKnowledgeParser();
    private final CodeRelationshipExtractor relationshipExtractor = new CodeRelationshipExtractor();

    @Test
    void parsesGoFunctionsMethodsAndTypesIntoCodeKnowledgeUnits() {
        String source = """
                package payment

                type Service struct {
                    repository Repository
                }

                func NewService(repository Repository) Service {
                    return Service{repository: repository}
                }

                func (service Service) CreateDynamicQr(request Request) Response {
                    return Response{}
                }
                """;

        List<CodeKnowledgeUnit> units = parser.parse("service.go", "text/plain", source);

        assertThat(units)
                .extracting(CodeKnowledgeUnit::symbolName)
                .contains("Service", "NewService", "CreateDynamicQr");
        assertThat(units)
                .extracting(CodeKnowledgeUnit::symbolType)
                .contains("type", "function", "method");
        assertThat(units.getFirst().toKnowledgeText())
                .contains("Language: go")
                .contains("File: service.go")
                .contains("Lines:");
    }

    @Test
    void parsesRicherGoServiceFileIntoLlmFriendlySymbolChunks() {
        String source = """
                package qris

                import (
                    "context"
                    "errors"
                    "fmt"
                    "time"
                )

                type Repository interface {
                    Save(ctx context.Context, payment Payment) error
                    FindPending(ctx context.Context, merchantID string) ([]Payment, error)
                }

                type EventPublisher interface {
                    Publish(ctx context.Context, topic string, payload any) error
                }

                type Payment struct {
                    ID         string
                    MerchantID string
                    Amount     int64
                    ExpiresAt  time.Time
                }

                type Service struct {
                    repository Repository
                    publisher  EventPublisher
                    clock      func() time.Time
                }

                func NewService(repository Repository, publisher EventPublisher, clock func() time.Time) *Service {
                    if clock == nil {
                        clock = time.Now
                    }
                    return &Service{
                        repository: repository,
                        publisher:  publisher,
                        clock:      clock,
                    }
                }

                func (service *Service) CreateDynamicQr(ctx context.Context, merchantID string, amount int64) (Payment, error) {
                    if merchantID == "" {
                        return Payment{}, errors.New("merchant id is required")
                    }
                    payment := Payment{
                        ID:         newPaymentID(merchantID),
                        MerchantID: merchantID,
                        Amount:     amount,
                        ExpiresAt:  service.clock().Add(15 * time.Minute),
                    }
                    if err := service.repository.Save(ctx, payment); err != nil {
                        return Payment{}, fmt.Errorf("save payment: %w", err)
                    }
                    go service.publisher.Publish(ctx, "payment.created", payment)
                    return payment, nil
                }

                func (service *Service) ReconcilePending(ctx context.Context, merchantID string) error {
                    payments, err := service.repository.FindPending(ctx, merchantID)
                    if err != nil {
                        return err
                    }
                    for _, payment := range payments {
                        if payment.ExpiresAt.Before(service.clock()) {
                            if publishErr := service.publisher.Publish(ctx, "payment.expired", payment); publishErr != nil {
                                return publishErr
                            }
                        }
                    }
                    return nil
                }

                func newPaymentID(merchantID string) string {
                    return fmt.Sprintf("%s-%d", merchantID, time.Now().UnixNano())
                }
                """;

        List<CodeKnowledgeUnit> units = parser.parse("qris/service.go", "text/plain", source);

        assertThat(units)
                .extracting(CodeKnowledgeUnit::symbolName)
                .contains(
                        "Repository",
                        "EventPublisher",
                        "Payment",
                        "Service",
                        "NewService",
                        "CreateDynamicQr",
                        "ReconcilePending",
                        "newPaymentID"
                );
        assertThat(units)
                .filteredOn(unit -> unit.symbolName().equals("CreateDynamicQr"))
                .singleElement()
                .satisfies(unit -> {
                    assertThat(unit.symbolType()).isEqualTo("method");
                    assertThat(unit.text()).contains("repository.Save", "publisher.Publish", "go service.publisher.Publish");
                    assertThat(unit.toKnowledgeText())
                            .contains("Language: go")
                            .contains("File: qris/service.go")
                            .contains("Symbol Type: method")
                            .contains("Symbol Name: CreateDynamicQr")
                            .contains("Lines:");
                });
        assertThat(units)
                .filteredOn(unit -> unit.symbolName().equals("Repository"))
                .singleElement()
                .satisfies(unit -> {
                    assertThat(unit.symbolType()).isEqualTo("type");
                    assertThat(unit.text()).contains("Save(ctx context.Context", "FindPending(ctx context.Context");
                });
    }

    @Test
    void demonstratesCrossFileRelationshipInferenceFromParsedGoUnits() {
        String repositorySource = """
                package qris

                import "context"

                type Repository interface {
                    Save(ctx context.Context, payment Payment) error
                    FindByID(ctx context.Context, id string) (Payment, error)
                }
                """;
        String serviceSource = """
                package qris

                import "context"

                type Service struct {
                    repository Repository
                }

                func NewService(repository Repository) *Service {
                    return &Service{repository: repository}
                }

                func (service *Service) CreateDynamicQr(ctx context.Context, payment Payment) error {
                    return service.repository.Save(ctx, payment)
                }
                """;

        List<CodeKnowledgeUnit> repositoryUnits = parser.parse("qris/repository.go", "text/plain", repositorySource);
        List<CodeKnowledgeUnit> serviceUnits = parser.parse("qris/service.go", "text/plain", serviceSource);

        List<CodeKnowledgeUnit> allUnits = new ArrayList<>();
        allUnits.addAll(repositoryUnits);
        allUnits.addAll(serviceUnits);
        List<CodeRelationship> relationships = relationshipExtractor.inferRelationships(allUnits);

        assertThat(repositoryUnits)
                .filteredOn(unit -> unit.symbolName().equals("Repository"))
                .singleElement()
                .satisfies(unit -> assertThat(unit.text()).contains("Save(ctx context.Context"));
        assertThat(serviceUnits)
                .extracting(CodeKnowledgeUnit::symbolName)
                .contains("Service", "NewService", "CreateDynamicQr");
        assertThat(relationships)
                .contains(
                        new CodeRelationship(
                                "qris/service.go",
                                "Service",
                                "DEPENDS_ON_TYPE",
                                "qris/repository.go",
                                "Repository"
                        ),
                        new CodeRelationship(
                                "qris/service.go",
                                "CreateDynamicQr",
                                "CALLS_INTERFACE_METHOD",
                                "qris/repository.go",
                                "Repository.Save"
                        )
                );
    }

    @Test
    void demonstratesThreeFileRelationshipInferenceIncludingInternalFunctionCall() {
        String repositorySource = """
                package qris

                import "context"

                type Repository interface {
                    Save(ctx context.Context, payment Payment) error
                }
                """;
        String publisherSource = """
                package qris

                import "context"

                type EventPublisher interface {
                    Publish(ctx context.Context, topic string, payload any) error
                }
                """;
        String serviceSource = """
                package qris

                import "context"

                type Service struct {
                    repository Repository
                    publisher  EventPublisher
                }

                func (service *Service) CreateDynamicQr(ctx context.Context, payment Payment) error {
                    normalized := normalizePayment(payment)
                    if err := service.repository.Save(ctx, normalized); err != nil {
                        return err
                    }
                    return service.publisher.Publish(ctx, "payment.created", normalized)
                }

                func normalizePayment(payment Payment) Payment {
                    if payment.ID == "" {
                        payment.ID = "generated-id"
                    }
                    return payment
                }
                """;

        List<CodeKnowledgeUnit> repositoryUnits = parser.parse("qris/repository.go", "text/plain", repositorySource);
        List<CodeKnowledgeUnit> publisherUnits = parser.parse("qris/publisher.go", "text/plain", publisherSource);
        List<CodeKnowledgeUnit> serviceUnits = parser.parse("qris/service.go", "text/plain", serviceSource);
        List<CodeKnowledgeUnit> allUnits = new ArrayList<>();
        allUnits.addAll(repositoryUnits);
        allUnits.addAll(publisherUnits);
        allUnits.addAll(serviceUnits);

        List<CodeRelationship> relationships = relationshipExtractor.inferRelationships(allUnits);

        assertThat(relationships)
                .contains(
                        new CodeRelationship(
                                "qris/service.go",
                                "Service",
                                "DEPENDS_ON_TYPE",
                                "qris/repository.go",
                                "Repository"
                        ),
                        new CodeRelationship(
                                "qris/service.go",
                                "Service",
                                "DEPENDS_ON_TYPE",
                                "qris/publisher.go",
                                "EventPublisher"
                        ),
                        new CodeRelationship(
                                "qris/service.go",
                                "CreateDynamicQr",
                                "CALLS_INTERFACE_METHOD",
                                "qris/repository.go",
                                "Repository.Save"
                        ),
                        new CodeRelationship(
                                "qris/service.go",
                                "CreateDynamicQr",
                                "CALLS_INTERFACE_METHOD",
                                "qris/publisher.go",
                                "EventPublisher.Publish"
                        ),
                        new CodeRelationship(
                                "qris/service.go",
                                "CreateDynamicQr",
                                "CALLS_FUNCTION",
                                "qris/service.go",
                                "normalizePayment"
                        )
                );
    }

}
