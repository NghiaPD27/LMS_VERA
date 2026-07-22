package vera.lms.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public class PurchaseDto {

    public record CreatePurchaseRequest(
            @NotNull(message = "Program ID is required")
            Long programId
    ) {}

    public record PurchaseResponse(
            Long id,
            Long studentId,
            String studentName,
            String studentEmail,
            Long programId,
            String programName,
            BigDecimal amount,
            String currency,
            String status,
            String paymentCode,
            String paymentQrUrl,
            String paymentProvider,
            String paymentContent,
            Long enrollmentId,
            Instant createdAt,
            Instant paidAt,
            String adminNote
    ) {}

    public record UpdatePurchaseStatusRequest(
            @NotBlank(message = "Status is required")
            String status,

            @Size(max = 1000, message = "Note must not exceed 1000 characters")
            String note
    ) {}

    public record PurchaseEventResponse(
            Long id,
            Long purchaseId,
            String oldStatus,
            String newStatus,
            String note,
            Instant createdAt
    ) {}

    public record SepayWebhookRequest(
            Long id,
            String gateway,
            String transactionDate,
            String accountNumber,
            String subAccount,
            String code,
            String content,
            String transferType,
            String description,
            BigDecimal transferAmount,
            BigDecimal accumulated,
            String referenceCode
    ) {}

    public record SepayWebhookResponse(boolean success) {}
}
