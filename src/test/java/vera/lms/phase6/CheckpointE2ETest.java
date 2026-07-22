package vera.lms.phase6;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
