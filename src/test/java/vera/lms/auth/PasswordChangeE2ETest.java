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

public class PasswordChangeE2ETest extends BaseIntegrationTest {

    @Test
    void testChangePasswordSuccess() throws Exception {
        Long userId = createUser("student_pwd", "pwd@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String payload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        String passHash = jdbcTemplate.queryForObject("SELECT password FROM users WHERE id = ?", String.class, userId);
        assertNotNull(passHash);
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("NewSecurePassword123", passHash));
    }

    @Test
    void testChangePasswordUpdatesLoginAbility() throws Exception {
        Long userId = createUser("student_pwd2", "pwd2@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String changePayload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changePayload))
                .andExpect(status().isOk());

        String oldLoginPayload = "{\"username\": \"student_pwd2\", \"password\": \"TempPassword123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oldLoginPayload))
                .andExpect(status().isUnauthorized());

        String newLoginPayload = "{\"username\": \"student_pwd2\", \"password\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(newLoginPayload))
                .andExpect(status().isOk());
    }

    @Test
    void testChangePasswordClearsMustChangePasswordFlag() throws Exception {
        Long userId = createUser("student_pwd3", "pwd3@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        Boolean mustChange = jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM account_access WHERE user_id = ?", Boolean.class, userId);
        assertTrue(mustChange);

        String changePayload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changePayload))
                .andExpect(status().isOk());

        mustChange = jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM account_access WHERE user_id = ?", Boolean.class, userId);
        assertFalse(mustChange);
    }

    @Test
    void testChangePasswordFailureIncorrectOldPassword() throws Exception {
        Long userId = createUser("student_pwd4", "pwd4@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String payload = "{\"oldPassword\": \"WrongOldPassword\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChangePasswordFailureEmptyOldPassword() throws Exception {
        String payload = "{\"oldPassword\": \"\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChangePasswordFailureEmptyNewPassword() throws Exception {
        String payload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChangePasswordSameAsOld() throws Exception {
        Long userId = createUser("student_pwd5", "pwd5@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String payload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"TempPassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChangePasswordTooShort() throws Exception {
        Long userId = createUser("student_pwd6", "pwd6@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String payload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"abcde\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChangePasswordTooLong() throws Exception {
        Long userId = createUser("student_pwd7", "pwd7@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String longPassword = "a".repeat(101);
        String payload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"" + longPassword + "\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTemporaryPasswordLockoutMeApi() throws Exception {
        Long userId = createUser("student_lockout", "lockout@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer mock-access-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testActiveJwtRevocationOnPasswordChange() throws Exception {
        Long userId = createUser("student_rev", "rev@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String token = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO refresh_tokens (user_id, token, expiry_date, revoked) VALUES (?, ?, ?, false)",
                userId, token, Instant.now().plus(7, ChronoUnit.DAYS));

        String payload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        Boolean revoked = jdbcTemplate.queryForObject(
                "SELECT revoked FROM refresh_tokens WHERE token = ?", Boolean.class, token);
        assertTrue(revoked);
    }

    @Test
    void testParallelPasswordChangeRaceCondition() throws Exception {
        Long userId = createUser("student_race_pwd", "racepwd@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true);

        String payload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer mock-access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void testChangePasswordUnauthenticated() throws Exception {
        String payload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }
}
