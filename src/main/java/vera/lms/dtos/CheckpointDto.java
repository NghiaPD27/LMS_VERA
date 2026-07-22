package vera.lms.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class CheckpointDto {

    public record CheckpointEligibleStudentResponse(
            Long studentId,
            String studentName,
            Long enrollmentId,
            Long programId,
            String programName,
            Integer blockNumber,
            Integer startLessonNumber,
            Integer gateLessonNumber,
            Integer nextLessonNumber,
            Long gateLessonId,
            String gateLessonName,
            Instant eligibleAt
    ) {}

    public record CreateCheckpointSessionRequest(
            @NotNull(message = "Program ID is required")
            Long programId,

            @NotNull(message = "Block number is required")
            Integer blockNumber,

            @NotNull(message = "Evaluator ID is required")
            Long evaluatorId,

            @NotNull(message = "Scheduled time is required")
            Instant scheduledAt,

            @NotBlank(message = "Meet link is required")
            @Size(max = 500, message = "Meet link must not exceed 500 characters")
            String meetLink,

            List<Long> participantEnrollmentIds
    ) {}

    public record AddCheckpointParticipantsRequest(
            @NotEmpty(message = "Participant enrollment IDs are required")
            List<Long> enrollmentIds
    ) {}

    public record SubmitCheckpointResultRequest(
            @NotNull(message = "Participant ID is required")
            Long participantId,

            @NotBlank(message = "Result is required")
            String result,

            @Size(max = 1000, message = "Comment must not exceed 1000 characters")
            String comment
    ) {}

    public record UpdateCheckpointSessionRequest(
            Long evaluatorId,

            Instant scheduledAt,

            @Size(max = 500, message = "Meet link must not exceed 500 characters")
            String meetLink
    ) {}

    public record UpdateCheckpointSessionStatusRequest(
            @NotBlank(message = "Status is required")
            String status
    ) {}

    public record CheckpointSessionResponse(
            Long id,
            Long checkpointId,
            Long programId,
            String programName,
            Integer blockNumber,
            Integer startLessonNumber,
            Integer gateLessonNumber,
            Integer nextLessonNumber,
            Long evaluatorId,
            String evaluatorName,
            Instant scheduledAt,
            String meetLink,
            String status,
            Instant createdAt,
            Instant updatedAt,
            List<CheckpointParticipantResponse> participants
    ) {}

    public record CheckpointParticipantResponse(
            Long id,
            Long enrollmentId,
            Long studentId,
            String studentName,
            Instant addedAt,
            CheckpointResultResponse result
    ) {}

    public record CheckpointResultResponse(
            Long id,
            Long participantId,
            Long evaluatorId,
            String result,
            String comment,
            Instant evaluatedAt
    ) {}

    public record StudentCheckpointStatusResponse(
            Long lessonId,
            String lessonProgressStatus,
            Long checkpointId,
            Long sessionId,
            Long participantId,
            Long programId,
            String programName,
            Integer blockNumber,
            Integer gateLessonNumber,
            Integer nextLessonNumber,
            String sessionStatus,
            Instant scheduledAt,
            String meetLink,
            Long evaluatorId,
            String evaluatorName,
            String lastResult,
            String lastComment,
            Instant lastEvaluatedAt
    ) {}
}
