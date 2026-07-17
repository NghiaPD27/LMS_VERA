package vera.lms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class LoginE2ETest extends BaseIntegrationTest {

    @Test
    void testAdminLoginSuccess() throws Exception {
        String payload = "{\"username\": \"admin\", \"password\": \"AdminPassword123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void testStudentLoginSuccess() throws Exception {
        Long userId = createUser("student1", "student1@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String payload = "{\"username\": \"student1\", \"password\": \"TempPassword123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void testTeacherLoginSuccess() throws Exception {
        Long userId = createUser("teacher1", "teacher1@vera.lms", "TempPassword123", "TEACHER", true);
        createAccountAccess(userId, "ACTIVE", true);

        String payload = "{\"username\": \"teacher1\", \"password\": \"TempPassword123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void testEvaluatorLoginSuccess() throws Exception {
        Long userId = createUser("evaluator1", "evaluator1@vera.lms", "TempPassword123", "EVALUATOR", true);
        createAccountAccess(userId, "ACTIVE", true);

        String payload = "{\"username\": \"evaluator1\", \"password\": \"TempPassword123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void testLoginFailureInvalidPassword() throws Exception {
        String payload = "{\"username\": \"admin\", \"password\": \"WrongPassword\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLoginUsernameNotFound() throws Exception {
        String payload = "{\"username\": \"noone_here\", \"password\": \"Password123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLoginEmptyUsername() throws Exception {
        String payload = "{\"username\": \"\", \"password\": \"Password123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLoginEmptyPassword() throws Exception {
        String payload = "{\"username\": \"admin\", \"password\": \"\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLoginDisabledUserAccount() throws Exception {
        Long userId = createUser("disabled_user", "disabled@vera.lms", "Password123", "STUDENT", false);
        createAccountAccess(userId, "ACTIVE", false);

        String payload = "{\"username\": \"disabled_user\", \"password\": \"Password123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void testLoginSqlInjectionPayload() throws Exception {
        String payload = "{\"username\": \"' OR 1=1 --\", \"password\": \"password\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAuthenticationFilterRejectsExpiredAccessToken() throws Exception {
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6OTQ2Njg0ODAwfQ.dummy_signature";
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testTokenRefreshExpiredRefreshToken() throws Exception {
        String expiredRefreshToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6OTQ2Njg0ODAwfQ.dummy_signature";
        String payload = "{\"refreshToken\": \"" + expiredRefreshToken + "\"}";
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testTokenRefreshMalformedRefreshToken() throws Exception {
        String payload = "{\"refreshToken\": \"not-a-jwt-structure-at-all\"}";
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSessionExpirationBlocksRefreshTokenFlow() throws Exception {
        Long userId = createUser("student_exp", "student_exp@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "EXPIRED", false);

        String refreshToken = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO refresh_tokens (user_id, token, expiry_date, revoked) VALUES (?, ?, ?, false)",
                userId, refreshToken, Instant.now().plus(7, ChronoUnit.DAYS));

        String payload = "{\"refreshToken\": \"" + refreshToken + "\"}";
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void testConcurrentSessionManagement() throws Exception {
        Long userId = createUser("student_multi", "multi@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        String payload = "{\"username\": \"student_multi\", \"password\": \"Password123\"}";
        
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        Integer tokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked = false", Integer.class, userId);
        assertNotNull(tokenCount);
        assertTrue(tokenCount >= 2);
    }

    @Test
    void testTokenRefreshRaceCondition() throws Exception {
        Long userId = createUser("student_race", "race@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        String token = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO refresh_tokens (user_id, token, expiry_date, revoked) VALUES (?, ?, ?, false)",
                userId, token, Instant.now().plus(1, ChronoUnit.HOURS));

        String payload = "{\"refreshToken\": \"" + token + "\"}";
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void testCleanSessionLogoutCleanup() throws Exception {
        Long userId = createUser("student_logout", "logout@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        String token = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO refresh_tokens (user_id, token, expiry_date, revoked) VALUES (?, ?, ?, false)",
                userId, token, Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + token + "\"}"))
                .andExpect(status().isOk());

        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked = false", Integer.class, userId);
        assertEquals(0, activeTokens);
    }
}
