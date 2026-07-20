package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vera.lms.dtos.QuizDto.QuizAttemptResponse;
import vera.lms.dtos.QuizDto.QuizResponse;
import vera.lms.dtos.QuizDto.SubmitQuizAttemptRequest;
import vera.lms.dtos.QuizDto.UpsertQuizRequest;
import vera.lms.models.User;
import vera.lms.services.QuizService;

@RestController
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/api/lessons/{lessonId}/quiz")
    public ResponseEntity<QuizResponse> upsertLessonQuiz(
            @PathVariable Long lessonId,
            @RequestBody @Valid UpsertQuizRequest request) {
        return ResponseEntity.ok(quizService.upsertQuiz(lessonId, request));
    }

    @GetMapping("/api/lessons/{lessonId}/quiz")
    public ResponseEntity<QuizResponse> getLessonQuiz(
            @PathVariable Long lessonId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(quizService.getQuizForLesson(lessonId, currentUser));
    }

    @PostMapping("/api/quizzes/{quizId}/attempts")
    public ResponseEntity<QuizAttemptResponse> startQuizAttempt(
            @PathVariable Long quizId,
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(quizService.startAttempt(quizId, student));
    }

    @PostMapping("/api/quiz-attempts/{attemptId}/submit")
    public ResponseEntity<QuizAttemptResponse> submitQuizAttempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal User student,
            @RequestBody @Valid SubmitQuizAttemptRequest request) {
        return ResponseEntity.ok(quizService.submitAttempt(attemptId, student, request));
    }
}
