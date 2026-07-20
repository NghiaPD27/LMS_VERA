package vera.lms.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class LessonDto {
    public record CreateLessonRequest(
        @NotBlank(message = "Lesson name is required")
        String name,

        @Min(value = 1, message = "Lesson number must be at least 1")
        int lessonNumber,

        String content
    ) {}

    public record UpdateLessonRequest(
        @NotBlank(message = "Lesson name is required")
        String name,

        @Min(value = 1, message = "Lesson number must be at least 1")
        int lessonNumber,

        String content
    ) {}

    public record LessonResponse(
            Long id,
            Long programId,
            String name,
            int lessonNumber,
            String content,
            String status,
            String lessonProgressStatus,
            Boolean locked
    ) {}
}
