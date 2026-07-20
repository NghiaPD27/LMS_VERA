package vera.lms.phase2;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProgramLessonEnrollmentE2ETest extends BaseIntegrationTest {

    private Long seedProgram(String name) {
        jdbcTemplate.update("INSERT INTO programs (name, description) VALUES (?, ?)", name, "Desc");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedLesson(Long programId, String name, int lessonNumber, String status) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, ?)
                """, programId, name, lessonNumber, "Content", status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lessons WHERE program_id = ? AND lesson_number = ? AND status <> 'ARCHIVED'",
                Long.class, programId, lessonNumber);
    }

    private Long seedStudent(String username, String email) {
        Long studentId = createUser(username, email, "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "First", "Last", "123456");
        return studentId;
    }

    private void seedProgress(Long studentId, Long lessonId, String status) {
        jdbcTemplate.update("""
                INSERT INTO student_lesson_progress (student_id, lesson_id, status)
                VALUES (?, ?, ?)
                """, studentId, lessonId, status);
    }

    private Long seedEnrollment(Long studentId, Long programId, String status, Instant enrolledAt, Instant expiredAt) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (student_id, program_id, status, enrolled_at, expired_at)
                VALUES (?, ?, ?, ?, ?)
                """, studentId, programId, status, enrolledAt, expiredAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM enrollments WHERE student_id = ? AND program_id = ?",
                Long.class,
                studentId,
                programId);
    }

    @Test
    void testProgramCrudAndList() throws Exception {
        String payload = "{\"name\": \"English B1\", \"description\": \"Intermediate English\"}";
        mockMvc.perform(post("/api/programs")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("English B1"));

        Long programId = jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = 'English B1'", Long.class);

        mockMvc.perform(get("/api/programs")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").exists())
                .andExpect(jsonPath("$.totalElements").exists());

        mockMvc.perform(get("/api/programs/" + programId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("English B1"));

        mockMvc.perform(put("/api/programs/" + programId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"English B1 Plus\", \"description\": \"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("English B1 Plus"));

        mockMvc.perform(delete("/api/programs/" + programId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM programs WHERE id = ?", Integer.class, programId);
        assertEquals(0, count);
    }

    @Test
    void testLessonCreatePatchPublishAndHardDelete() throws Exception {
        Long programId = seedProgram("Lesson CRUD Program");

        mockMvc.perform(post("/api/programs/" + programId + "/lessons")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Lesson 1\", \"lessonNumber\": 1, \"content\": \"Intro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        Long lessonId = jdbcTemplate.queryForObject("SELECT id FROM lessons WHERE program_id = ?", Long.class, programId);

        mockMvc.perform(patch("/api/lessons/" + lessonId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Lesson 1 Updated\", \"lessonNumber\": 1, \"content\": \"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lesson 1 Updated"));

        mockMvc.perform(post("/api/lessons/" + lessonId + "/publish")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(delete("/api/lessons/" + lessonId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());

        Integer lessonCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons WHERE id = ?", Integer.class, lessonId);
        assertEquals(0, lessonCount);
    }

    @Test
    void testEnrollmentCreatesInitialProgressForPublishedLessonsOnly() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Enrollment Program");
        Long lesson1Id = seedLesson(programId, "Lesson 1", 1, "PUBLISHED");
        seedLesson(programId, "Lesson 2", 2, "DRAFT");

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ", \"programId\":" + programId + "}"))
                .andExpect(status().isCreated());

        Integer progressCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_lesson_progress WHERE student_id = ?",
                Integer.class, studentId);
        String firstStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM student_lesson_progress WHERE student_id = ? AND lesson_id = ?",
                String.class, studentId, lesson1Id);

        assertEquals(1, progressCount);
        assertEquals("VIDEO_IN_PROGRESS", firstStatus);
    }

    @Test
    void testStudentSeesPublishedLessonPathWithLockedLessons() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Visibility Program");
        seedLesson(programId, "Lesson 1", 1, "PUBLISHED");
        seedLesson(programId, "Lesson 2", 2, "PUBLISHED");
        seedLesson(programId, "Draft Lesson", 3, "DRAFT");

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ", \"programId\":" + programId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/programs/" + programId + "/lessons")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Lesson 1"))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].lessonProgressStatus").value("VIDEO_IN_PROGRESS"))
                .andExpect(jsonPath("$[0].locked").value(false))
                .andExpect(jsonPath("$[1].name").value("Lesson 2"))
                .andExpect(jsonPath("$[1].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[1].lessonProgressStatus").value("LOCKED"))
                .andExpect(jsonPath("$[1].locked").value(true));
    }

    @Test
    void testStudentCannotAccessLessonsAfterEnrollmentExpires() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Expired Course Program");
        Long lessonId = seedLesson(programId, "Expired Lesson", 1, "PUBLISHED");
        seedEnrollment(
                studentId,
                programId,
                "ACTIVE",
                Instant.now().minus(210, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));
        seedProgress(studentId, lessonId, "VIDEO_IN_PROGRESS");

        mockMvc.perform(get("/api/programs/" + programId + "/lessons")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Course enrollment is expired or unavailable"));
    }

    @Test
    void testAdminExtendsEnrollmentFromCurrentFutureExpiry() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Extend Future Program");
        Instant originalExpiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Long enrollmentId = seedEnrollment(
                studentId,
                programId,
                "ACTIVE",
                Instant.now().minus(30, ChronoUnit.DAYS),
                originalExpiry);

        mockMvc.perform(patch("/api/admin/enrollments/" + enrollmentId + "/extend")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"months\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(enrollmentId))
                .andExpect(jsonPath("$.expiredAt").exists());

        Instant newExpiry = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM enrollments WHERE id = ?",
                Timestamp.class,
                enrollmentId).toInstant();
        long daysDiff = ChronoUnit.DAYS.between(originalExpiry, newExpiry);
        assertTrue(daysDiff >= 58 && daysDiff <= 63);
    }

    @Test
    void testAdminExtendsExpiredEnrollmentFromNowWithoutChangingAccountExpiry() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Extend Expired Program");
        Instant legacyAccountExpiry = Instant.now().minus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update(
                "UPDATE account_access SET expired_at = ? WHERE user_id = ?",
                legacyAccountExpiry,
                studentId);
        Long enrollmentId = seedEnrollment(
                studentId,
                programId,
                "ACTIVE",
                Instant.now().minus(210, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/admin/enrollments/" + enrollmentId + "/extend")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"months\":1}"))
                .andExpect(status().isOk());

        Instant newExpiry = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM enrollments WHERE id = ?",
                Timestamp.class,
                enrollmentId).toInstant();
        Instant accountExpiry = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?",
                Timestamp.class,
                studentId).toInstant();

        long daysFromNow = ChronoUnit.DAYS.between(Instant.now(), newExpiry);
        assertTrue(daysFromNow >= 27 && daysFromNow <= 32);
        assertEquals(legacyAccountExpiry, accountExpiry.truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void testAdminRejectsInvalidEnrollmentExtensionMonths() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Invalid Extend Program");
        Long enrollmentId = seedEnrollment(
                studentId,
                programId,
                "ACTIVE",
                Instant.now(),
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/admin/enrollments/" + enrollmentId + "/extend")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"months\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testOneActiveEnrollmentRuleAndLifecycle() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long program1Id = seedProgram("Program 1");
        Long program2Id = seedProgram("Program 2");

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ", \"programId\":" + program1Id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ", \"programId\":" + program2Id + "}"))
                .andExpect(status().isConflict());

        Long enrollmentId = jdbcTemplate.queryForObject("SELECT id FROM enrollments WHERE student_id = ?", Long.class, studentId);
        mockMvc.perform(patch("/api/enrollments/" + enrollmentId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"COMPLETED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ", \"programId\":" + program2Id + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void testOnlyStudentCanBeEnrolled() throws Exception {
        Long teacherId = createUser("teacher_user", "teacher@vera.lms", "Password123", "TEACHER", true);
        createAccountAccess(teacherId, "ACTIVE", false);
        Long programId = seedProgram("Role Guard Program");

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + teacherId + ", \"programId\":" + programId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCompletedLessonNumberCannotChange() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Completed Guard Program");
        Long lessonId = seedLesson(programId, "Lesson 1", 1, "PUBLISHED");
        seedProgress(studentId, lessonId, "COMPLETED");

        mockMvc.perform(patch("/api/lessons/" + lessonId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Lesson 1\", \"lessonNumber\": 2, \"content\": \"Content\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void testLessonWithProgressIsArchivedAndNumberCanBeReused() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Archive Program");
        Long lessonId = seedLesson(programId, "Lesson 1", 1, "PUBLISHED");
        seedProgress(studentId, lessonId, "VIDEO_IN_PROGRESS");

        mockMvc.perform(delete("/api/lessons/" + lessonId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());

        String archivedStatus = jdbcTemplate.queryForObject("SELECT status FROM lessons WHERE id = ?", String.class, lessonId);
        assertEquals("ARCHIVED", archivedStatus);

        mockMvc.perform(post("/api/programs/" + programId + "/lessons")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Replacement\", \"lessonNumber\": 1, \"content\": \"New\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void testRbacRulesForPhase2AdminEndpoints() throws Exception {
        seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("RBAC Program");

        mockMvc.perform(post("/api/programs")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Forbidden\", \"description\": \"Desc\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/programs/" + programId + "/lessons")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Forbidden\", \"lessonNumber\": 1, \"content\": \"Nope\"}"))
                .andExpect(status().isForbidden());
    }
}
