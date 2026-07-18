package vera.lms.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vera.lms.BaseIntegrationTest;
import vera.lms.dtos.AuthDto.LoginResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AccountExpirationE2ETest extends BaseIntegrationTest {

    @Test
    void testFirstLoginInitializesFirstLoginAtWithoutAccountExpiry() throws Exception {
        Long userId = createUser("student_exp1", "exp1@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        Integer nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_access WHERE user_id = ? AND first_login_at IS NULL AND expired_at IS NULL",
                Integer.class,
                userId);
        assertEquals(1, nullCount);

        login("student_exp1", "Password123");

        Instant firstLoginAt = jdbcTemplate.queryForObject(
                "SELECT first_login_at FROM account_access WHERE user_id = ?",
                Instant.class,
                userId);

        assertNotNull(firstLoginAt);
        assertNull(jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?",
                java.sql.Timestamp.class,
                userId));
    }

    @Test
    void testSubsequentLoginDoesNotModifyFirstLoginOrAccountExpiry() throws Exception {
        Long userId = createUser("student_exp3", "exp3@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        login("student_exp3", "Password123");

        Instant initialFirstLoginAt = jdbcTemplate.queryForObject(
                "SELECT first_login_at FROM account_access WHERE user_id = ?",
                Instant.class,
                userId);

        login("student_exp3", "Password123");

        Instant subsequentFirstLoginAt = jdbcTemplate.queryForObject(
                "SELECT first_login_at FROM account_access WHERE user_id = ?",
                Instant.class,
                userId);

        assertEquals(initialFirstLoginAt, subsequentFirstLoginAt);
        assertNull(jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?",
                java.sql.Timestamp.class,
                userId));
    }

    @Test
    void testLegacyAccountExpiredAtDoesNotBlockLoginOrProfileAccess() throws Exception {
        Long userId = createUser("student_legacy_exp", "legacy_exp@vera.lms", "Password123", "STUDENT", true);
        jdbcTemplate.update("""
                INSERT INTO account_access (user_id, status, must_change_password, expired_at)
                VALUES (?, 'ACTIVE', false, ?)
                """, userId, Instant.now().minus(10, ChronoUnit.DAYS));

        MvcResult result = login("student_legacy_exp", "Password123");
        LoginResponse loginResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                LoginResponse.class);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM account_access WHERE user_id = ?",
                String.class,
                userId);
        assertEquals("ACTIVE", status);
    }

    @Test
    void testSuspendedAccountStatusDeniedAccess() throws Exception {
        Long userId = createUser("student_susp", "susp@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "SUSPENDED", false);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer mock-student-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testManuallyExpiredAccountStatusDeniedAccess() throws Exception {
        Long userId = createUser("student_exp_after", "after@vera.lms", "Password123", "STUDENT", true);
        jdbcTemplate.update("""
                INSERT INTO account_access (user_id, status, must_change_password, expired_at)
                VALUES (?, 'EXPIRED', false, ?)
                """, userId, Instant.now().minus(10, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer mock-student-token"))
                .andExpect(status().isForbidden());
    }

    private MvcResult login(String username, String password) throws Exception {
        String payload = "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
    }
}
