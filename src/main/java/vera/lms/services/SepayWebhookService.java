package vera.lms.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vera.lms.configs.SepayProperties;
import vera.lms.dtos.PurchaseDto.SepayWebhookRequest;
import vera.lms.dtos.PurchaseDto.SepayWebhookResponse;
import vera.lms.enums.PurchaseStatus;
import vera.lms.enums.SepayWebhookEventStatus;
import vera.lms.exceptions.UnauthorizedException;
import vera.lms.models.CoursePurchase;
import vera.lms.models.FinalAssessmentRetakePayment;
import vera.lms.models.SepayWebhookEvent;
import vera.lms.repositories.CoursePurchaseRepository;
import vera.lms.repositories.FinalAssessmentRetakePaymentRepository;
import vera.lms.repositories.SepayWebhookEventRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class SepayWebhookService {

    private static final long ALLOWED_TIMESTAMP_DRIFT_SECONDS = 300;

    private final SepayProperties sepayProperties;
    private final ObjectMapper objectMapper;
    private final CoursePurchaseRepository purchaseRepository;
    private final FinalAssessmentRetakePaymentRepository retakePaymentRepository;
    private final SepayWebhookEventRepository eventRepository;
    private final PurchaseService purchaseService;
    private final FinalAssessmentService finalAssessmentService;

    public SepayWebhookService(
            SepayProperties sepayProperties,
            ObjectMapper objectMapper,
            CoursePurchaseRepository purchaseRepository,
            FinalAssessmentRetakePaymentRepository retakePaymentRepository,
            SepayWebhookEventRepository eventRepository,
            PurchaseService purchaseService,
            FinalAssessmentService finalAssessmentService) {
        this.sepayProperties = sepayProperties;
        this.objectMapper = objectMapper;
        this.purchaseRepository = purchaseRepository;
        this.retakePaymentRepository = retakePaymentRepository;
        this.eventRepository = eventRepository;
        this.purchaseService = purchaseService;
        this.finalAssessmentService = finalAssessmentService;
    }

    public SepayWebhookResponse handleWebhook(String rawBody, String signature, String timestampHeader) {
        verifySignature(rawBody, signature, timestampHeader);

        SepayWebhookRequest payload = parsePayload(rawBody);
        if (payload.id() == null) {
            saveEvent(payload, rawBody, null, SepayWebhookEventStatus.FAILED, "Missing SePay transaction id");
            return new SepayWebhookResponse(true);
        }
        if (eventRepository.existsBySepayTransactionId(payload.id())) {
            return new SepayWebhookResponse(true);
        }

        String paymentCode = resolvePaymentCode(payload);
        if (!"in".equalsIgnoreCase(payload.transferType())) {
            saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.IGNORED, "Transfer type is not incoming");
            return new SepayWebhookResponse(true);
        }
        if (paymentCode == null) {
            saveEvent(payload, rawBody, null, SepayWebhookEventStatus.IGNORED, "Payment code not found");
            return new SepayWebhookResponse(true);
        }

        CoursePurchase purchase = purchaseRepository.findByPaymentCode(paymentCode)
                .orElse(null);
        if (!matchesConfiguredAccount(payload.accountNumber())) {
            saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.IGNORED, "Bank account does not match");
            return new SepayWebhookResponse(true);
        }

        if (purchase != null) {
            if (!matchesAmount(payload.transferAmount(), purchase.getAmount())) {
                saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.IGNORED, "Transfer amount does not match purchase amount");
                return new SepayWebhookResponse(true);
            }
            if (purchase.getStatus() != PurchaseStatus.PENDING && purchase.getStatus() != PurchaseStatus.PAID) {
                saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.IGNORED, "Purchase is not payable");
                return new SepayWebhookResponse(true);
            }

            try {
                purchaseService.markPaidFromSepay(
                        purchase,
                        String.valueOf(payload.id()),
                        payload.referenceCode(),
                        Instant.now());
                saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.PROCESSED, null);
            } catch (RuntimeException ex) {
                saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.FAILED, ex.getMessage());
            }

            return new SepayWebhookResponse(true);
        }

        FinalAssessmentRetakePayment retakePayment = retakePaymentRepository.findByPaymentCode(paymentCode)
                .orElse(null);
        if (retakePayment == null) {
            saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.IGNORED, "Payment code not found");
            return new SepayWebhookResponse(true);
        }
        if (!matchesAmount(payload.transferAmount(), retakePayment.getAmount())) {
            saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.IGNORED, "Transfer amount does not match final assessment retake amount");
            return new SepayWebhookResponse(true);
        }
        if (retakePayment.getStatus() != PurchaseStatus.PENDING && retakePayment.getStatus() != PurchaseStatus.PAID) {
            saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.IGNORED, "Final assessment retake payment is not payable");
            return new SepayWebhookResponse(true);
        }

        try {
            finalAssessmentService.markRetakePaymentPaidFromSepay(
                    retakePayment,
                    String.valueOf(payload.id()),
                    payload.referenceCode(),
                    Instant.now());
            saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.PROCESSED, null);
        } catch (RuntimeException ex) {
            saveEvent(payload, rawBody, paymentCode, SepayWebhookEventStatus.FAILED, ex.getMessage());
        }
        return new SepayWebhookResponse(true);
    }

    private void verifySignature(String rawBody, String signature, String timestampHeader) {
        if (isBlank(sepayProperties.webhookSecret())) {
            throw new UnauthorizedException("SePay webhook secret is not configured");
        }
        if (isBlank(signature) || isBlank(timestampHeader)) {
            throw new UnauthorizedException("Missing SePay signature headers");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid SePay timestamp");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > ALLOWED_TIMESTAMP_DRIFT_SECONDS) {
            throw new UnauthorizedException("SePay webhook timestamp expired");
        }

        String expected = "sha256=" + hmacSha256Hex(timestamp + "." + rawBody, sepayProperties.webhookSecret());
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid SePay webhook signature");
        }
    }

    private SepayWebhookRequest parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, SepayWebhookRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid SePay webhook payload");
        }
    }

    private String resolvePaymentCode(SepayWebhookRequest payload) {
        if (!isBlank(payload.code())) {
            return payload.code().trim();
        }
        if (isBlank(payload.content())) {
            return null;
        }
        String coursePrefix = Pattern.quote(sepayProperties.paymentCodePrefixOrDefault());
        String retakePrefix = Pattern.quote(sepayProperties.retakePaymentCodePrefixOrDefault());
        Matcher matcher = Pattern.compile("(" + coursePrefix + "|" + retakePrefix + ")\\d+").matcher(payload.content());
        return matcher.find() ? matcher.group() : null;
    }

    private boolean matchesConfiguredAccount(String accountNumber) {
        if (isBlank(sepayProperties.bankAccount())) {
            return true;
        }
        return sepayProperties.bankAccount().trim().equals(accountNumber);
    }

    private boolean matchesAmount(BigDecimal transferAmount, BigDecimal purchaseAmount) {
        if (transferAmount == null || purchaseAmount == null) {
            return false;
        }
        return transferAmount.compareTo(purchaseAmount) == 0;
    }

    private void saveEvent(
            SepayWebhookRequest payload,
            String rawBody,
            String paymentCode,
            SepayWebhookEventStatus status,
            String reason) {
        try {
            SepayWebhookEvent event = SepayWebhookEvent.builder()
                    .sepayTransactionId(payload.id())
                    .paymentCode(paymentCode)
                    .referenceCode(payload.referenceCode())
                    .accountNumber(payload.accountNumber())
                    .transferAmount(payload.transferAmount())
                    .status(status)
                    .reason(truncate(reason, 500))
                    .rawPayload(rawBody)
                    .build();
            eventRepository.save(event);
        } catch (DataIntegrityViolationException ignored) {
            // SePay can retry or replay the same transaction. The unique transaction id makes this idempotent.
        }
    }

    private String hmacSha256Hex(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot verify SePay webhook signature", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
