package vera.lms.phase9;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportingAuditE2ETest extends BaseIntegrationTest {

    private static final Instant SLOT_START = Instant.parse("2030-04-01T09:00:00Z");

    @Test
    void testAdminDashboardAndStudentProgressFilters() throws Exception {
        Long teacherId = seedTeacher("teacher_user");
        Long otherTeacherId = seedTeacher("other_teacher");
        Long studentId = seedStudent("student_user", "ACTIVE");
        Long otherStudentId = seedStudent("other_student", "SUSPENDED");
        Long programId = seedProgram("Phase 9 Program");
        Long otherProgramId = seedProgram("Other Phase 9 Program");
        Long lesson1 = seedLesson(programId, 1);
        Long lesson2 = seedLesson(programId, 2);
        Long enrollmentId = seedEnrollment(studentId, programId, "ACTIVE", Instant.now().plus(30, ChronoUnit.DAYS));
        Long otherEnrollmentId = seedEnrollment(otherStudentId, otherProgramId, "ACTIVE", Instant.now().plus(45, ChronoUnit.DAYS));
        seedLessonProgress(studentId, lesson1, "COMPLETED");
        seedLessonProgress(studentId, lesson2, "QUIZ_AVAILABLE");
        seedLessonProgress(otherStudentId, seedLesson(otherProgramId, 1), "VIDEO_IN_PROGRESS");
        seedTeacherAssignment(enrollmentId, teacherId);
        seedTeacherAssignment(otherEnrollmentId, otherTeacherId);
        seedPurchase(studentId, programId, "PENDING");
        seedPurchase(otherStudentId, otherProgramId, "PAID");

        mockMvc.perform(get("/api/admin/reports/dashboard")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(2))
                .andExpect(jsonPath("$.totalTeachers").value(2))
                .andExpect(jsonPath("$.activeAccounts").value(4))
                .andExpect(jsonPath("$.suspendedAccounts").value(1))
                .andExpect(jsonPath("$.activeEnrollments").value(2))
                .andExpect(jsonPath("$.pendingPurchases").value(1))
                .andExpect(jsonPath("$.paidPurchases").value(1));

        mockMvc.perform(get("/api/admin/reports/student-progress")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", programId.toString())
                        .param("enrollmentStatus", "ACTIVE")
                        .param("accountStatus", "ACTIVE")
                        .param("teacherId", teacherId.toString())
                        .param("keyword", "student_user")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].enrollmentId").value(enrollmentId))
                .andExpect(jsonPath("$.content[0].studentId").value(studentId))
                .andExpect(jsonPath("$.content[0].programId").value(programId))
                .andExpect(jsonPath("$.content[0].progressPercent").value(50))
                .andExpect(jsonPath("$.content[0].currentLessonNumber").value(2))
                .andExpect(jsonPath("$.content[0].nextAction").value("TAKE_QUIZ"))
                .andExpect(jsonPath("$.content[0].teacherId").value(teacherId));

        mockMvc.perform(get("/api/admin/reports/student-progress/" + enrollmentId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.enrollmentId").value(enrollmentId))
                .andExpect(jsonPath("$.lessons.length()").value(2))
                .andExpect(jsonPath("$.lessons[0].progressStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.lessons[1].progressStatus").value("QUIZ_AVAILABLE"));
    }

    @Test
    void testAuditLogsAreWrittenForImportantAdminTeacherEvaluatorActions() throws Exception {
        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student_user",
                                  "email": "student@phase9.test",
                                  "password": "Password123",
                                  "firstName": "Audit",
                                  "lastName": "Student",
                                  "phoneNumber": "0901234567"
                                }
                                """))
                .andExpect(status().isCreated());
        Long studentId = userId("student_user");
        Long teacherId = seedTeacher("teacher_user");
        Long evaluatorId = seedEvaluator("eval_user");
        Long checkpointStudentId = seedStudent("checkpoint_student", "ACTIVE");
        Long finalStudentId = seedStudent("final_student", "ACTIVE");
        Long programId = seedProgram("Audit Program");
        Long lessonId = seedLesson(programId, 1);
        Long enrollmentId = seedEnrollment(studentId, programId, "ACTIVE", Instant.now().plus(30, ChronoUnit.DAYS));
        seedLessonProgress(studentId, lessonId, "WAITING_FOR_TEACHER");

        mockMvc.perform(patch("/api/admin/enrollments/" + enrollmentId + "/extend")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"months\":2}"))
                .andExpect(status().isOk());

        Long draftLessonId = seedDraftLesson(programId, 2);
        mockMvc.perform(post("/api/lessons/" + draftLessonId + "/publish")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());

        seedTeacherCompensation(teacherId);
        Long bookingId = seedBooking(studentId, teacherId, enrollmentId, lessonId);
        mockMvc.perform(post("/api/teacher/bookings/" + bookingId + "/review")
                        .header("Authorization", "Bearer teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"APPROVED\",\"comment\":\"audit teacher review\"}"))
                .andExpect(status().isOk());

        Long checkpointParticipantId = seedCheckpointParticipant(checkpointStudentId, evaluatorId);
        mockMvc.perform(post("/api/evaluator/checkpoint-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "NOT_PASS",
                                  "comment": "audit checkpoint"
                                }
                                """.formatted(checkpointParticipantId)))
                .andExpect(status().isOk());

        Long finalParticipantId = seedFinalAssessmentParticipant(finalStudentId, evaluatorId);
        mockMvc.perform(post("/api/evaluator/final-assessment-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "PASS",
                                  "comment": "audit final"
                                }
                                """.formatted(finalParticipantId)))
                .andExpect(status().isOk());

        assertAuditAction("USER_CREATED", "USER", studentId);
        assertAuditAction("ENROLLMENT_EXTENDED", "ENROLLMENT", enrollmentId);
        assertAuditAction("LESSON_PUBLISHED", "LESSON", draftLessonId);
        assertAuditAction("TEACHER_REVIEW_SUBMITTED", "TEACHER_BOOKING", bookingId);
        assertAuditAction("CHECKPOINT_RESULT_SUBMITTED", "CHECKPOINT_PARTICIPANT", checkpointParticipantId);
        assertAuditAction("FINAL_ASSESSMENT_RESULT_SUBMITTED", "FINAL_ASSESSMENT_PARTICIPANT", finalParticipantId);

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer admin-token")
                        .param("action", "FINAL_ASSESSMENT_RESULT_SUBMITTED")
                        .param("targetType", "FINAL_ASSESSMENT_PARTICIPANT")
                        .param("targetId", finalParticipantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorUsername").value("eval_user"))
                .andExpect(jsonPath("$.content[0].details").value(org.hamcrest.Matchers.containsString("result=PASS")));

        Long auditLogId = jdbcTemplate.queryForObject(
                "SELECT id FROM audit_logs WHERE action = 'FINAL_ASSESSMENT_RESULT_SUBMITTED'",
                Long.class);
        mockMvc.perform(get("/api/admin/audit-logs/" + auditLogId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(auditLogId));
    }

    private void assertAuditAction(String action, String targetType, Long targetId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_logs
                WHERE action = ? AND target_type = ? AND target_id = ?
                """, Integer.class, action, targetType, targetId);
        assertEquals(1, count);
    }

    private Long seedStudent(String username, String status) {
        Long userId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(userId, status, false);
        createStudentProfile(userId, "Phase9", username, "0901234567");
        return userId;
    }

    private Long seedTeacher(String username) {
        Long userId = createUser(username, username + "@teacher.test", "Password123", "TEACHER", true);
        createAccountAccess(userId, "ACTIVE", false);
        createTeacherProfile(userId, "Teacher", username, "0907654321", "Phase 9 teacher");
        return userId;
    }

    private Long seedEvaluator(String username) {
        Long userId = createUser(username, username + "@eval.test", "Password123", "EVALUATOR", true);
        createAccountAccess(userId, "ACTIVE", false);
        createEvaluatorProfile(userId, "Evaluator", username, "0902223333");
        return userId;
    }

    private Long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private Long seedProgram(String name) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, final_assessment_retake_price, currency, sales_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, name, "Phase 9 program", 1800000, 250000, "VND", "PUBLISHED");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedLesson(Long programId, int lessonNumber) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, 'PUBLISHED')
                """, programId, "Phase 9 Lesson " + lessonNumber, lessonNumber, "Lesson content");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lessons WHERE program_id = ? AND lesson_number = ?",
                Long.class,
                programId,
                lessonNumber);
    }

    private Long seedDraftLesson(Long programId, int lessonNumber) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, 'DRAFT')
                """, programId, "Phase 9 Draft " + lessonNumber, lessonNumber, "Draft content");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lessons WHERE program_id = ? AND lesson_number = ?",
                Long.class,
                programId,
                lessonNumber);
    }

    private Long seedEnrollment(Long studentId, Long programId, String status, Instant expiredAt) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (student_id, program_id, status, enrolled_at, expired_at)
                VALUES (?, ?, ?, ?, ?)
                """, studentId, programId, status, Instant.now().minus(1, ChronoUnit.DAYS), expiredAt);
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

    private void seedTeacherAssignment(Long enrollmentId, Long teacherId) {
        jdbcTemplate.update("""
                INSERT INTO student_teacher_assignments (enrollment_id, teacher_id, assigned_at)
                VALUES (?, ?, ?)
                """, enrollmentId, teacherId, Instant.now());
    }

    private void seedPurchase(Long studentId, Long programId, String status) {
        jdbcTemplate.update("""
                INSERT INTO course_purchases
                    (student_id, program_id, amount, currency, program_name, payment_code, payment_qr_url, payment_provider, payment_content, status)
                VALUES (?, ?, ?, 'VND', 'Phase 9 Program', ?, 'qr', 'SEPAY', ?, ?)
                """, studentId, programId, 1800000, "P9" + studentId + status, "P9" + studentId + status, status);
    }

    private void seedTeacherCompensation(Long teacherId) {
        jdbcTemplate.update("""
                INSERT INTO teacher_compensation_configs (teacher_id, amount_per_session, currency)
                VALUES (?, 50000, 'VND')
                """, teacherId);
    }

    private Long seedBooking(Long studentId, Long teacherId, Long enrollmentId, Long lessonId) {
        jdbcTemplate.update("""
                INSERT INTO teacher_availability (teacher_id, start_at, end_at)
                VALUES (?, ?, ?)
                """, teacherId, SLOT_START, SLOT_START.plus(1, ChronoUnit.HOURS));
        Long availabilityId = jdbcTemplate.queryForObject("SELECT id FROM teacher_availability WHERE teacher_id = ?", Long.class, teacherId);
        jdbcTemplate.update("""
                INSERT INTO teacher_bookings
                    (student_id, teacher_id, enrollment_id, lesson_id, availability_id, start_at, end_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'BOOKED')
                """, studentId, teacherId, enrollmentId, lessonId, availabilityId, SLOT_START, SLOT_START.plus(1, ChronoUnit.HOURS));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM teacher_bookings WHERE student_id = ? AND lesson_id = ?",
                Long.class,
                studentId,
                lessonId);
    }

    private Long seedCheckpointParticipant(Long studentId, Long evaluatorId) {
        Long programId = seedProgram("Audit Checkpoint Program");
        Long lesson5 = seedLesson(programId, 5);
        Long lesson6 = seedLesson(programId, 6);
        Long enrollmentId = seedEnrollment(studentId, programId, "ACTIVE", Instant.now().plus(30, ChronoUnit.DAYS));
        seedLessonProgress(studentId, lesson5, "WAITING_FOR_CHECKPOINT");
        seedLessonProgress(studentId, lesson6, "LOCKED");
        jdbcTemplate.update("""
                INSERT INTO checkpoints (program_id, block_number, start_lesson_number, gate_lesson_number, next_lesson_number)
                VALUES (?, 1, 1, 5, 6)
                """, programId);
        Long checkpointId = jdbcTemplate.queryForObject("SELECT id FROM checkpoints WHERE program_id = ?", Long.class, programId);
        jdbcTemplate.update("""
                INSERT INTO checkpoint_sessions (checkpoint_id, evaluator_id, scheduled_at, meet_link, status)
                VALUES (?, ?, ?, 'https://meet.google.com/audit-checkpoint', 'PENDING')
                """, checkpointId, evaluatorId, SLOT_START);
        Long sessionId = jdbcTemplate.queryForObject("SELECT id FROM checkpoint_sessions WHERE checkpoint_id = ?", Long.class, checkpointId);
        jdbcTemplate.update("""
                INSERT INTO checkpoint_participants (session_id, enrollment_id, student_id)
                VALUES (?, ?, ?)
                """, sessionId, enrollmentId, studentId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM checkpoint_participants WHERE session_id = ?",
                Long.class,
                sessionId);
    }

    private Long seedFinalAssessmentParticipant(Long studentId, Long evaluatorId) {
        Long programId = seedProgram("Audit Final Program");
        Long enrollmentId = seedEnrollment(studentId, programId, "ACTIVE", Instant.now().plus(30, ChronoUnit.DAYS));
        jdbcTemplate.update("""
                INSERT INTO final_assessment_sessions (program_id, evaluator_id, scheduled_at, meet_link, status)
                VALUES (?, ?, ?, 'https://meet.google.com/audit-final', 'PENDING')
                """, programId, evaluatorId, SLOT_START);
        Long sessionId = jdbcTemplate.queryForObject("SELECT id FROM final_assessment_sessions WHERE program_id = ?", Long.class, programId);
        jdbcTemplate.update("""
                INSERT INTO final_assessment_participants (session_id, enrollment_id, student_id)
                VALUES (?, ?, ?)
                """, sessionId, enrollmentId, studentId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM final_assessment_participants WHERE session_id = ?",
                Long.class,
                sessionId);
    }
}
