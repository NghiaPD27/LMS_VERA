package vera.lms.phase2;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vera.lms.BaseIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdversarialPhase2Test extends BaseIntegrationTest {

    private Long seedProgram(String name) {
        jdbcTemplate.update("INSERT INTO programs (name, description) VALUES (?, ?)", name, "Desc");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedStudent(String username, String email) {
        Long studentId = createUser(username, email, "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "First", "Last", "123456");
        return studentId;
    }

    private void seedLesson(Long programId, String name, int lessonNumber, String status) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, ?)
                """, programId, name, lessonNumber, "Content", status);
    }

    @Test
    void testConcurrentEnrollmentRaceCondition() throws Exception {
        Long studentId = seedStudent("race_student", "race@vera.lms");
        Long program1Id = seedProgram("Concurrent Program 1");
        Long program2Id = seedProgram("Concurrent Program 2");

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        futures.add(executorService.submit(() -> {
            latch.await();
            return mockMvc.perform(post("/api/enrollments")
                            .header("Authorization", "Bearer admin-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"studentId\":" + studentId + ", \"programId\":" + program1Id + "}"))
                    .andReturn();
        }));
        futures.add(executorService.submit(() -> {
            latch.await();
            return mockMvc.perform(post("/api/enrollments")
                            .header("Authorization", "Bearer admin-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"studentId\":" + studentId + ", \"programId\":" + program2Id + "}"))
                    .andReturn();
        }));

        latch.countDown();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        int successCount = 0;
        int conflictCount = 0;
        for (Future<MvcResult> future : futures) {
            int status = future.get().getResponse().getStatus();
            if (status == 201) {
                successCount++;
            } else if (status == 409) {
                conflictCount++;
            }
        }

        Integer activeEnrollments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE student_id = ? AND status = 'ACTIVE'",
                Integer.class, studentId);
        assertEquals(1, activeEnrollments);
        assertEquals(1, successCount);
        assertEquals(1, conflictCount);
    }

    @Test
    void testCreateLessonWithMalformedInputs() throws Exception {
        Long programId = seedProgram("Validation Program");

        mockMvc.perform(post("/api/programs/" + programId + "/lessons")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"lessonNumber\": 1, \"content\": \"Content\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/programs/" + programId + "/lessons")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Negative\", \"lessonNumber\": -5, \"content\": \"Content\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateLessonForMissingProgram() throws Exception {
        mockMvc.perform(post("/api/programs/99999/lessons")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"No Program\", \"lessonNumber\": 1, \"content\": \"Content\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testFirstPublishedLessonDoesNotNeedLessonNumberOne() throws Exception {
        Long studentId = seedStudent("student_user", "student@vera.lms");
        Long programId = seedProgram("Non One Program");
        seedLesson(programId, "Lesson Two", 2, "PUBLISHED");

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ", \"programId\":" + programId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/programs/" + programId + "/lessons")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lessonNumber").value(2));
    }
}
