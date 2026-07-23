package vera.lms.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class FinalAssessmentDto {

    public record FinalAssessmentEligibleStudentResponse(
            Long studentId,
            String studentName,
            Long enrollmentId,
            Long programId,
            String programName,
            Long finalLessonId,
            Integer finalLessonNumber,
            String finalLessonName,
            Boolean retake,
            Long retakePaymentId,
            Instant eligibleAt
    ) {}

    public record CreateFinalAssessmentSessionRequest(
            @NotNull(message = "Program ID is required")
            Long programId,

            @NotNull(message = "Evaluator ID is required")
            Long evaluatorId,

            @NotNull(message = "Scheduled time is required")
            Instant scheduledAt,

            @NotBlank(message = "Meet link is required")
            @Size(max = 500, message = "Meet link must not exceed 500 characters")
            String meetLink,

            List<Long> participantEnrollmentIds
    ) {}

    public record AddFinalAssessmentParticipantsRequest(
            @NotEmpty(message = "Participant enrollment IDs are required")
            List<Long> enrollmentIds
    ) {}

    public record UpdateFinalAssessmentSessionRequest(
            Long evaluatorId,
            Instant scheduledAt,
            @Size(max = 500, message = "Meet link must not exceed 500 characters")
            String meetLink
    ) {}

    public record UpdateFinalAssessmentSessionStatusRequest(
            @NotBlank(message = "Status is required")
            String status
    ) {}

    public record SubmitFinalAssessmentResultRequest(
            @NotNull(message = "Participant ID is required")
            Long participantId,

            @NotBlank(message = "Result is required")
            String result,

            @Size(max = 1000, message = "Comment must not exceed 1000 characters")
            String comment
    ) {}

    public record CreateFinalAssessmentRetakePaymentRequest(
            @NotNull(message = "Enrollment ID is required")
            Long enrollmentId
    ) {}

    public record FinalAssessmentSessionResponse(
            Long id,
            Long programId,
            String programName,
            Long evaluatorId,
            String evaluatorName,
            Instant scheduledAt,
            String meetLink,
            String status,
            Instant createdAt,
            Instant updatedAt,
            Integer participantCount,
            Integer resultSubmittedCount,
            Boolean canManage,
            List<FinalAssessmentParticipantResponse> participants
    ) {}

    public record FinalAssessmentParticipantResponse(
            Long id,
            Long enrollmentId,
            Long studentId,
            String studentName,
            Boolean retake,
            Long retakePaymentId,
            Instant addedAt,
            FinalAssessmentResultResponse result
    ) {}

    public record FinalAssessmentResultResponse(
            Long id,
            Long participantId,
            Long evaluatorId,
            String result,
            String comment,
            Instant evaluatedAt
    ) {}

    public record FinalAssessmentRetakePaymentResponse(
            Long id,
            Long enrollmentId,
            Long studentId,
            String studentName,
            Long programId,
            String programName,
            BigDecimal amount,
            String currency,
            String status,
            String paymentCode,
            String paymentQrUrl,
            String paymentProvider,
            String paymentContent,
            Instant createdAt,
            Instant paidAt
    ) {}

    public record StudentFinalAssessmentStatusResponse(
            Long enrollmentId,
            Long programId,
            String programName,
            String enrollmentStatus,
            Boolean eligible,
            Boolean retakeRequired,
            Long sessionId,
            Long participantId,
            String sessionStatus,
            Instant scheduledAt,
            String meetLink,
            Long evaluatorId,
            String evaluatorName,
            String lastResult,
            String lastComment,
            Instant lastEvaluatedAt,
            FinalAssessmentRetakePaymentResponse latestRetakePayment
    ) {}
}
