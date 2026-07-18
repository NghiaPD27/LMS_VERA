package vera.lms.phase3;

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

class VideoProgressE2ETest extends BaseIntegrationTest {

    private Long seedStudent(String username) {
        Long studentId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "Video", "Student", "0901234567");
        return studentId;
    }

    private Long seedProgram(String name) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, currency, sales_status)
                VALUES (?, ?, ?, ?, ?)
                """, name, "Video program", 1800000, "VND", "PUBLISHED");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedLesson(Long programId, int lessonNumber, String status) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, ?)
                """, programId, "Video Lesson " + lessonNumber, lessonNumber, "Lesson content", status);
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

    private void attachVideo(Long lessonId, int durationSeconds) throws Exception {
        mockMvc.perform(post("/api/lessons/" + lessonId + "/video")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bunnyVideoId": "bunny-video-1",
                                  "libraryId": "library-1",
                                  "durationSeconds": %d,
                                  "thumbnailUrl": "https://cdn.test/thumb.jpg",
                                  "status": "READY"
                                }
                                """.formatted(durationSeconds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lessonId))
                .andExpect(jsonPath("$.bunnyVideoId").value("bunny-video-1"))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    private Long seedAccessibleVideoLesson(int durationSeconds) throws Exception {
        Long studentId = seedStudent("student_user");
        Long programId = seedProgram("Video Program " + durationSeconds);
        Long lessonId = seedLesson(programId, 1, "PUBLISHED");
        seedEnrollment(studentId, programId);
        seedLessonProgress(studentId, lessonId, "VIDEO_IN_PROGRESS");
        attachVideo(lessonId, durationSeconds);
        return lessonId;
    }

    @Test
    void testAdminCanAttachVideoAndStudentCanGetPlaybackWhenUnlocked() throws Exception {
        Long lessonId = seedAccessibleVideoLesson(600);

        mockMvc.perform(get("/api/lessons/" + lessonId + "/video-playback")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lessonId))
                .andExpect(jsonPath("$.playbackUrl").value("https://mock-bunny.local/libraries/library-1/videos/bunny-video-1/playback.m3u8"))
                .andExpect(jsonPath("$.durationSeconds").value(600));
    }

    @Test
    void testAdminCanCreateTusUploadSessionForLessonVideoUpload() throws Exception {
        Long programId = seedProgram("Upload Session Program");
        Long lessonId = seedLesson(programId, 1, "PUBLISHED");

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-upload-session")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Admin Uploaded Lesson Video",
                                  "fileType": "video/mp4"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lessonId))
                .andExpect(jsonPath("$.lessonVideoId").exists())
                .andExpect(jsonPath("$.videoId").exists())
                .andExpect(jsonPath("$.libraryId").value("mock-library"))
                .andExpect(jsonPath("$.tusUploadUrl").value("https://video.bunnycdn.com/tusupload"))
                .andExpect(jsonPath("$.authorizationSignature").exists())
                .andExpect(jsonPath("$.authorizationExpire").exists())
                .andExpect(jsonPath("$.fileType").value("video/mp4"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM lesson_videos WHERE lesson_id = ?",
                String.class,
                lessonId);
        Integer storedDuration = jdbcTemplate.queryForObject(
                "SELECT duration_seconds FROM lesson_videos WHERE lesson_id = ?",
                Integer.class,
                lessonId);
        assertEquals("PROCESSING", storedStatus);
        assertEquals(0, storedDuration);
    }

    @Test
    void testAdminCanSyncUploadedVideoMetadataAfterTusUpload() throws Exception {
        Long programId = seedProgram("Sync Upload Program");
        Long lessonId = seedLesson(programId, 1, "PUBLISHED");

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-upload-session")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sync Video\",\"fileType\":\"video/mp4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video/sync")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lessonId))
                .andExpect(jsonPath("$.durationSeconds").value(600))
                .andExpect(jsonPath("$.thumbnailUrl").exists())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void testUploadSessionRejectsNonVideoFileType() throws Exception {
        Long programId = seedProgram("Invalid Upload Session Program");
        Long lessonId = seedLesson(programId, 1, "PUBLISHED");

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-upload-session")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Bad Upload",
                                  "fileType": "application/pdf"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File type must be a video MIME type"));
    }

    @Test
    void testVideoProgressBelowNinetyPercentDoesNotUnlockQuiz() throws Exception {
        Long lessonId = seedAccessibleVideoLesson(600);

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-progress")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSecond\":120,\"furthestWatchedSecond\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watchedPercentage").value(20))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.lessonProgressStatus").value("VIDEO_IN_PROGRESS"));

        String lessonProgressStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM student_lesson_progress WHERE lesson_id = ?",
                String.class,
                lessonId);
        assertEquals("VIDEO_IN_PROGRESS", lessonProgressStatus);
    }

    @Test
    void testVideoProgressAtNinetyPercentUnlocksQuiz() throws Exception {
        Long lessonId = seedAccessibleVideoLesson(600);

        for (int second : new int[]{120, 240, 360, 480}) {
            mockMvc.perform(post("/api/lessons/" + lessonId + "/video-progress")
                            .header("Authorization", "Bearer student-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentSecond\":" + second + ",\"furthestWatchedSecond\":" + second + "}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-progress")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSecond\":540,\"furthestWatchedSecond\":540}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watchedPercentage").value(90))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.lessonProgressStatus").value("QUIZ_AVAILABLE"));

        String lessonProgressStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM student_lesson_progress WHERE lesson_id = ?",
                String.class,
                lessonId);
        assertEquals("QUIZ_AVAILABLE", lessonProgressStatus);
    }

    @Test
    void testFurthestWatchedSecondCannotDecrease() throws Exception {
        Long lessonId = seedAccessibleVideoLesson(600);

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-progress")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSecond\":120,\"furthestWatchedSecond\":120}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-progress")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSecond\":100,\"furthestWatchedSecond\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Furthest watched second cannot decrease"));
    }

    @Test
    void testAbnormalProgressJumpIsRejected() throws Exception {
        Long lessonId = seedAccessibleVideoLesson(600);

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-progress")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSecond\":540,\"furthestWatchedSecond\":540}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Video progress jump is too large"));
    }

    @Test
    void testCurrentSecondCannotExceedVideoDuration() throws Exception {
        Long lessonId = seedAccessibleVideoLesson(600);

        mockMvc.perform(post("/api/lessons/" + lessonId + "/video-progress")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSecond\":606,\"furthestWatchedSecond\":120}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current second exceeds video duration"));
    }

    @Test
    void testLockedOrUnenrolledStudentCannotGetPlaybackUrl() throws Exception {
        Long studentId = seedStudent("student_user");
        Long programId = seedProgram("Locked Video Program");
        Long lessonId = seedLesson(programId, 1, "PUBLISHED");
        seedEnrollment(studentId, programId);
        seedLessonProgress(studentId, lessonId, "LOCKED");
        attachVideo(lessonId, 600);

        mockMvc.perform(get("/api/lessons/" + lessonId + "/video-playback")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Lesson is locked"));
    }
}
