package vera.lms.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vera.lms.BaseIntegrationTest;
import vera.lms.dtos.AuthDto.LoginResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class AdversarialSecurityTest extends BaseIntegrationTest {

    @Test
    void testBackdoorPasswordBypassAfterPasswordChange() throws Exception {
        // Fix: Seed a valid BCrypt hash of "AdminPassword123" first to allow password change to proceed
        String realHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("AdminPassword123");
        jdbcTemplate.update("UPDATE users SET password = ? WHERE username = 'admin'", realHash);

        // Log in as admin
        String loginPayload = "{\"username\": \"admin\", \"password\": \"AdminPassword123\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseContent, LoginResponse.class);
        String accessToken = loginResponse.accessToken();

        // Change password using change-password endpoint
        String changePasswordPayload = "{\"oldPassword\": \"AdminPassword123\", \"newPassword\": \"NewAdminSecurePassword999\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changePasswordPayload))
                .andExpect(status().isOk());

        // Attempt login with new password -> should work
        String loginNewPayload = "{\"username\": \"admin\", \"password\": \"NewAdminSecurePassword999\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginNewPayload))
                .andExpect(status().isOk());

        // Attempt login with backdoor password -> SHOULD fail
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccessTokenReuseAfterLogout() throws Exception {
        Long userId = createUser("student_reuse_logout", "reuse_logout@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", false);

        String loginPayload = "{\"username\": \"student_reuse_logout\", \"password\": \"Password123\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(responseContent, LoginResponse.class);
        String accessToken = loginResponse.accessToken();
        String refreshToken = loginResponse.refreshToken();

        // Access /api/auth/me before logout
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Sleep 1 second to guarantee that logout happens in a later epoch second than token issuance
        Thread.sleep(1000);

        // Logout using refresh token
        String logoutPayload = "{\"refreshToken\": \"" + refreshToken + "\"}";
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(logoutPayload))
                .andExpect(status().isOk());

        // Access /api/auth/me after logout -> SHOULD fail
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCrossUserSessionRevocation() throws Exception {
        // Create User A
        Long userAId = createUser("student_user_a", "usera@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userAId, "ACTIVE", false);

        // Create User B
        Long userBId = createUser("student_user_b", "userb@vera.lms", "Password123", "STUDENT", true);
        createAccountAccess(userBId, "ACTIVE", false);

        // Log in User A
        String loginPayloadA = "{\"username\": \"student_user_a\", \"password\": \"Password123\"}";
        MvcResult loginResultA = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayloadA))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse loginResponseA = objectMapper.readValue(loginResultA.getResponse().getContentAsString(), LoginResponse.class);

        // Log in User B
        String loginPayloadB = "{\"username\": \"student_user_b\", \"password\": \"Password123\"}";
        MvcResult loginResultB = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayloadB))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse loginResponseB = objectMapper.readValue(loginResultB.getResponse().getContentAsString(), LoginResponse.class);

        // Verify User B's refresh token is active
        Integer activeTokensBBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked = false", Integer.class, userBId);
        assertEquals(1, activeTokensBBefore);

        // User A logs out using User B's refresh token
        String logoutPayload = "{\"refreshToken\": \"" + loginResponseB.refreshToken() + "\"}";
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + loginResponseA.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(logoutPayload))
                .andExpect(status().isForbidden());

        // Verify User B's refresh token is NOT revoked
        Integer activeTokensBAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked = false", Integer.class, userBId);
        assertEquals(1, activeTokensBAfter);
    }

    @Test
    void testTokenReuseAfterPasswordChange() throws Exception {
        Long userId = createUser("student_reuse_pwd", "reuse_pwd@vera.lms", "TempPassword123", "STUDENT", true);
        createAccountAccess(userId, "ACTIVE", true); // must change password is true

        // Log in to get tokens
        String loginPayload = "{\"username\": \"student_reuse_pwd\", \"password\": \"TempPassword123\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String accessToken = loginResponse.accessToken();

        // Sleep 1 second to guarantee that password change happens in a later epoch second than token issuance
        Thread.sleep(1000);

        // Change password using change-password endpoint
        String changePayload = "{\"oldPassword\": \"TempPassword123\", \"newPassword\": \"NewSecurePassword123\"}";
        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changePayload))
                .andExpect(status().isOk());

        // Access protected endpoint using the OLD access token -> SHOULD fail
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }
}
