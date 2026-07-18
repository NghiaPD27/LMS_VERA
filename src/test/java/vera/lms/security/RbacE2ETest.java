package vera.lms.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class RbacE2ETest extends BaseIntegrationTest {

    @Test
    void testUnauthenticatedProfileAccessDenied() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUnauthenticatedAdminAccessDenied() throws Exception {
        String payload = "{\"username\": \"student_anon\", \"email\": \"anon@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\"}";
        mockMvc.perform(post("/api/admin/students")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testStudentCannotCreateOtherStudents() throws Exception {
        createUser("student_user", "student@vera.lms", "Password123", "STUDENT", true);

        String payload = "{\"username\": \"student_another\", \"email\": \"another@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer student-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void testTeacherCannotAccessAdminEndPoints() throws Exception {
        createUser("teacher_user", "teacher@vera.lms", "Password123", "TEACHER", true);
        String payload = "{\"username\": \"student_another\", \"email\": \"another@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer teacher-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void testEvaluatorCannotAccessAdminEndPoints() throws Exception {
        createUser("eval_user", "eval@vera.lms", "Password123", "EVALUATOR", true);
        String payload = "{\"username\": \"student_another\", \"email\": \"another@vera.lms\", \"password\": \"TempPassword123\", \"firstName\": \"John\", \"lastName\": \"Doe\"}";
        mockMvc.perform(post("/api/admin/students")
                .header("Authorization", "Bearer evaluator-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAccessRequestWithMalformedHeaderToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer not-real-header-format"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccessRequestWithValidFormatJwtButInvalidSignature() throws Exception {
        String invalidSignatureToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6OTQ2Njg0ODAwfQ.invalid_sig";
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + invalidSignatureToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAdminSelfRoleDemotionBlocked() throws Exception {
        String payload = "{\"enabled\": false}";
        mockMvc.perform(patch("/api/admin/users/1")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());

        Boolean enabled = jdbcTemplate.queryForObject("SELECT enabled FROM users WHERE id = 1", Boolean.class);
        assertTrue(enabled);
    }

    @Test
    void testAdminUpdatesNonExistentUserId() throws Exception {
        String payload = "{\"email\": \"newemail@vera.lms\"}";
        mockMvc.perform(patch("/api/admin/users/99999")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void testAdminExtendsNonExistentEnrollmentId() throws Exception {
        String payload = "{\"months\": 6}";
        mockMvc.perform(patch("/api/admin/enrollments/99999/extend")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void testAdministrativeLockoutOfLoggedInStudentSession() throws Exception {
        Long userId = createUser("student_lock", "lock@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer mock-student-token"))
                .andExpect(status().isOk());

        String payload = "{\"status\": \"SUSPENDED\"}";
        mockMvc.perform(patch("/api/admin/users/" + userId)
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer mock-student-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteUserCascadesToStudentProfile() throws Exception {
        Long userId = createUser("student_del", "del@vera.lms", "Password123", "STUDENT", true);
        createStudentProfile(userId, "John", "Del", "1234");
        createAccountAccess(userId, "ACTIVE", false);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_profiles WHERE user_id = ?", Integer.class, userId);
        assertEquals(0, profileCount);

        Integer accessCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_access WHERE user_id = ?", Integer.class, userId);
        assertEquals(0, accessCount);
    }

    @Test
    void testDeleteUserCascadesToTeacherProfile() throws Exception {
        Long userId = createUser("teacher_del", "tdel@vera.lms", "Password123", "TEACHER", true);
        createTeacherProfile(userId, "Alice", "Del", "1234", "bio");

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teacher_profiles WHERE user_id = ?", Integer.class, userId);
        assertEquals(0, profileCount);
    }

    @Test
    void testDeleteUserCascadesToEvaluatorProfile() throws Exception {
        Long userId = createUser("eval_del", "edel@vera.lms", "Password123", "EVALUATOR", true);
        createEvaluatorProfile(userId, "Robert", "Del", "1234");

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluator_profiles WHERE user_id = ?", Integer.class, userId);
        assertEquals(0, profileCount);
    }
}
