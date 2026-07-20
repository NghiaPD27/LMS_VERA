package vera.lms.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class VideoDto {

    public record UpsertLessonVideoRequest(
            @NotBlank(message = "Bunny video ID is required")
            String bunnyVideoId,

            @NotBlank(message = "Library ID is required")
            String libraryId,

            @Min(value = 1, message = "Duration must be at least 1 second")
            int durationSeconds,

            String thumbnailUrl,

            String status
    ) {}

    public record CreateVideoUploadSessionRequest(
            String title,

            @NotBlank(message = "File type is required")
            String fileType
    ) {}

    public record LessonVideoResponse(
            Long id,
            Long lessonId,
            String bunnyVideoId,
            String libraryId,
            int durationSeconds,
            String thumbnailUrl,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record VideoPlaybackResponse(
            Long lessonId,
            Long lessonVideoId,
            String playbackUrl,
            int durationSeconds,
            String thumbnailUrl,
            String status
    ) {}

    public record VideoUploadSessionResponse(
            Long lessonId,
            Long lessonVideoId,
            String videoId,
            String libraryId,
            String tusUploadUrl,
            String authorizationSignature,
            long authorizationExpire,
            String title,
            String fileType,
            String status
    ) {}

    public record UpdateVideoProgressRequest(
            @Min(value = 0, message = "Current second cannot be negative")
            int currentSecond,

            @Min(value = 0, message = "Furthest watched second cannot be negative")
            int furthestWatchedSecond
    ) {}

    public record VideoProgressResponse(
            Long lessonId,
            Long lessonVideoId,
            int currentSecond,
            int furthestWatchedSecond,
            int watchedPercentage,
            boolean completed,
            String lessonProgressStatus,
            Instant updatedAt
    ) {}

    public record VideoProgressSnapshot(
            int currentSecond,
            int furthestWatchedSecond,
            int watchedPercentage,
            boolean completed,
            String lessonProgressStatus
    ) {}

    public record LearningStateResponse(
            Long lessonId,
            String lessonStatus,
            String videoStatus,
            VideoProgressSnapshot progress,
            boolean quizAvailable,
            boolean hasQuiz,
            String enrollmentStatus,
            Instant expiredAt
    ) {}
}
