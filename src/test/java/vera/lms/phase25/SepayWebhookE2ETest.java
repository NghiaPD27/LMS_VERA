package vera.lms.phase25;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SepayWebhookE2ETest extends BaseIntegrationTest {

    private static final String SEPAY_SECRET = "test-sepay-secret";
    private static final String SEPAY_ACCOUNT = "123456789";

    private Long seedStudent(String username) {
        Long studentId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "SePay", "Buyer", "0901234567");
        return studentId;
    }

    private Long seedProgram(String name) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, currency, sales_status)
                VALUES (?, ?, ?, ?, 'PUBLISHED')
                """, name, "SePay program", 1800000, "VND");
        Long programId = jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, 'SePay Lesson One', 1, 'Lesson content', 'PUBLISHED')
                """, programId);
        return programId;
    }

    private Long createPurchase(Long programId) throws Exception {
        mockMvc.perform(post("/api/student/purchases")
                        .header("Authorization", "Bearer mock-student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":" + programId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentCode").exists())
                .andExpect(jsonPath("$.paymentQrUrl").exists())
                .andExpect(jsonPath("$.paymentProvider").value("SEPAY"))
                .andExpect(jsonPath("$.paymentContent").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
        return jdbcTemplate.queryForObject("SELECT id FROM course_purchases", Long.class);
    }

    @Test
    void testCreatePurchaseIncludesSepayPaymentInfo() throws Exception {
        seedStudent("sepay_info_student");
        Long programId = seedProgram("SePay Info English");

        Long purchaseId = createPurchase(programId);
        String paymentCode = jdbcTemplate.queryForObject(
                "SELECT payment_code FROM course_purchases WHERE id = ?",
                String.class,
                purchaseId);
        String paymentQrUrl = jdbcTemplate.queryForObject(
                "SELECT payment_qr_url FROM course_purchases WHERE id = ?",
                String.class,
                purchaseId);

        assertEquals("LMSP" + purchaseId, paymentCode);
        org.junit.jupiter.api.Assertions.assertTrue(paymentQrUrl.contains("acc=" + SEPAY_ACCOUNT));
        org.junit.jupiter.api.Assertions.assertTrue(paymentQrUrl.contains("bank=Vietcombank"));
        org.junit.jupiter.api.Assertions.assertTrue(paymentQrUrl.contains("amount=1800000"));
        org.junit.jupiter.api.Assertions.assertTrue(paymentQrUrl.contains("des=" + paymentCode));
    }

    @Test
    void testValidSepayWebhookMarksPurchasePaidAndAutoEnrolls() throws Exception {
        seedStudent("sepay_valid_student");
        Long programId = seedProgram("SePay Valid English");
        Long purchaseId = createPurchase(programId);
        String paymentCode = getPaymentCode(purchaseId);

        performSepayWebhook(validPayload(92704, paymentCode, 1800000, SEPAY_ACCOUNT, "in"), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String purchaseStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM course_purchases WHERE id = ?",
                String.class,
                purchaseId);
        Long enrollmentId = jdbcTemplate.queryForObject(
                "SELECT enrollment_id FROM course_purchases WHERE id = ?",
                Long.class,
                purchaseId);
        String providerTransactionId = jdbcTemplate.queryForObject(
                "SELECT provider_transaction_id FROM course_purchases WHERE id = ?",
                String.class,
                purchaseId);
        Integer processedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sepay_webhook_events WHERE sepay_transaction_id = 92704 AND status = 'PROCESSED'",
                Integer.class);

        assertEquals("PAID", purchaseStatus);
        assertNotNull(enrollmentId);
        assertEquals("92704", providerTransactionId);
        assertEquals(1, processedEvents);
    }

    @Test
    void testDuplicateSepayWebhookIsIdempotent() throws Exception {
        Long studentId = seedStudent("sepay_duplicate_student");
        Long programId = seedProgram("SePay Duplicate English");
        Long purchaseId = createPurchase(programId);
        String rawBody = validPayload(92705, getPaymentCode(purchaseId), 1800000, SEPAY_ACCOUNT, "in");

        performSepayWebhook(rawBody, true).andExpect(status().isOk());
        performSepayWebhook(rawBody, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Integer enrollmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE student_id = ? AND status = 'ACTIVE'",
                Integer.class,
                studentId);
        Integer progressCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_lesson_progress WHERE student_id = ?",
                Integer.class,
                studentId);
        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sepay_webhook_events WHERE sepay_transaction_id = 92705",
                Integer.class);

        assertEquals(1, enrollmentCount);
        assertEquals(1, progressCount);
        assertEquals(1, eventCount);
    }

    @Test
    void testInvalidSepaySignatureIsRejected() throws Exception {
        seedStudent("sepay_bad_sig_student");
        Long programId = seedProgram("SePay Bad Signature English");
        Long purchaseId = createPurchase(programId);

        String rawBody = validPayload(92706, getPaymentCode(purchaseId), 1800000, SEPAY_ACCOUNT, "in");

        mockMvc.perform(post("/api/webhooks/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SePay-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-SePay-Signature", "sha256=bad")
                        .content(rawBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testExpiredSepayTimestampIsRejected() throws Exception {
        seedStudent("sepay_old_timestamp_student");
        Long programId = seedProgram("SePay Old Timestamp English");
        Long purchaseId = createPurchase(programId);
        String rawBody = validPayload(92707, getPaymentCode(purchaseId), 1800000, SEPAY_ACCOUNT, "in");
        long oldTimestamp = Instant.now().minusSeconds(600).getEpochSecond();

        mockMvc.perform(post("/api/webhooks/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SePay-Timestamp", String.valueOf(oldTimestamp))
                        .header("X-SePay-Signature", signature(rawBody, oldTimestamp))
                        .content(rawBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSepayWebhookWithInvalidBusinessDataIsIgnored() throws Exception {
        seedStudent("sepay_ignored_student");
        Long programId = seedProgram("SePay Ignored English");
        Long purchaseId = createPurchase(programId);
        String paymentCode = getPaymentCode(purchaseId);

        performSepayWebhook(validPayload(92708, paymentCode, 1, SEPAY_ACCOUNT, "in"), true).andExpect(status().isOk());
        performSepayWebhook(validPayload(92709, paymentCode, 1800000, "999999999", "in"), true).andExpect(status().isOk());
        performSepayWebhook(validPayload(92710, paymentCode, 1800000, SEPAY_ACCOUNT, "out"), true).andExpect(status().isOk());
        performSepayWebhook(validPayload(92711, "LMSP999999", 1800000, SEPAY_ACCOUNT, "in"), true).andExpect(status().isOk());

        String purchaseStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM course_purchases WHERE id = ?",
                String.class,
                purchaseId);
        Integer ignoredEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sepay_webhook_events WHERE status = 'IGNORED'",
                Integer.class);

        assertEquals("PENDING", purchaseStatus);
        assertEquals(4, ignoredEvents);
    }

    private org.springframework.test.web.servlet.ResultActions performSepayWebhook(String rawBody, boolean validSignature) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        return mockMvc.perform(post("/api/webhooks/sepay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-SePay-Timestamp", String.valueOf(timestamp))
                .header("X-SePay-Signature", validSignature ? signature(rawBody, timestamp) : "sha256=bad")
                .content(rawBody));
    }

    private String validPayload(long id, String paymentCode, int amount, String accountNumber, String transferType) {
        return """
                {
                  "id": %d,
                  "gateway": "Vietcombank",
                  "transactionDate": "2026-07-18 10:00:00",
                  "accountNumber": "%s",
                  "subAccount": "",
                  "code": "%s",
                  "content": "%s chuyen tien",
                  "transferType": "%s",
                  "description": "NGUYEN VAN A chuyen tien",
                  "transferAmount": %d,
                  "accumulated": 5000000,
                  "referenceCode": "FT%d"
                }
                """.formatted(id, accountNumber, paymentCode, paymentCode, transferType, amount, id);
    }

    private String getPaymentCode(Long purchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT payment_code FROM course_purchases WHERE id = ?",
                String.class,
                purchaseId);
    }

    private String signature(String rawBody, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SEPAY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String value = timestamp + "." + rawBody;
        String hex = HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        return "sha256=" + hex;
    }
}
