package vera.lms.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class TeacherDto {

    public record AssignTeacherRequest(
            @NotNull(message = "Teacher ID is required")
            Long teacherId
    ) {}

    public record TeacherAssignmentResponse(
            Long id,
            Long enrollmentId,
            Long studentId,
            String studentName,
            Long programId,
            String programName,
            Long teacherId,
            String teacherName,
            Instant assignedAt
    ) {}

    public record UpsertTeacherCompensationRequest(
            @NotNull(message = "Amount per session is required")
            @DecimalMin(value = "0.00", message = "Amount per session must be non-negative")
            BigDecimal amountPerSession,

            String currency
    ) {}

    public record TeacherCompensationResponse(
            Long id,
            Long teacherId,
            BigDecimal amountPerSession,
            String currency,
            Instant updatedAt
    ) {}

    public record CreateAvailabilityRequest(
            @NotNull(message = "Start time is required")
            Instant startAt,

            @NotNull(message = "End time is required")
            Instant endAt
    ) {}

    public record TeacherAvailabilityResponse(
            Long id,
            Long teacherId,
            Instant startAt,
            Instant endAt,
            String status,
            Instant createdAt
    ) {}

    public record TeacherAvailabilitySlotResponse(
            Long availabilityId,
            Long teacherId,
            Instant startAt,
            Instant endAt,
            String status,
            Long bookingId,
            Long studentId,
            String studentName,
            Long lessonId,
            String lessonName
    ) {}

    public record TeacherSlotResponse(
            Long teacherId,
            String teacherName,
            Long availabilityId,
            Instant startAt,
            Instant endAt
    ) {}

    public record CreateBookingRequest(
            @NotNull(message = "Lesson ID is required")
            Long lessonId,

            @NotNull(message = "Slot start time is required")
            Instant slotStartAt
    ) {}

    public record TeacherBookingResponse(
            Long id,
            Long studentId,
            String studentName,
            Long teacherId,
            String teacherName,
            Long enrollmentId,
            Long lessonId,
            String lessonName,
            Instant startAt,
            Instant endAt,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ReviewBookingRequest(
            @NotBlank(message = "Review result is required")
            String result,

            @Size(max = 1000, message = "Comment must not exceed 1000 characters")
            String comment
    ) {}

    public record TeacherReviewResponse(
            Long id,
            Long bookingId,
            String result,
            String comment,
            Instant reviewedAt,
            TeacherBookingResponse booking,
            TeacherEarningResponse earning
    ) {}

    public record TeacherEarningResponse(
            Long id,
            Long teacherId,
            Long bookingId,
            Long studentId,
            String studentName,
            Long lessonId,
            String lessonName,
            BigDecimal amount,
            String currency,
            String status,
            Instant earnedAt
    ) {}

    public record TeacherEarningsSummaryResponse(
            Long teacherId,
            BigDecimal totalEarned,
            String currency,
            List<TeacherEarningResponse> earnings
    ) {}
}
