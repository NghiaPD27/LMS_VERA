package vera.lms.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase8AuthorizationE2ETest extends BaseIntegrationTest {

    private static final Instant SLOT_START = Instant.parse("2030-03-01T09:00:00Z");

    @Test
    void testStudentCannotAccessForeignEnrollmentScopedApis() throws Exception {
        seedStudent("student_user");
        Long otherStudentId = seedStudent("other_student");
        Long currentProgramId = seedProgram("Current Student Program");
        Long foreignProgramId = seedProgram("Foreign Student Program");
        Long foreignLessonId = seedLesson(foreignProgramId, 1);
        seedEnrollment(userId("student_user"), currentProgramId, "ACTIVE");
        Long foreignEnrollmentId = seedEnrollment(otherStudentId, foreignProgramId, "ACTIVE");
        seedLessonProgress(otherStudentId, foreignLessonId, "VIDEO_IN_PROGRESS");

        mockMvc.perform(get("/api/programs/" + foreignProgramId + "/lessons")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Course enrollment is expired or unavailable"));

        mockMvc.perform(get("/api/student/final-assessment-status")
                        .header("Authorization", "Bearer student-token")
                        .param("enrollmentId", foreignEnrollmentId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Enrollment does not belong to current student"));

        mockMvc.perform(get("/api/student/final-assessment-retake-payments")
                        .header("Authorization", "Bearer student-token")
                        .param("enrollmentId", foreignEnrollmentId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Enrollment does not belong to current student"));
    }

    @Test
    void testStudentCannotUseForeignQuizAttemptOrBookingIds() throws Exception {
        Long currentStudentId = seedStudent("student_user");
        Long otherStudentId = seedStudent("other_student");
        Long teacherId = seedTeacher("teacher_user");
        Long programId = seedProgram("Foreign IDs Program");
        Long lessonId = seedLesson(programId, 1);
        Long otherEnrollmentId = seedEnrollment(otherStudentId, programId, "ACTIVE");
        Long quizId = seedQuiz(lessonId);
        Long foreignAttemptId = seedQuizAttempt(otherStudentId, quizId);
        Long foreignBookingId = seedBooking(otherStudentId, teacherId, otherEnrollmentId, lessonId);
        seedEnrollment(currentStudentId, seedProgram("Current IDs Program"), "ACTIVE");

        mockMvc.perform(post("/api/quiz-attempts/" + foreignAttemptId + "/submit")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": 1, "selectedOptionId": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Quiz attempt does not belong to current student"));

        mockMvc.perform(patch("/api/student/bookings/" + foreignBookingId + "/cancel")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Teacher booking does not belong to current student"));
    }

    @Test
    void testStudentCannotGetPlaybackUrlForLockedLesson() throws Exception {
        Long studentId = seedStudent("student_user");
        Long programId = seedProgram("Locked Playback Program");
        Long lessonId = seedLesson(programId, 1);
        seedEnrollment(studentId, programId, "ACTIVE");
        seedLessonProgress(studentId, lessonId, "LOCKED");
        attachReadyVideo(lessonId);

        mockMvc.perform(get("/api/lessons/" + lessonId + "/video-playback")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Lesson is locked"));
    }

    @Test
    void testTeacherOnlySeesAssignedStudentsAndCannotManageAnotherTeachersAvailability() throws Exception {
        Long currentTeacherId = seedTeacher("teacher_user");
        Long otherTeacherId = seedTeacher("other_teacher");
        Long assignedStudentId = seedStudent("assigned_student");
        Long foreignStudentId = seedStudent("foreign_student");
        Long assignedEnrollmentId = seedEnrollment(assignedStudentId, seedProgram("Assigned Program"), "ACTIVE");
        Long foreignEnrollmentId = seedEnrollment(foreignStudentId, seedProgram("Foreign Assigned Program"), "ACTIVE");
        seedTeacherAssignment(assignedEnrollmentId, currentTeacherId);
        seedTeacherAssignment(foreignEnrollmentId, otherTeacherId);
        Long foreignAvailabilityId = seedAvailability(otherTeacherId);

        mockMvc.perform(get("/api/teacher/students")
                        .header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(assignedStudentId));

        mockMvc.perform(delete("/api/teacher/availability/" + foreignAvailabilityId)
                        .header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Teacher availability does not belong to current teacher"));
    }

    @Test
    void testEvaluatorCannotOpenSessionsAssignedToAnotherEvaluator() throws Exception {
        seedEvaluator("eval_user");
        Long otherEvaluatorId = seedEvaluator("other_eval");
        Long programId = seedProgram("Evaluator Scope Program");
        Long checkpointId = seedCheckpoint(programId);
        Long checkpointSessionId = seedCheckpointSession(checkpointId, otherEvaluatorId);
        Long finalSessionId = seedFinalAssessmentSession(programId, otherEvaluatorId);

        mockMvc.perform(get("/api/evaluator/checkpoint-sessions/" + checkpointSessionId)
                        .header("Authorization", "Bearer evaluator-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Checkpoint session does not belong to current evaluator"));

        mockMvc.perform(get("/api/evaluator/final-assessment-sessions/" + finalSessionId)
                        .header("Authorization", "Bearer evaluator-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Final assessment session does not belong to current evaluator"));
    }

    @Test
    void testAdminCanManageAcrossStudentTeacherEvaluatorScopes() throws Exception {
        Long studentId = seedStudent("student_user");
        Long teacherId = seedTeacher("teacher_user");
        Long evaluatorId = seedEvaluator("eval_user");
        Long programId = seedProgram("Admin Scope Program");
        Long enrollmentId = seedEnrollment(studentId, programId, "ACTIVE");
        seedTeacherAssignment(enrollmentId, teacherId);
        Long finalSessionId = seedFinalAssessmentSession(programId, evaluatorId);

        mockMvc.perform(get("/api/admin/enrollments")
                        .header("Authorization", "Bearer admin-token")
                        .param("studentId", studentId.toString())
                        .param("programId", programId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(enrollmentId));

        mockMvc.perform(get("/api/admin/checkpoint-sessions")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/final-assessment-sessions/" + finalSessionId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(finalSessionId));
    }

    private Long seedStudent(String username) {
        Long userId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);
        createStudentProfile(userId, "Security", username, "0901234567");
        return userId;
    }

    private Long seedTeacher(String username) {
        Long userId = createUser(username, username + "@teacher.test", "Password123", "TEACHER", true);
        createAccountAccess(userId, "ACTIVE", false);
        createTeacherProfile(userId, "Teacher", username, "0907654321", "Security teacher");
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
                """, name, "Phase 8 security program", 1800000, 250000, "VND", "PUBLISHED");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedLesson(Long programId, int lessonNumber) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, 'PUBLISHED')
                """, programId, "Security Lesson " + lessonNumber, lessonNumber, "Lesson content");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lessons WHERE program_id = ? AND lesson_number = ?",
                Long.class,
                programId,
                lessonNumber);
    }

    private Long seedEnrollment(Long studentId, Long programId, String status) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (student_id, program_id, status, enrolled_at, expired_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                studentId,
                programId,
                status,
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

    private void attachReadyVideo(Long lessonId) {
        jdbcTemplate.update("""
                INSERT INTO lesson_videos (lesson_id, bunny_video_id, library_id, duration_seconds, status)
                VALUES (?, ?, ?, ?, 'READY')
                """, lessonId, "security-video-" + lessonId, "library-1", 600);
    }

    private Long seedQuiz(Long lessonId) {
        jdbcTemplate.update("INSERT INTO quizzes (lesson_id, title) VALUES (?, ?)", lessonId, "Security Quiz");
        return jdbcTemplate.queryForObject("SELECT id FROM quizzes WHERE lesson_id = ?", Long.class, lessonId);
    }

    private Long seedQuizAttempt(Long studentId, Long quizId) {
        jdbcTemplate.update("""
                INSERT INTO quiz_attempts (quiz_id, student_id, attempt_number, submitted)
                VALUES (?, ?, 1, false)
                """, quizId, studentId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM quiz_attempts WHERE quiz_id = ? AND student_id = ?",
                Long.class,
                quizId,
                studentId);
    }

    private Long seedAvailability(Long teacherId) {
        jdbcTemplate.update("""
                INSERT INTO teacher_availability (teacher_id, start_at, end_at)
                VALUES (?, ?, ?)
                """, teacherId, SLOT_START, SLOT_START.plus(1, ChronoUnit.HOURS));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM teacher_availability WHERE teacher_id = ?",
                Long.class,
                teacherId);
    }

    private Long seedBooking(Long studentId, Long teacherId, Long enrollmentId, Long lessonId) {
        Long availabilityId = seedAvailability(teacherId);
        jdbcTemplate.update("""
                INSERT INTO teacher_bookings
                    (student_id, teacher_id, enrollment_id, lesson_id, availability_id, start_at, end_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'BOOKED')
                """,
                studentId,
                teacherId,
                enrollmentId,
                lessonId,
                availabilityId,
                SLOT_START,
                SLOT_START.plus(1, ChronoUnit.HOURS));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM teacher_bookings WHERE student_id = ? AND lesson_id = ?",
                Long.class,
                studentId,
                lessonId);
    }

    private void seedTeacherAssignment(Long enrollmentId, Long teacherId) {
        jdbcTemplate.update("""
                INSERT INTO student_teacher_assignments (enrollment_id, teacher_id, assigned_at)
                VALUES (?, ?, ?)
                """, enrollmentId, teacherId, Instant.now());
    }

    private Long seedCheckpoint(Long programId) {
        jdbcTemplate.update("""
                INSERT INTO checkpoints (program_id, block_number, start_lesson_number, gate_lesson_number, next_lesson_number)
                VALUES (?, 1, 1, 5, 6)
                """, programId);
        return jdbcTemplate.queryForObject("SELECT id FROM checkpoints WHERE program_id = ?", Long.class, programId);
    }

    private Long seedCheckpointSession(Long checkpointId, Long evaluatorId) {
        jdbcTemplate.update("""
                INSERT INTO checkpoint_sessions (checkpoint_id, evaluator_id, scheduled_at, meet_link, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, checkpointId, evaluatorId, SLOT_START, "https://meet.google.com/other-checkpoint");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM checkpoint_sessions WHERE checkpoint_id = ?",
                Long.class,
                checkpointId);
    }

    private Long seedFinalAssessmentSession(Long programId, Long evaluatorId) {
        jdbcTemplate.update("""
                INSERT INTO final_assessment_sessions (program_id, evaluator_id, scheduled_at, meet_link, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, programId, evaluatorId, SLOT_START, "https://meet.google.com/other-final");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM final_assessment_sessions WHERE program_id = ? AND evaluator_id = ?",
                Long.class,
                programId,
                evaluatorId);
    }
}
