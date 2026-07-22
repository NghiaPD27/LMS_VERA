package vera.lms.phase25;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseAutoEnrollmentE2ETest extends BaseIntegrationTest {

    private Long seedStudent(String username) {
        Long studentId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "Public", "Buyer", "0901234567");
        return studentId;
    }

    private Long seedProgram(String name, String salesStatus) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, currency, sales_status)
                VALUES (?, ?, ?, ?, ?)
                """, name, "Purchase program", 1800000, "VND", salesStatus);
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private void seedLesson(Long programId, int lessonNumber, String name) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, 'PUBLISHED')
                """, programId, name, lessonNumber, "Lesson content");
    }

    @Test
    void testStudentCreatesPendingPurchaseForPublishedProgram() throws Exception {
        seedStudent("purchase_student");
        Long programId = seedProgram("Public Purchase English", "PUBLISHED");

        mockMvc.perform(post("/api/student/purchases")
                        .header("Authorization", "Bearer mock-student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":" + programId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programId").value(programId))
                .andExpect(jsonPath("$.programName").value("Public Purchase English"))
                .andExpect(jsonPath("$.amount").value(1800000))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.enrollmentId").doesNotExist());

        Integer enrollmentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enrollments", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, enrollmentCount);

        mockMvc.perform(get("/api/student/purchases")
                        .header("Authorization", "Bearer mock-student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void testStudentCannotPurchaseUnpublishedProgram() throws Exception {
        seedStudent("purchase_draft_student");
        Long programId = seedProgram("Draft Purchase English", "DRAFT");

        mockMvc.perform(post("/api/student/purchases")
                        .header("Authorization", "Bearer mock-student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":" + programId + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testAdminGetsPurchaseDetailUpdatesStatusAndReadsEvents() throws Exception {
        seedStudent("purchase_cancel_student");
        Long programId = seedProgram("Cancel Purchase English", "PUBLISHED");

        mockMvc.perform(post("/api/student/purchases")
                        .header("Authorization", "Bearer mock-student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":" + programId + "}"))
                .andExpect(status().isCreated());
        Long purchaseId = jdbcTemplate.queryForObject("SELECT id FROM course_purchases", Long.class);

        mockMvc.perform(get("/api/admin/purchases/" + purchaseId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(purchaseId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(patch("/api/admin/purchases/" + purchaseId + "/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"note\":\"Wrong transfer content\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.adminNote").value("Wrong transfer content"));

        mockMvc.perform(get("/api/admin/purchases/" + purchaseId + "/events")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].newStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[0].note").value("Wrong transfer content"));
    }

    @Test
    void testAdminMarkPaidAutoEnrollsStudentForSixMonths() throws Exception {
        Long studentId = seedStudent("purchase_paid_student");
        Long programId = seedProgram("Paid Purchase English", "PUBLISHED");
        seedLesson(programId, 1, "Lesson One");
        seedLesson(programId, 2, "Lesson Two");

        mockMvc.perform(post("/api/student/purchases")
                        .header("Authorization", "Bearer mock-student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":" + programId + "}"))
                .andExpect(status().isCreated());

        Long purchaseId = jdbcTemplate.queryForObject("SELECT id FROM course_purchases", Long.class);

        mockMvc.perform(post("/api/admin/purchases/" + purchaseId + "/mark-paid")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(purchaseId))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.enrollmentId").exists())
                .andExpect(jsonPath("$.paidAt").exists());

        Long enrollmentId = jdbcTemplate.queryForObject(
                "SELECT enrollment_id FROM course_purchases WHERE id = ?",
                Long.class,
                purchaseId);
        assertNotNull(enrollmentId);

        Instant enrolledAt = jdbcTemplate.queryForObject(
                "SELECT enrolled_at FROM enrollments WHERE id = ?",
                Timestamp.class,
                enrollmentId).toInstant();
        Instant expiredAt = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM enrollments WHERE id = ?",
                Timestamp.class,
                enrollmentId).toInstant();
        long days = ChronoUnit.DAYS.between(enrolledAt, expiredAt);
        assertTrue(days >= 180 && days <= 185);
        assertNull(jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?",
                Timestamp.class,
                studentId));

        Integer progressCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_lesson_progress WHERE student_id = ?",
                Integer.class,
                studentId);
        org.junit.jupiter.api.Assertions.assertEquals(2, progressCount);

        String firstLessonStatus = jdbcTemplate.queryForObject("""
                SELECT slp.status
                FROM student_lesson_progress slp
                JOIN lessons l ON l.id = slp.lesson_id
                WHERE slp.student_id = ? AND l.lesson_number = 1
                """, String.class, studentId);
        org.junit.jupiter.api.Assertions.assertEquals("VIDEO_IN_PROGRESS", firstLessonStatus);

        mockMvc.perform(get("/api/admin/purchases")
                        .header("Authorization", "Bearer admin-token")
                        .param("studentId", studentId.toString())
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(purchaseId))
                .andExpect(jsonPath("$.content[0].enrollmentId").value(enrollmentId));
    }

    @Test
    void testMarkPaidIsIdempotent() throws Exception {
        Long studentId = seedStudent("purchase_idempotent_student");
        Long programId = seedProgram("Idempotent Purchase English", "PUBLISHED");
        seedLesson(programId, 1, "Idempotent Lesson One");
        seedLesson(programId, 2, "Idempotent Lesson Two");

        mockMvc.perform(post("/api/student/purchases")
                        .header("Authorization", "Bearer mock-student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":" + programId + "}"))
                .andExpect(status().isCreated());

        Long purchaseId = jdbcTemplate.queryForObject("SELECT id FROM course_purchases", Long.class);

        mockMvc.perform(post("/api/admin/purchases/" + purchaseId + "/mark-paid")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/purchases/" + purchaseId + "/mark-paid")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        Integer enrollmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE student_id = ? AND status = 'ACTIVE'",
                Integer.class,
                studentId);
        Integer progressCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_lesson_progress WHERE student_id = ?",
                Integer.class,
                studentId);

        org.junit.jupiter.api.Assertions.assertEquals(1, enrollmentCount);
        org.junit.jupiter.api.Assertions.assertEquals(2, progressCount);
    }
}
