package vera.lms.phase7;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinalAssessmentE2ETest extends BaseIntegrationTest {

    private static final String SEPAY_SECRET = "test-sepay-secret";
    private static final String SEPAY_ACCOUNT = "123456789";
    private static final Instant REVIEWED_AT = Instant.parse("2030-02-08T10:00:00Z");
    private static final Instant SLOT_START = Instant.parse("2030-02-08T09:00:00Z");

    @Test
    void testStudentCompletingFinalPublishedLessonGetsFreeFinalAssessmentAndPassCompletesEnrollment() throws Exception {
        Fixture fixture = seedFinalReadyStudent("Free Pass", 250000);

        mockMvc.perform(get("/api/admin/final-assessment-eligible-students")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", fixture.programId().toString())
                        .param("weekStart", "2030-02-01T00:00:00Z")
                        .param("weekEnd", "2030-02-15T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].enrollmentId").value(fixture.enrollmentId()))
                .andExpect(jsonPath("$[0].finalLessonNumber").value(3))
                .andExpect(jsonPath("$[0].retake").value(false))
                .andExpect(jsonPath("$[0].eligibleAt").value("2030-02-08T10:00:00Z"));

        createFinalSession(fixture, fixture.evaluatorId());
        Long sessionId = sessionId(fixture.enrollmentId());
        Long participantId = participantId(fixture.enrollmentId());

        mockMvc.perform(get("/api/evaluator/final-assessment-sessions")
                        .header("Authorization", "Bearer evaluator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sessionId))
                .andExpect(jsonPath("$[0].participants[0].retake").value(false));

        mockMvc.perform(post("/api/evaluator/final-assessment-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "PASS",
                                  "comment": "Ready to graduate"
                                }
                                """.formatted(participantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PASS"));

        assertEquals("COMPLETED", enrollmentStatus(fixture.enrollmentId()));

        mockMvc.perform(get("/api/student/final-assessment-status")
                        .header("Authorization", "Bearer student-token")
                        .param("enrollmentId", fixture.enrollmentId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.lastResult").value("PASS"))
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    void testNotPassRequiresStudentPaidRetakeBeforeEligibleAgain() throws Exception {
        Fixture fixture = seedFinalReadyStudent("Paid Retake", 300000);
        createFinalSession(fixture, fixture.evaluatorId());
        Long firstParticipantId = participantId(fixture.enrollmentId());

        mockMvc.perform(post("/api/evaluator/final-assessment-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "NOT_PASS",
                                  "comment": "Needs one more attempt"
                                }
                                """.formatted(firstParticipantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_PASS"));
        assertEquals("WAITING_FOR_REASSESSMENT", enrollmentStatus(fixture.enrollmentId()));

        mockMvc.perform(get("/api/admin/final-assessment-eligible-students")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", fixture.programId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/student/final-assessment-retake-payments")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":" + fixture.enrollmentId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(300000))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentCode").exists())
                .andExpect(jsonPath("$.paymentQrUrl").exists());

        Long paymentId = jdbcTemplate.queryForObject("SELECT id FROM final_assessment_retake_payments", Long.class);
        String paymentCode = jdbcTemplate.queryForObject(
                "SELECT payment_code FROM final_assessment_retake_payments WHERE id = ?",
                String.class,
                paymentId);
        assertEquals("LMSR" + paymentId, paymentCode);

        mockMvc.perform(post("/api/student/final-assessment-retake-payments")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":" + fixture.enrollmentId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Enrollment already has a pending final assessment retake payment"));

        performSepayWebhook(validPayload(93701, paymentCode, 300000, SEPAY_ACCOUNT, "in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertEquals("PAID", retakePaymentStatus(paymentId));

        mockMvc.perform(get("/api/admin/final-assessment-eligible-students")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", fixture.programId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].retake").value(true))
                .andExpect(jsonPath("$[0].retakePaymentId").value(paymentId));

        createFinalSession(fixture, fixture.evaluatorId());
        Long secondParticipantId = latestParticipantId(fixture.enrollmentId());

        mockMvc.perform(get("/api/admin/final-assessment-sessions/" + latestSessionId(fixture.enrollmentId()))
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].retake").value(true))
                .andExpect(jsonPath("$.participants[0].retakePaymentId").value(paymentId));

        mockMvc.perform(post("/api/evaluator/final-assessment-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "PASS"
                                }
                                """.formatted(secondParticipantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PASS"));

        assertEquals("COMPLETED", enrollmentStatus(fixture.enrollmentId()));
    }

    @Test
    void testRetakePaymentIsBlockedBeforeFailureOrWithoutConfiguredPrice() throws Exception {
        Fixture activeFixture = seedFinalReadyStudent("No Failure Yet", 250000);

        mockMvc.perform(post("/api/student/final-assessment-retake-payments")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":" + activeFixture.enrollmentId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Enrollment is not waiting for final assessment reassessment"));

        jdbcTemplate.update("UPDATE programs SET final_assessment_retake_price = 0 WHERE id = ?", activeFixture.programId());
        jdbcTemplate.update("UPDATE enrollments SET status = 'WAITING_FOR_REASSESSMENT' WHERE id = ?", activeFixture.enrollmentId());

        mockMvc.perform(post("/api/student/final-assessment-retake-payments")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":" + activeFixture.enrollmentId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Final assessment retake price is not configured"));
    }

    @Test
    void testEvaluatorCannotSubmitResultForAnotherEvaluatorFinalSession() throws Exception {
        Fixture fixture = seedFinalReadyStudent("Forbidden Evaluator", 250000);
        Long otherEvaluatorId = seedEvaluator("other_eval");
        createFinalSession(fixture, otherEvaluatorId);
        Long participantId = participantId(fixture.enrollmentId());

        mockMvc.perform(post("/api/evaluator/final-assessment-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "PASS"
                                }
                                """.formatted(participantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Final assessment session does not belong to current evaluator"));
    }

    private Fixture seedFinalReadyStudent(String suffix, int retakePrice) {
        Long studentId = seedStudent("student_user");
        Long teacherId = seedTeacher("teacher_user");
        Long evaluatorId = seedEvaluator("eval_user");
        Long programId = seedProgram("Final Program " + suffix, retakePrice);
        Long lesson1 = seedLesson(programId, 1);
        Long lesson2 = seedLesson(programId, 2);
        Long lesson3 = seedLesson(programId, 3);
        Long enrollmentId = seedEnrollment(studentId, programId);
        seedLessonProgress(studentId, lesson1, "COMPLETED");
        seedLessonProgress(studentId, lesson2, "COMPLETED");
        seedLessonProgress(studentId, lesson3, "COMPLETED");
        seedApprovedFinalReview(studentId, teacherId, enrollmentId, lesson3);
        return new Fixture(studentId, teacherId, evaluatorId, programId, enrollmentId, lesson3);
    }

    private Long seedStudent(String username) {
        Long studentId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "Final", username, "0901234567");
        return studentId;
    }

    private Long seedTeacher(String username) {
        Long teacherId = createUser(username, username + "@teacher.test", "Password123", "TEACHER", true);
        createAccountAccess(teacherId, "ACTIVE", false);
        createTeacherProfile(teacherId, "Teacher", username, "0907654321", "Final teacher");
        return teacherId;
    }

    private Long seedEvaluator(String username) {
        Long evaluatorId = createUser(username, username + "@eval.test", "Password123", "EVALUATOR", true);
        createAccountAccess(evaluatorId, "ACTIVE", false);
        createEvaluatorProfile(evaluatorId, "Evaluator", username, "0902223333");
        return evaluatorId;
    }

    private Long seedProgram(String name, int retakePrice) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, final_assessment_retake_price, currency, sales_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, name, "Final assessment program", 1800000, retakePrice, "VND", "PUBLISHED");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedLesson(Long programId, int lessonNumber) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, 'PUBLISHED')
                """, programId, "Final Lesson " + lessonNumber, lessonNumber, "Lesson content");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lessons WHERE program_id = ? AND lesson_number = ?",
                Long.class,
                programId,
                lessonNumber);
    }

    private Long seedEnrollment(Long studentId, Long programId) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (student_id, program_id, status, enrolled_at, expired_at)
                VALUES (?, ?, 'ACTIVE', ?, ?)
                """,
                studentId,
                programId,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(60, ChronoUnit.DAYS));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM enrollments WHERE student_id = ? AND program_id = ?",
                Long.class,
                studentId,
                programId);
    }

    private void seedLessonProgress(Long studentId, Long lessonId, String status) {
        jdbcTemplate.update("""
                INSERT INTO student_lesson_progress (student_id, lesson_id, status)
                VALUES (?, ?, ?)
                """, studentId, lessonId, status);
    }

    private void seedApprovedFinalReview(Long studentId, Long teacherId, Long enrollmentId, Long lessonId) {
        jdbcTemplate.update("""
                INSERT INTO teacher_availability (teacher_id, start_at, end_at)
                VALUES (?, ?, ?)
                """, teacherId, SLOT_START, SLOT_START.plus(1, ChronoUnit.HOURS));
        Long availabilityId = jdbcTemplate.queryForObject("SELECT id FROM teacher_availability", Long.class);
        jdbcTemplate.update("""
                INSERT INTO teacher_bookings
                    (student_id, teacher_id, enrollment_id, lesson_id, availability_id, start_at, end_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPLETED')
                """,
                studentId,
                teacherId,
                enrollmentId,
                lessonId,
                availabilityId,
                SLOT_START,
                SLOT_START.plus(1, ChronoUnit.HOURS));
        Long bookingId = jdbcTemplate.queryForObject("SELECT id FROM teacher_bookings", Long.class);
        jdbcTemplate.update("""
                INSERT INTO teacher_reviews (booking_id, result, comment, reviewed_at)
                VALUES (?, 'APPROVED', 'Ready for final assessment', ?)
                """, bookingId, REVIEWED_AT);
    }

    private void createFinalSession(Fixture fixture, Long evaluatorId) throws Exception {
        mockMvc.perform(post("/api/admin/final-assessment-sessions")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programId": %d,
                                  "evaluatorId": %d,
                                  "scheduledAt": "2030-02-15T13:00:00Z",
                                  "meetLink": "https://meet.google.com/final",
                                  "participantEnrollmentIds": [%d]
                                }
                                """.formatted(fixture.programId(), evaluatorId, fixture.enrollmentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(fixture.programId()))
                .andExpect(jsonPath("$.participants.length()").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions performSepayWebhook(String rawBody) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        return mockMvc.perform(post("/api/webhooks/sepay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-SePay-Timestamp", String.valueOf(timestamp))
                .header("X-SePay-Signature", signature(rawBody, timestamp))
                .content(rawBody));
    }

    private String validPayload(long id, String paymentCode, int amount, String accountNumber, String transferType) {
        return """
                {
                  "id": %d,
                  "gateway": "Vietcombank",
                  "transactionDate": "2030-02-16 10:00:00",
                  "accountNumber": "%s",
                  "subAccount": "",
                  "code": "%s",
                  "content": "%s final retake",
                  "transferType": "%s",
                  "description": "Student final retake payment",
                  "transferAmount": %d,
                  "accumulated": 5000000,
                  "referenceCode": "FT%d"
                }
                """.formatted(id, accountNumber, paymentCode, paymentCode, transferType, amount, id);
    }

    private String signature(String rawBody, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SEPAY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String value = timestamp + "." + rawBody;
        String hex = HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        return "sha256=" + hex;
    }

    private Long participantId(Long enrollmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM final_assessment_participants WHERE enrollment_id = ?",
                Long.class,
                enrollmentId);
    }

    private Long latestParticipantId(Long enrollmentId) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM final_assessment_participants
                WHERE enrollment_id = ?
                ORDER BY id DESC
                FETCH FIRST 1 ROW ONLY
                """, Long.class, enrollmentId);
    }

    private Long sessionId(Long enrollmentId) {
        return jdbcTemplate.queryForObject("""
                SELECT session_id FROM final_assessment_participants
                WHERE enrollment_id = ?
                """, Long.class, enrollmentId);
    }

    private Long latestSessionId(Long enrollmentId) {
        return jdbcTemplate.queryForObject("""
                SELECT session_id FROM final_assessment_participants
                WHERE enrollment_id = ?
                ORDER BY id DESC
                FETCH FIRST 1 ROW ONLY
                """, Long.class, enrollmentId);
    }

    private String enrollmentStatus(Long enrollmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM enrollments WHERE id = ?",
                String.class,
                enrollmentId);
    }

    private String retakePaymentStatus(Long paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM final_assessment_retake_payments WHERE id = ?",
                String.class,
                paymentId);
    }

    private record Fixture(
            Long studentId,
            Long teacherId,
            Long evaluatorId,
            Long programId,
            Long enrollmentId,
            Long finalLessonId
    ) {}
}
