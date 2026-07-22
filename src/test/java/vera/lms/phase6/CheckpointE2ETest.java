package vera.lms.phase6;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckpointE2ETest extends BaseIntegrationTest {

    private static final Instant REVIEWED_AT = Instant.parse("2030-01-08T10:00:00Z");
    private static final Instant SLOT_START = Instant.parse("2030-01-08T09:00:00Z");

    private Long seedStudent(String username) {
        Long studentId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "Phase6", username, "0901234567");
        return studentId;
    }

    private Long seedTeacher(String username) {
        Long teacherId = createUser(username, username + "@teacher.test", "Password123", "TEACHER", true);
        createAccountAccess(teacherId, "ACTIVE", false);
        createTeacherProfile(teacherId, "Teacher", username, "0907654321", "Checkpoint teacher");
        return teacherId;
    }

    private Long seedEvaluator(String username) {
        Long evaluatorId = createUser(username, username + "@eval.test", "Password123", "EVALUATOR", true);
        createAccountAccess(evaluatorId, "ACTIVE", false);
        createEvaluatorProfile(evaluatorId, "Evaluator", username, "0902223333");
        return evaluatorId;
    }

    private Long seedProgram(String name) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, currency, sales_status)
                VALUES (?, ?, ?, ?, ?)
                """, name, "Checkpoint program", 1800000, "VND", "PUBLISHED");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedLesson(Long programId, int lessonNumber) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, 'PUBLISHED')
                """, programId, "Checkpoint Lesson " + lessonNumber, lessonNumber, "Lesson content");
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
                Instant.now().plus(30, ChronoUnit.DAYS));
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

    private Fixture seedCheckpointReadyStudent(String suffix) {
        Long studentId = seedStudent("student_user");
        Long teacherId = seedTeacher("teacher_user");
        Long evaluatorId = seedEvaluator("eval_user");
        Long programId = seedProgram("Checkpoint Program " + suffix);
        Long lesson1 = seedLesson(programId, 1);
        Long lesson2 = seedLesson(programId, 2);
        Long lesson3 = seedLesson(programId, 3);
        Long lesson4 = seedLesson(programId, 4);
        Long lesson5 = seedLesson(programId, 5);
        Long lesson6 = seedLesson(programId, 6);
        Long enrollmentId = seedEnrollment(studentId, programId);

        seedLessonProgress(studentId, lesson1, "COMPLETED");
        seedLessonProgress(studentId, lesson2, "COMPLETED");
        seedLessonProgress(studentId, lesson3, "COMPLETED");
        seedLessonProgress(studentId, lesson4, "COMPLETED");
        seedLessonProgress(studentId, lesson5, "WAITING_FOR_CHECKPOINT");
        seedLessonProgress(studentId, lesson6, "LOCKED");
        seedApprovedGateReview(studentId, teacherId, enrollmentId, lesson5);

        return new Fixture(studentId, teacherId, evaluatorId, programId, enrollmentId, lesson5, lesson6);
    }

    private void seedApprovedGateReview(Long studentId, Long teacherId, Long enrollmentId, Long lessonId) {
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
                VALUES (?, 'APPROVED', 'Ready for checkpoint', ?)
                """, bookingId, REVIEWED_AT);
    }

    @Test
    void testAdminGetsWeeklyCheckpointEligibleStudentsAndCreatesGroupSession() throws Exception {
        Fixture fixture = seedCheckpointReadyStudent("Weekly Group");

        mockMvc.perform(get("/api/admin/checkpoint-eligible-students")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", fixture.programId().toString())
                        .param("blockNumber", "1")
                        .param("weekStart", "2030-01-05T00:00:00Z")
                        .param("weekEnd", "2030-01-12T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(fixture.studentId()))
                .andExpect(jsonPath("$[0].enrollmentId").value(fixture.enrollmentId()))
                .andExpect(jsonPath("$[0].blockNumber").value(1))
                .andExpect(jsonPath("$[0].gateLessonNumber").value(5))
                .andExpect(jsonPath("$[0].nextLessonNumber").value(6))
                .andExpect(jsonPath("$[0].eligibleAt").value("2030-01-08T10:00:00Z"));

        mockMvc.perform(post("/api/admin/checkpoint-sessions")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programId": %d,
                                  "blockNumber": 1,
                                  "evaluatorId": %d,
                                  "scheduledAt": "2030-01-15T13:00:00Z",
                                  "meetLink": "https://meet.google.com/checkpoint-weekly",
                                  "participantEnrollmentIds": [%d]
                                }
                                """.formatted(fixture.programId(), fixture.evaluatorId(), fixture.enrollmentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(fixture.programId()))
                .andExpect(jsonPath("$.evaluatorId").value(fixture.evaluatorId()))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.participants[0].studentId").value(fixture.studentId()));

        mockMvc.perform(get("/api/evaluator/checkpoint-sessions")
                        .header("Authorization", "Bearer evaluator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].meetLink").value("https://meet.google.com/checkpoint-weekly"))
                .andExpect(jsonPath("$[0].participants[0].enrollmentId").value(fixture.enrollmentId()));
    }

    @Test
    void testAdminCanSearchEvaluatorsForCheckpointAssignment() throws Exception {
        Long evaluatorId = seedEvaluator("eval_user");
        seedEvaluator("other_eval");

        mockMvc.perform(get("/api/admin/evaluators")
                        .header("Authorization", "Bearer admin-token")
                        .param("keyword", "eval_user")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(evaluatorId))
                .andExpect(jsonPath("$.content[0].username").value("eval_user"))
                .andExpect(jsonPath("$.content[0].email").value("eval_user@eval.test"))
                .andExpect(jsonPath("$.content[0].firstName").value("Evaluator"))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void testAdminCanListDetailUpdateRemoveAndCancelCheckpointSessionBeforeResults() throws Exception {
        Fixture fixture = seedCheckpointReadyStudent("Admin Manage");
        Long otherEvaluatorId = seedEvaluator("other_eval");
        createCheckpointSession(fixture);
        Long sessionId = sessionId(fixture.enrollmentId());
        Long participantId = participantId(fixture.enrollmentId());

        mockMvc.perform(get("/api/admin/checkpoint-sessions")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", fixture.programId().toString())
                        .param("blockNumber", "1")
                        .param("status", "PENDING")
                        .param("weekStart", "2030-01-14T00:00:00Z")
                        .param("weekEnd", "2030-01-16T00:00:00Z")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(sessionId))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].participants[0].id").value(participantId));

        mockMvc.perform(get("/api/admin/checkpoint-sessions/" + sessionId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.meetLink").value("https://meet.google.com/checkpoint"));

        mockMvc.perform(patch("/api/admin/checkpoint-sessions/" + sessionId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evaluatorId": %d,
                                  "scheduledAt": "2030-01-15T15:00:00Z",
                                  "meetLink": "https://meet.google.com/updated"
                                }
                                """.formatted(otherEvaluatorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatorId").value(otherEvaluatorId))
                .andExpect(jsonPath("$.scheduledAt").value("2030-01-15T15:00:00Z"))
                .andExpect(jsonPath("$.meetLink").value("https://meet.google.com/updated"));

        mockMvc.perform(delete("/api/admin/checkpoint-sessions/" + sessionId + "/participants/" + participantId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(0));

        mockMvc.perform(patch("/api/admin/checkpoint-sessions/" + sessionId + "/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void testStudentBeforeTeacherApprovesGateLessonIsNotCheckpointEligible() throws Exception {
        Long studentId = seedStudent("student_user");
        Long programId = seedProgram("Checkpoint Program Not Ready");
        Long lesson5 = seedLesson(programId, 5);
        seedEnrollment(studentId, programId);
        seedLessonProgress(studentId, lesson5, "WAITING_FOR_TEACHER");

        mockMvc.perform(get("/api/admin/checkpoint-eligible-students")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", programId.toString())
                        .param("blockNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testCheckpointPassCompletesGateLessonUnlocksNextBlockAndHidesEligibility() throws Exception {
        Fixture fixture = seedCheckpointReadyStudent("Pass");
        createCheckpointSession(fixture);
        Long sessionId = sessionId(fixture.enrollmentId());
        Long participantId = participantId(fixture.enrollmentId());

        mockMvc.perform(post("/api/evaluator/checkpoint-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "PASS",
                                  "comment": "Attended enough and ready"
                                }
                                """.formatted(participantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PASS"))
                .andExpect(jsonPath("$.participantId").value(participantId));

        assertEquals("COMPLETED", lessonProgressStatus(fixture.studentId(), fixture.lesson5()));
        assertEquals("VIDEO_IN_PROGRESS", lessonProgressStatus(fixture.studentId(), fixture.lesson6()));

        mockMvc.perform(get("/api/admin/checkpoint-sessions/" + sessionId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/admin/checkpoint-eligible-students")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", fixture.programId().toString())
                        .param("blockNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testCheckpointNotPassKeepsNextBlockLockedAndStudentCanBeGroupedAgain() throws Exception {
        Fixture fixture = seedCheckpointReadyStudent("Not Pass");
        createCheckpointSession(fixture);
        Long participantId = participantId(fixture.enrollmentId());

        mockMvc.perform(post("/api/evaluator/checkpoint-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "NOT_PASS",
                                  "comment": "Needs review"
                                }
                                """.formatted(participantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_PASS"));

        assertEquals("WAITING_FOR_CHECKPOINT", lessonProgressStatus(fixture.studentId(), fixture.lesson5()));
        assertEquals("LOCKED", lessonProgressStatus(fixture.studentId(), fixture.lesson6()));

        mockMvc.perform(get("/api/admin/checkpoint-eligible-students")
                        .header("Authorization", "Bearer admin-token")
                        .param("programId", fixture.programId().toString())
                        .param("blockNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testStudentCanViewCheckpointStatusAndEvaluatorCanOpenSessionDetail() throws Exception {
        Fixture fixture = seedCheckpointReadyStudent("Status");
        createCheckpointSession(fixture);
        Long sessionId = sessionId(fixture.enrollmentId());
        Long participantId = participantId(fixture.enrollmentId());

        mockMvc.perform(get("/api/student/checkpoint-status")
                        .header("Authorization", "Bearer student-token")
                        .param("lessonId", fixture.lesson5().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(fixture.lesson5()))
                .andExpect(jsonPath("$.lessonProgressStatus").value("WAITING_FOR_CHECKPOINT"))
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.participantId").value(participantId))
                .andExpect(jsonPath("$.scheduledAt").value("2030-01-15T13:00:00Z"))
                .andExpect(jsonPath("$.meetLink").value("https://meet.google.com/checkpoint"))
                .andExpect(jsonPath("$.evaluatorName").value("Evaluator eval_user"));

        mockMvc.perform(get("/api/evaluator/checkpoint-sessions/" + sessionId)
                        .header("Authorization", "Bearer evaluator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.participants[0].id").value(participantId));
    }

    @Test
    void testAdminCannotEditRemoveOrCancelSessionAfterResult() throws Exception {
        Fixture fixture = seedCheckpointReadyStudent("Immutable Result");
        createCheckpointSession(fixture);
        Long sessionId = sessionId(fixture.enrollmentId());
        Long participantId = participantId(fixture.enrollmentId());

        mockMvc.perform(post("/api/evaluator/checkpoint-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "NOT_PASS"
                                }
                                """.formatted(participantId)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/checkpoint-sessions/" + sessionId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetLink\":\"https://meet.google.com/cannot-edit\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only pending checkpoint sessions can be edited"));

        mockMvc.perform(delete("/api/admin/checkpoint-sessions/" + sessionId + "/participants/" + participantId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Checkpoint participant with a result cannot be removed"));

        mockMvc.perform(patch("/api/admin/checkpoint-sessions/" + sessionId + "/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Checkpoint session with results cannot be cancelled"));
    }

    @Test
    void testEvaluatorCannotSubmitResultForAnotherEvaluatorSession() throws Exception {
        Fixture fixture = seedCheckpointReadyStudent("Forbidden");
        Long otherEvaluatorId = seedEvaluator("other_eval");
        mockMvc.perform(post("/api/admin/checkpoint-sessions")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programId": %d,
                                  "blockNumber": 1,
                                  "evaluatorId": %d,
                                  "scheduledAt": "2030-01-15T13:00:00Z",
                                  "meetLink": "https://meet.google.com/other-eval",
                                  "participantEnrollmentIds": [%d]
                                }
                                """.formatted(fixture.programId(), otherEvaluatorId, fixture.enrollmentId())))
                .andExpect(status().isOk());
        Long participantId = participantId(fixture.enrollmentId());

        mockMvc.perform(post("/api/evaluator/checkpoint-results")
                        .header("Authorization", "Bearer evaluator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantId": %d,
                                  "result": "PASS"
                                }
                                """.formatted(participantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Checkpoint session does not belong to current evaluator"));
    }

    private void createCheckpointSession(Fixture fixture) throws Exception {
        mockMvc.perform(post("/api/admin/checkpoint-sessions")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programId": %d,
                                  "blockNumber": 1,
                                  "evaluatorId": %d,
                                  "scheduledAt": "2030-01-15T13:00:00Z",
                                  "meetLink": "https://meet.google.com/checkpoint",
                                  "participantEnrollmentIds": [%d]
                                }
                                """.formatted(fixture.programId(), fixture.evaluatorId(), fixture.enrollmentId())))
                .andExpect(status().isOk());
    }

    private Long participantId(Long enrollmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM checkpoint_participants WHERE enrollment_id = ?",
                Long.class,
                enrollmentId);
    }

    private Long sessionId(Long enrollmentId) {
        return jdbcTemplate.queryForObject("""
                SELECT session_id FROM checkpoint_participants
                WHERE enrollment_id = ?
                """, Long.class, enrollmentId);
    }

    private String lessonProgressStatus(Long studentId, Long lessonId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM student_lesson_progress
                WHERE student_id = ? AND lesson_id = ?
                """, String.class, studentId, lessonId);
    }

    private record Fixture(
            Long studentId,
            Long teacherId,
            Long evaluatorId,
            Long programId,
            Long enrollmentId,
            Long lesson5,
            Long lesson6
    ) {}
}
