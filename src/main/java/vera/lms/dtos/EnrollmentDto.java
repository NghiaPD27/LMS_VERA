package vera.lms.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class EnrollmentDto {
    public record EnrollStudentRequest(
        @NotNull(message = "Student ID is required")
        Long studentId,
        @NotNull(message = "Program ID is required")
        Long programId
    ) {}
    public record UpdateEnrollmentRequest(
        @NotBlank(message = "Status is required")
        String status
    ) {}
    public record ExtendEnrollmentRequest(
        @NotNull(message = "Months count is required")
        @Min(value = 1, message = "Months count must be at least 1")
        Integer months
    ) {}
    public record EnrollmentResponse(
        Long id,
        Long studentId,
        Long programId,
        String programName,
        String status,
        Instant enrolledAt,
        Instant expiredAt,
        Integer progressPercent,
        Integer currentLessonNumber,
        String currentLessonName,
        String currentLessonStatus,
        String nextAction
    ) {}
    public record AdminEnrollmentResponse(
        Long id,
        Long studentId,
        String studentName,
        String studentEmail,
        Long programId,
        String programName,
        String status,
        Instant enrolledAt,
        Instant expiredAt,
        Long teacherId,
        String teacherName,
        Instant teacherAssignedAt
    ) {}
}
