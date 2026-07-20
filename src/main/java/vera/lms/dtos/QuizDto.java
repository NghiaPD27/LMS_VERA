package vera.lms.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class QuizDto {

    public record UpsertQuizRequest(
            @NotBlank(message = "Quiz title is required")
            String title,

            @NotEmpty(message = "Quiz must have at least one question")
            List<@Valid QuizQuestionRequest> questions
    ) {}

    public record QuizQuestionRequest(
            @NotBlank(message = "Question text is required")
            String questionText,

            @NotEmpty(message = "Question must have at least two options")
            List<@Valid QuizOptionRequest> options
    ) {}

    public record QuizOptionRequest(
            @NotBlank(message = "Option text is required")
            String optionText,

            boolean correct
    ) {}

    public record QuizResponse(
            Long id,
            Long lessonId,
            String title,
            List<QuizQuestionResponse> questions
    ) {}

    public record QuizQuestionResponse(
            Long id,
            String questionText,
            int position,
            List<QuizOptionResponse> options
    ) {}

    public record QuizOptionResponse(
            Long id,
            String optionText,
            int position,
            Boolean correct
    ) {}

    public record QuizAttemptResponse(
            Long id,
            Long quizId,
            Long lessonId,
            Long studentId,
            int attemptNumber,
            boolean submitted,
            Integer correctCount,
            Integer totalQuestions,
            Integer scorePercent,
            Integer bestScorePercent,
            String lessonProgressStatus,
            Instant startedAt,
            Instant submittedAt
    ) {}

    public record SubmitQuizAttemptRequest(
            @NotEmpty(message = "Quiz submission must include answers")
            List<@Valid SubmitQuizAnswerRequest> answers
    ) {}

    public record SubmitQuizAnswerRequest(
            @NotNull(message = "Question ID is required")
            Long questionId,

            @NotNull(message = "Selected option ID is required")
            Long selectedOptionId
    ) {}
}
