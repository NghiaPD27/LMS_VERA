package vera.lms.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class AccountExpirationE2ETest extends BaseIntegrationTest {

    @Test
    void testFirstLoginInitializesFirstLoginAt() throws Exception {
        Long userId = createUser("student_exp1", "exp1@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        Integer nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_access WHERE user_id = ? AND first_login_at IS NULL", Integer.class, userId);
        assertEquals(1, nullCount);

        String payload = "{\"username\": \"student_exp1\", \"password\": \"Password123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        Instant firstLoginAt = jdbcTemplate.queryForObject(
                "SELECT first_login_at FROM account_access WHERE user_id = ?", Instant.class, userId);
        assertNotNull(firstLoginAt);
    }

    @Test
    void testFirstLoginSetsExpirationDateTo6Months() throws Exception {
        Long userId = createUser("student_exp2", "exp2@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        String payload = "{\"username\": \"student_exp2\", \"password\": \"Password123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        Instant firstLoginAt = jdbcTemplate.queryForObject(
                "SELECT first_login_at FROM account_access WHERE user_id = ?", Instant.class, userId);
        Instant expiredAt = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?", Instant.class, userId);

        assertNotNull(firstLoginAt);
        assertNotNull(expiredAt);
        long secondsDiff = Math.abs(ChronoUnit.SECONDS.between(firstLoginAt.plus(182, ChronoUnit.DAYS), expiredAt));
        assertTrue(secondsDiff < 86400 * 5);
    }

    @Test
    void testSubsequentLoginDoesNotModifyExpiryDates() throws Exception {
        Long userId = createUser("student_exp3", "exp3@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        String payload = "{\"username\": \"student_exp3\", \"password\": \"Password123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        Instant initialFirstLoginAt = jdbcTemplate.queryForObject(
                "SELECT first_login_at FROM account_access WHERE user_id = ?", Instant.class, userId);
        Instant initialExpiredAt = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?", Instant.class, userId);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        Instant subsequentFirstLoginAt = jdbcTemplate.queryForObject(
                "SELECT first_login_at FROM account_access WHERE user_id = ?", Instant.class, userId);
        Instant subsequentExpiredAt = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?", Instant.class, userId);

        assertEquals(initialFirstLoginAt, subsequentFirstLoginAt);
        assertEquals(initialExpiredAt, subsequentExpiredAt);
    }

    @Test
    void testAdminManuallyExtendsAccountExpiration() throws Exception {
        Long userId = createUser("student_exp4", "exp4@vera.lms", "Password123", "STUDENT", true);
        Instant originalExpiry = Instant.now().plus(180, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("INSERT INTO account_access (user_id, status, must_change_password, expired_at) VALUES (?, 'ACTIVE', false, ?)",
                userId, originalExpiry);

        String payload = "{\"months\": 3}";
        mockMvc.perform(patch("/api/admin/users/" + userId + "/extend")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredAt").exists());

        Instant newExpiry = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?", Instant.class, userId);
        assertNotNull(newExpiry);
        long daysDiff = ChronoUnit.DAYS.between(originalExpiry, newExpiry);
        assertTrue(daysDiff >= 89 && daysDiff <= 93);
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
    void testAccessApiAtExactSecondOfExpiry() throws Exception {
        Long userId = createUser("student_exp_exact", "exact@vera.lms", "Password123", "STUDENT", true);
        jdbcTemplate.update("INSERT INTO account_access (user_id, status, must_change_password, expired_at) VALUES (?, 'ACTIVE', false, ?)",
                userId, Instant.now());

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer mock-student-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminExtendsExpiryWithNegativeMonths() throws Exception {
        Long userId = createUser("student_exp5", "exp5@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        String payload = "{\"months\": -1}";
        mockMvc.perform(patch("/api/admin/users/" + userId + "/extend")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAdminExtendsExpiryWithZeroMonths() throws Exception {
        Long userId = createUser("student_exp6", "exp6@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        String payload = "{\"months\": 0}";
        mockMvc.perform(patch("/api/admin/users/" + userId + "/extend")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAdminExtendsExpiryWithLargeMonths() throws Exception {
        Long userId = createUser("student_exp7", "exp7@vera.lms", "Password123", "STUDENT", true);
        Instant originalExpiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("INSERT INTO account_access (user_id, status, must_change_password, expired_at) VALUES (?, 'ACTIVE', false, ?)",
                userId, originalExpiry);

        String payload = "{\"months\": 120}";
        mockMvc.perform(patch("/api/admin/users/" + userId + "/extend")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        Instant newExpiry = jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?", Instant.class, userId);
        assertNotNull(newExpiry);
        long yearsDiff = ChronoUnit.YEARS.between(
                originalExpiry.atZone(java.time.ZoneOffset.UTC),
                newExpiry.atZone(java.time.ZoneOffset.UTC)
        );
        assertEquals(10, yearsDiff);
    }

    @Test
    void testAdminExtendsExpiryForNonStudentAccount() throws Exception {
        Long teacherId = createUser("teacher_exp", "texp@vera.lms", "Password123", "TEACHER", true);
        jdbcTemplate.update("INSERT INTO account_access (user_id, status, must_change_password, expired_at) VALUES (?, 'ACTIVE', false, null)",
                teacherId);

        String payload = "{\"months\": 6}";
        mockMvc.perform(patch("/api/admin/users/" + teacherId + "/extend")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());

        Integer nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_access WHERE user_id = ? AND expired_at IS NULL", Integer.class, teacherId);
        assertEquals(1, nullCount);
    }

    @Test
    void testExpiredAccountRestorationLifecycle() throws Exception {
        Long userId = createUser("student_exp_restore", "restore@vera.lms", "Password123", "STUDENT", true);
        jdbcTemplate.update("INSERT INTO account_access (user_id, status, must_change_password, expired_at) VALUES (?, 'EXPIRED', false, ?)",
                userId, Instant.now().minus(1, ChronoUnit.DAYS));

        String loginPayload = "{\"username\": \"student_exp_restore\", \"password\": \"Password123\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isForbidden());

        String payload = "{\"months\": 6}";
        mockMvc.perform(patch("/api/admin/users/" + userId + "/extend")
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject("SELECT status FROM account_access WHERE user_id = ?", String.class, userId);
        assertEquals("ACTIVE", status);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isOk());
    }

    @Test
    void testDynamicExpirationViaClockDrift() throws Exception {
        Long userId = createUser("student_drift", "drift@vera.lms", "Password123", "STUDENT", true);
        jdbcTemplate.update("INSERT INTO account_access (user_id, status, must_change_password, expired_at) VALUES (?, 'ACTIVE', false, ?)",
                userId, Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer mock-student-token"))
                .andExpect(status().isForbidden());

        String status = jdbcTemplate.queryForObject("SELECT status FROM account_access WHERE user_id = ?", String.class, userId);
        assertEquals("EXPIRED", status);
    }

    @Test
    void testAccessApiAfterExpiry() throws Exception {
        Long userId = createUser("student_exp_after", "after@vera.lms", "Password123", "STUDENT", true);
        jdbcTemplate.update("INSERT INTO account_access (user_id, status, must_change_password, expired_at) VALUES (?, 'EXPIRED', false, ?)",
                userId, Instant.now().minus(10, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer mock-student-token"))
                .andExpect(status().isForbidden());
    }
}
