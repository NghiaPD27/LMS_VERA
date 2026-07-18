package vera.lms.dtos;

import jakarta.validation.constraints.NotNull;

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
            Instant paidAt
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
