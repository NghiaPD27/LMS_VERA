package vera.lms.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class AdminProfileCreationE2ETest extends BaseIntegrationTest {

    @Test
    void testAdminCreatesStudentProfile() throws Exception {
        String payload = "{\"username\": \"student_jdoe\", \"email\": \"jdoe@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\", \"phoneNumber\": \"0912345678\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.username").value("student_jdoe"))
                .andExpect(jsonPath("$.email").value("jdoe@vera.lms"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.phoneNumber").value("0912345678"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'student_jdoe'", Integer.class);
        assertEquals(1, userCount);

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_profiles WHERE first_name = 'John' AND last_name = 'Doe'", Integer.class);
        assertEquals(1, profileCount);
    }

    @Test
    void testAdminCreatesTeacherProfile() throws Exception {
        String payload = "{\"username\": \"teacher_asmith\", \"email\": \"asmith@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"Alice\", \"lastName\": \"Smith\", \"phoneNumber\": \"0987654321\", \"bio\": \"Computer Science teacher\"}";
        mockMvc.perform(post("/api/admin/teachers")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.bio").value("Computer Science teacher"));

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teacher_profiles WHERE first_name = 'Alice'", Integer.class);
        assertEquals(1, profileCount);
    }

    @Test
    void testAdminCreatesEvaluatorProfile() throws Exception {
        String payload = "{\"username\": \"eval_rgreen\", \"email\": \"rgreen@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"Robert\", \"lastName\": \"Green\", \"phoneNumber\": \"0977665544\"}";
        mockMvc.perform(post("/api/admin/evaluators")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated());

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluator_profiles WHERE first_name = 'Robert'", Integer.class);
        assertEquals(1, profileCount);
    }

    @Test
    void testCreatedStudentProfileDefaultsVerified() throws Exception {
        String payload = "{\"username\": \"student_def\", \"email\": \"def@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Def\", \"phoneNumber\": \"0912345678\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated());

        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'student_def'", Long.class);
        
        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, userId);
        assertTrue(enabled);

        Boolean mustChange = jdbcTemplate.queryForObject("SELECT must_change_password FROM account_access WHERE user_id = ?", Boolean.class, userId);
        assertTrue(mustChange);

        String status = jdbcTemplate.queryForObject("SELECT status FROM account_access WHERE user_id = ?", String.class, userId);
        assertEquals("ACTIVE", status);

        String roleName = jdbcTemplate.queryForObject(
                "SELECT r.name FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?", String.class, userId);
        assertEquals("STUDENT", roleName);
    }

    @Test
    void testCreatedTeacherProfileDefaultsVerified() throws Exception {
        String payload = "{\"username\": \"teacher_def\", \"email\": \"tdef@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"Alice\", \"lastName\": \"Def\", \"phoneNumber\": \"0987654321\"}";
        mockMvc.perform(post("/api/admin/teachers")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated());

        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'teacher_def'", Long.class);
        
        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, userId);
        assertTrue(enabled);

        String roleName = jdbcTemplate.queryForObject(
                "SELECT r.name FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?", String.class, userId);
        assertEquals("TEACHER", roleName);
    }

    @Test
    void testCreatedEvaluatorProfileDefaultsVerified() throws Exception {
        String payload = "{\"username\": \"eval_def\", \"email\": \"edef@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"Robert\", \"lastName\": \"Def\", \"phoneNumber\": \"0977665544\"}";
        mockMvc.perform(post("/api/admin/evaluators")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated());

        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'eval_def'", Long.class);
        
        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, userId);
        assertTrue(enabled);

        String roleName = jdbcTemplate.queryForObject(
                "SELECT r.name FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?", String.class, userId);
        assertEquals("EVALUATOR", roleName);
    }

    @Test
    void testCreateUserDuplicateUsername() throws Exception {
        createUser("student_dup", "dup1@vera.lms", "Password123", "STUDENT", true);

        String payload = "{\"username\": \"student_dup\", \"email\": \"dup2@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\", \"phoneNumber\": \"0912345678\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void testCreateUserDuplicateEmail() throws Exception {
        createUser("student_dup2", "dup@vera.lms", "Password123", "STUDENT", true);

        String payload = "{\"username\": \"student_uniq\", \"email\": \"dup@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\", \"phoneNumber\": \"0912345678\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void testCreateUserInvalidEmail() throws Exception {
        String payload = "{\"username\": \"student_uniq\", \"email\": \"notanemail\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\", \"phoneNumber\": \"0912345678\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUserMissingFields() throws Exception {
        String payload = "{\"username\": \"\", \"email\": \"\", \"password\": \"\", \"firstName\": \"\", \"lastName\": \"\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUserNameTooLong() throws Exception {
        String longName = "a".repeat(51);
        String payload = "{\"username\": \"student_long\", \"email\": \"long@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"" + longName + "\", \"lastName\": \"Doe\", \"phoneNumber\": \"0912345678\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateUserInvalidPhone() throws Exception {
        String payload = "{\"username\": \"student_phone\", \"email\": \"phone@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\", \"phoneNumber\": \"not-a-number\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testOnboardingAndExpirationLifecycle() throws Exception {
        String payload = "{\"username\": \"student_life\", \"email\": \"life@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Life\", \"phoneNumber\": \"0912345678\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated());

        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'student_life'", Long.class);

        Boolean mustChange = jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM account_access WHERE user_id = ?", Boolean.class, userId);
        assertTrue(mustChange);

        Integer nullCheck = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_access WHERE user_id = ? AND first_login_at IS NULL AND expired_at IS NULL", 
                Integer.class, userId);
        assertEquals(1, nullCheck);

        String loginPayload = "{\"username\": \"student_life\", \"password\": \"TempPassword123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isOk());

        Integer initializedCheck = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_access WHERE user_id = ? AND first_login_at IS NOT NULL AND expired_at IS NULL",
                Integer.class, userId);
        assertEquals(1, initializedCheck);

        String changePayload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-student-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changePayload))
                .andExpect(status().isOk());

        mustChange = jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM account_access WHERE user_id = ?", Boolean.class, userId);
        assertFalse(mustChange);
    }

    @Test
    void testBulkOnboarding() throws Exception {
        for (int i = 0; i < 20; i++) {
            String payload = String.format(
                    "{\"username\": \"student_bulk_%d\", \"email\": \"bulk_%d@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"First%d\", \"lastName\": \"Last%d\", \"phoneNumber\": \"0912345678\"}",
                    i, i, i, i);
            mockMvc.perform(post("/api/admin/students")
                    .header("Authorization", "Bearer admin-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isCreated());
        }

        Integer bulkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username LIKE 'student_bulk_%'", Integer.class);
        assertEquals(20, bulkCount);
    }
}
