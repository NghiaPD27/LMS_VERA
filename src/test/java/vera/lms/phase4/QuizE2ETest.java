package vera.lms.phase4;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizE2ETest extends BaseIntegrationTest {

    private Long seedStudent(String username) {
        Long studentId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "Quiz", "Student", "0901234567");
        return studentId;
    }

    private Long seedProgram(String name) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, currency, sales_status)
                VALUES (?, ?, ?, ?, ?)
                """, name, "Quiz program", 1800000, "VND", "PUBLISHED");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedLesson(Long programId, int lessonNumber) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, 'PUBLISHED')
                """, programId, "Quiz Lesson " + lessonNumber, lessonNumber, "Lesson content");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lessons WHERE program_id = ? AND lesson_number = ?",
                Long.class,
                programId,
                lessonNumber);
    }

    private void seedEnrollment(Long studentId, Long programId) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (student_id, program_id, status, enrolled_at, expired_at)
                VALUES (?, ?, 'ACTIVE', ?, ?)
                """,
                studentId,
                programId,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS));
    }

    private void seedLessonProgress(Long studentId, Long lessonId, String status) {
        jdbcTemplate.update("""
                INSERT INTO student_lesson_progress (student_id, lesson_id, status)
                VALUES (?, ?, ?)
                """, studentId, lessonId, status);
    }

    private Long seedLessonVideo(Long lessonId) {
        jdbcTemplate.update("""
                INSERT INTO lesson_videos (lesson_id, bunny_video_id, library_id, duration_seconds, thumbnail_url, status)
                VALUES (?, 'quiz-video', 'quiz-library', 600, 'https://cdn.test/quiz-thumb.jpg', 'READY')
                """, lessonId);
        return jdbcTemplate.queryForObject("SELECT id FROM lesson_videos WHERE lesson_id = ?", Long.class, lessonId);
    }

    private void seedVideoProgress(Long studentId, Long lessonVideoId, boolean completed) {
        jdbcTemplate.update("""
                INSERT INTO video_progress
                    (student_id, lesson_video_id, current_second, furthest_watched_second, watched_percentage, completed)
                VALUES (?, ?, ?, ?, ?, ?)
                """, studentId, lessonVideoId, completed ? 540 : 120, completed ? 540 : 120, completed ? 90 : 20, completed);
    }

    private Long seedAccessibleQuizLesson(boolean videoCompleted) throws Exception {
        Long studentId = seedStudent("student_user");
        Long programId = seedProgram("Quiz Program " + videoCompleted);
        Long lessonId = seedLesson(programId, 1);
        seedEnrollment(studentId, programId);
        seedLessonProgress(studentId, lessonId, videoCompleted ? "QUIZ_AVAILABLE" : "VIDEO_IN_PROGRESS");
        Long lessonVideoId = seedLessonVideo(lessonId);
        seedVideoProgress(studentId, lessonVideoId, videoCompleted);
        createQuiz(lessonId);
        return lessonId;
    }

    private Long createQuiz(Long lessonId) throws Exception {
        mockMvc.perform(post("/api/lessons/" + lessonId + "/quiz")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Lesson Review Quiz",
                                  "questions": [
                                    {
                                      "questionText": "Which greeting is formal?",
                                      "options": [
                                        {"optionText": "Good morning", "correct": true},
                                        {"optionText": "Yo", "correct": false}
                                      ]
                                    },
                                    {
                                      "questionText": "What means goodbye?",
                                      "options": [
                                        {"optionText": "Hello", "correct": false},
                                        {"optionText": "See you later", "correct": true}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lessonId))
                .andExpect(jsonPath("$.questions.length()").value(2));
        return jdbcTemplate.queryForObject("SELECT id FROM quizzes WHERE lesson_id = ?", Long.class, lessonId);
    }

    private Long quizIdForLesson(Long lessonId) {
        return jdbcTemplate.queryForObject("SELECT id FROM quizzes WHERE lesson_id = ?", Long.class, lessonId);
    }

    private Long optionId(Long quizId, int questionPosition, int optionPosition) {
        return jdbcTemplate.queryForObject("""
                SELECT qo.id
                FROM quiz_options qo
                JOIN quiz_questions qq ON qq.id = qo.question_id
                WHERE qq.quiz_id = ? AND qq.position = ? AND qo.position = ?
                """, Long.class, quizId, questionPosition, optionPosition);
    }

    @Test
    void testAdminCreatesQuizAndStudentCanReadAfterVideoCompleted() throws Exception {
        Long lessonId = seedAccessibleQuizLesson(true);

        mockMvc.perform(get("/api/lessons/" + lessonId + "/quiz")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lesson Review Quiz"))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].options.length()").value(2));
    }

    @Test
    void testAdminCanAddQuestionToExistingQuiz() throws Exception {
        Long programId = seedProgram("Quiz Update Program");
        Long lessonId = seedLesson(programId, 1);
        createQuiz(lessonId);

        mockMvc.perform(post("/api/lessons/" + lessonId + "/quiz")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated Lesson Review Quiz",
                                  "questions": [
                                    {
                                      "questionText": "Which greeting is formal?",
                                      "options": [
                                        {"optionText": "Good morning", "correct": true},
                                        {"optionText": "Yo", "correct": false}
                                      ]
                                    },
                                    {
                                      "questionText": "What means goodbye?",
                                      "options": [
                                        {"optionText": "Hello", "correct": false},
                                        {"optionText": "See you later", "correct": true}
                                      ]
                                    },
                                    {
                                      "questionText": "Which phrase is polite?",
                                      "options": [
                                        {"optionText": "Please", "correct": true},
                                        {"optionText": "Move", "correct": false}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Lesson Review Quiz"))
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[2].questionText").value("Which phrase is polite?"))
                .andExpect(jsonPath("$.questions[2].position").value(3));

        Long quizId = quizIdForLesson(lessonId);
        Integer questionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quiz_questions WHERE quiz_id = ?",
                Integer.class,
                quizId);
        assertEquals(3, questionCount);
    }

    @Test
    void testStudentCannotStartQuizBeforeVideoCompleted() throws Exception {
        Long lessonId = seedAccessibleQuizLesson(false);
        Long quizId = quizIdForLesson(lessonId);

        mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Video must be completed before taking quiz"));
    }

    @Test
    void testStudentSubmitsQuizAndUnlocksTeacherBooking() throws Exception {
        Long lessonId = seedAccessibleQuizLesson(true);
        Long quizId = quizIdForLesson(lessonId);

        mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.submitted").value(false));

        Long attemptId = jdbcTemplate.queryForObject("SELECT id FROM quiz_attempts WHERE quiz_id = ?", Long.class, quizId);
        Long firstCorrectOption = optionId(quizId, 1, 1);
        Long secondWrongOption = optionId(quizId, 2, 1);

        mockMvc.perform(post("/api/quiz-attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedOptionId": %d},
                                    {"questionId": %d, "selectedOptionId": %d}
                                  ]
                                }
                                """.formatted(
                                jdbcTemplate.queryForObject("SELECT id FROM quiz_questions WHERE quiz_id = ? AND position = 1", Long.class, quizId),
                                firstCorrectOption,
                                jdbcTemplate.queryForObject("SELECT id FROM quiz_questions WHERE quiz_id = ? AND position = 2", Long.class, quizId),
                                secondWrongOption)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.scorePercent").value(50))
                .andExpect(jsonPath("$.lessonProgressStatus").value("WAITING_FOR_TEACHER"));

        Integer answerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quiz_answers WHERE attempt_id = ?",
                Integer.class,
                attemptId);
        String lessonProgressStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM student_lesson_progress WHERE lesson_id = ?",
                String.class,
                lessonId);

        assertEquals(2, answerCount);
        assertEquals("WAITING_FOR_TEACHER", lessonProgressStatus);
    }

    @Test
    void testQuizHasNoMinimumScoreAndLowScoreStillMovesToTeacherStep() throws Exception {
        Long lessonId = seedAccessibleQuizLesson(true);
        Long quizId = quizIdForLesson(lessonId);

        mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk());

        Long attemptId = jdbcTemplate.queryForObject("SELECT id FROM quiz_attempts WHERE quiz_id = ?", Long.class, quizId);
        Long firstWrongOption = optionId(quizId, 1, 2);
        Long secondWrongOption = optionId(quizId, 2, 1);

        mockMvc.perform(post("/api/quiz-attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedOptionId": %d},
                                    {"questionId": %d, "selectedOptionId": %d}
                                  ]
                                }
                                """.formatted(
                                jdbcTemplate.queryForObject("SELECT id FROM quiz_questions WHERE quiz_id = ? AND position = 1", Long.class, quizId),
                                firstWrongOption,
                                jdbcTemplate.queryForObject("SELECT id FROM quiz_questions WHERE quiz_id = ? AND position = 2", Long.class, quizId),
                                secondWrongOption)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorePercent").value(0))
                .andExpect(jsonPath("$.lessonProgressStatus").value("WAITING_FOR_TEACHER"));
    }

    @Test
    void testStudentCanRetakeQuizMultipleTimes() throws Exception {
        Long lessonId = seedAccessibleQuizLesson(true);
        Long quizId = quizIdForLesson(lessonId);

        mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptNumber").value(1));

        Long firstAttemptId = jdbcTemplate.queryForObject("SELECT id FROM quiz_attempts WHERE quiz_id = ?", Long.class, quizId);
        Long firstWrongOption = optionId(quizId, 1, 2);
        Long secondWrongOption = optionId(quizId, 2, 1);

        mockMvc.perform(post("/api/quiz-attempts/" + firstAttemptId + "/submit")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": %d, "selectedOptionId": %d},
                                    {"questionId": %d, "selectedOptionId": %d}
                                  ]
                                }
                                """.formatted(
                                jdbcTemplate.queryForObject("SELECT id FROM quiz_questions WHERE quiz_id = ? AND position = 1", Long.class, quizId),
                                firstWrongOption,
                                jdbcTemplate.queryForObject("SELECT id FROM quiz_questions WHERE quiz_id = ? AND position = 2", Long.class, quizId),
                                secondWrongOption)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorePercent").value(0));

        mockMvc.perform(post("/api/quizzes/" + quizId + "/attempts")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptNumber").value(2));

        Integer attemptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quiz_attempts WHERE quiz_id = ?",
                Integer.class,
                quizId);
        assertEquals(2, attemptCount);
    }
}
