package vera.lms.phase2;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicRegisterAndCatalogE2ETest extends BaseIntegrationTest {

    private Long seedProgram(String name, String salesStatus) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, currency, sales_status)
                VALUES (?, ?, ?, ?, ?)
                """, name, "Catalog description", 1250000, "VND", salesStatus);
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    @Test
    void testPublicStudentCanRegisterWithoutTemporaryPasswordExpiry() throws Exception {
        String payload = """
                {
                  "username": "public_student",
                  "email": "public@student.test",
                  "password": "StrongPassword123",
                  "firstName": "Public",
                  "lastName": "Student",
                  "phoneNumber": "0901234567"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("public_student"))
                .andExpect(jsonPath("$.email").value("public@student.test"))
                .andExpect(jsonPath("$.firstName").value("Public"))
                .andExpect(jsonPath("$.lastName").value("Student"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'public_student'",
                Long.class);
        String role = jdbcTemplate.queryForObject("""
                SELECT r.name
                FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE u.id = ?
                """, String.class, userId);
        Boolean mustChangePassword = jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM account_access WHERE user_id = ?",
                Boolean.class, userId);

        assertNull(jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?",
                java.sql.Timestamp.class, userId));
        org.junit.jupiter.api.Assertions.assertEquals("STUDENT", role);
        org.junit.jupiter.api.Assertions.assertFalse(mustChangePassword);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"public_student\",\"password\":\"StrongPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        assertNull(jdbcTemplate.queryForObject(
                "SELECT expired_at FROM account_access WHERE user_id = ?",
                java.sql.Timestamp.class, userId));
    }

    @Test
    void testPublicRegisterRejectsDuplicateUsernameOrEmail() throws Exception {
        Long existingId = createUser("existing_public", "existing@student.test", "Password123", "STUDENT", true);
        createAccountAccess(existingId, "ACTIVE", false);
        createStudentProfile(existingId, "Existing", "Student", "0900000000");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "existing_public",
                                  "email": "new@student.test",
                                  "password": "StrongPassword123",
                                  "firstName": "New",
                                  "lastName": "Student"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new_public",
                                  "email": "existing@student.test",
                                  "password": "StrongPassword123",
                                  "firstName": "New",
                                  "lastName": "Student"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void testAdminCanManageProgramCommerceFields() throws Exception {
        String createPayload = """
                {
                  "name": "English Commerce",
                  "description": "Sellable course",
                  "price": 2500000,
                  "currency": "vnd",
                  "salesStatus": "PUBLISHED"
                }
                """;

        mockMvc.perform(post("/api/programs")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("English Commerce"))
                .andExpect(jsonPath("$.price").value(2500000))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.salesStatus").value("PUBLISHED"));

        Long programId = jdbcTemplate.queryForObject(
                "SELECT id FROM programs WHERE name = 'English Commerce'",
                Long.class);

        String updatePayload = """
                {
                  "name": "English Commerce Plus",
                  "description": "Updated course",
                  "price": 3000000,
                  "currency": "VND",
                  "salesStatus": "DRAFT"
                }
                """;

        mockMvc.perform(put("/api/programs/" + programId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("English Commerce Plus"))
                .andExpect(jsonPath("$.price").value(3000000))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.salesStatus").value("DRAFT"));
    }

    @Test
    void testPublicCatalogOnlyShowsPublishedPrograms() throws Exception {
        Long publishedId = seedProgram("Public English A1", "PUBLISHED");
        Long draftId = seedProgram("Draft English A2", "DRAFT");
        seedProgram("Archived English B1", "ARCHIVED");

        mockMvc.perform(get("/api/public/programs")
                        .param("keyword", "English")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publishedId))
                .andExpect(jsonPath("$.content[0].name").value("Public English A1"))
                .andExpect(jsonPath("$.content[0].price").value(1250000))
                .andExpect(jsonPath("$.content[0].currency").value("VND"))
                .andExpect(jsonPath("$.content[0].salesStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/public/programs/" + publishedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publishedId))
                .andExpect(jsonPath("$.salesStatus").value("PUBLISHED"));

        mockMvc.perform(get("/api/public/programs/" + draftId))
                .andExpect(status().isNotFound());
    }
}
