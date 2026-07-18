package vera.lms.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
    public record EnrollmentResponse(Long id, Long studentId, Long programId, String status) {}
    public record AdminEnrollmentResponse(
        Long id,
        Long studentId,
        String studentName,
        String studentEmail,
        Long programId,
        String programName,
        String status
    ) {}
}
