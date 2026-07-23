package vera.lms;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = LmsVeraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
//@Testcontainers
public abstract class BaseIntegrationTest {

    // @Container
    // @ServiceConnection
    // protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabase() {
        jdbcTemplate.execute("DELETE FROM final_assessment_results");
        jdbcTemplate.execute("DELETE FROM final_assessment_participants");
        jdbcTemplate.execute("DELETE FROM final_assessment_retake_payments");
        jdbcTemplate.execute("DELETE FROM final_assessment_sessions");
        jdbcTemplate.execute("DELETE FROM checkpoint_results");
        jdbcTemplate.execute("DELETE FROM checkpoint_participants");
        jdbcTemplate.execute("DELETE FROM checkpoint_sessions");
        jdbcTemplate.execute("DELETE FROM checkpoints");
        jdbcTemplate.execute("DELETE FROM teacher_earnings");
        jdbcTemplate.execute("DELETE FROM teacher_reviews");
        jdbcTemplate.execute("DELETE FROM teacher_bookings");
        jdbcTemplate.execute("DELETE FROM teacher_availability");
        jdbcTemplate.execute("DELETE FROM teacher_compensation_configs");
        jdbcTemplate.execute("DELETE FROM student_teacher_assignments");
        jdbcTemplate.execute("DELETE FROM quiz_answers");
        jdbcTemplate.execute("DELETE FROM quiz_attempts");
        jdbcTemplate.execute("DELETE FROM quiz_options");
        jdbcTemplate.execute("DELETE FROM quiz_questions");
        jdbcTemplate.execute("DELETE FROM quizzes");
        jdbcTemplate.execute("DELETE FROM video_progress");
        jdbcTemplate.execute("DELETE FROM lesson_videos");
        jdbcTemplate.execute("DELETE FROM student_lesson_progress");
        jdbcTemplate.execute("DELETE FROM sepay_webhook_events");
        jdbcTemplate.execute("DELETE FROM purchase_events");
        jdbcTemplate.execute("DELETE FROM course_purchases");
        jdbcTemplate.execute("DELETE FROM enrollments");
        jdbcTemplate.execute("DELETE FROM lessons");
        jdbcTemplate.execute("DELETE FROM programs");
        jdbcTemplate.execute("DELETE FROM refresh_tokens");
        jdbcTemplate.execute("DELETE FROM account_access");
        jdbcTemplate.execute("DELETE FROM student_profiles");
        jdbcTemplate.execute("DELETE FROM teacher_profiles");
        jdbcTemplate.execute("DELETE FROM evaluator_profiles");
        jdbcTemplate.execute("DELETE FROM users");

        // Seed a default Admin user
        // Plaintext Password: AdminPassword123 (stored as BCrypt hash)
        String adminPasswordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("AdminPassword123");
        jdbcTemplate.update("INSERT INTO users (id, username, email, password, enabled, role_id) VALUES (1, 'admin', 'admin@vera.lms', ?, true, (SELECT id FROM roles WHERE name = 'ADMIN'))", adminPasswordHash);
        jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN id RESTART WITH 2");
        jdbcTemplate.execute("INSERT INTO account_access (user_id, status, must_change_password) VALUES (1, 'ACTIVE', false)");
    }

    protected Long createUser(String username, String email, String password, String role, boolean enabled) {
        String passwordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password);
        jdbcTemplate.update("INSERT INTO users (username, email, password, enabled, role_id) VALUES (?, ?, ?, ?, (SELECT id FROM roles WHERE name = ?))",
                username, email, passwordHash, enabled, role);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        return userId;
    }

    protected void createAccountAccess(Long userId, String status, boolean mustChangePassword) {
        jdbcTemplate.update("INSERT INTO account_access (user_id, status, must_change_password) VALUES (?, ?, ?)",
                userId, status, mustChangePassword);
    }

    protected void createStudentProfile(Long userId, String firstName, String lastName, String phoneNumber) {
        jdbcTemplate.update("INSERT INTO student_profiles (user_id, first_name, last_name, phone_number) VALUES (?, ?, ?, ?)",
                userId, firstName, lastName, phoneNumber);
    }

    protected void createTeacherProfile(Long userId, String firstName, String lastName, String phoneNumber, String bio) {
        jdbcTemplate.update("INSERT INTO teacher_profiles (user_id, first_name, last_name, phone_number, bio) VALUES (?, ?, ?, ?, ?)",
                userId, firstName, lastName, phoneNumber, bio);
    }

    protected void createEvaluatorProfile(Long userId, String firstName, String lastName, String phoneNumber) {
        jdbcTemplate.update("INSERT INTO evaluator_profiles (user_id, first_name, last_name, phone_number) VALUES (?, ?, ?, ?)",
                userId, firstName, lastName, phoneNumber);
    }
}
