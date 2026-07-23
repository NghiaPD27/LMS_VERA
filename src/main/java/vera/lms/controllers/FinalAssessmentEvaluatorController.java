package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.FinalAssessmentDto.FinalAssessmentResultResponse;
import vera.lms.dtos.FinalAssessmentDto.FinalAssessmentSessionResponse;
import vera.lms.dtos.FinalAssessmentDto.SubmitFinalAssessmentResultRequest;
import vera.lms.models.User;
import vera.lms.services.FinalAssessmentService;

import java.util.List;

@RestController
@RequestMapping("/api/evaluator")
public class FinalAssessmentEvaluatorController {

    private final FinalAssessmentService finalAssessmentService;

    public FinalAssessmentEvaluatorController(FinalAssessmentService finalAssessmentService) {
        this.finalAssessmentService = finalAssessmentService;
    }

    @GetMapping("/final-assessment-sessions")
    public ResponseEntity<List<FinalAssessmentSessionResponse>> getSessions(
            @AuthenticationPrincipal User evaluator) {
        return ResponseEntity.ok(finalAssessmentService.getEvaluatorSessions(evaluator));
    }

    @GetMapping("/final-assessment-sessions/{id}")
    public ResponseEntity<FinalAssessmentSessionResponse> getSession(
            @AuthenticationPrincipal User evaluator,
            @PathVariable Long id) {
        return ResponseEntity.ok(finalAssessmentService.getEvaluatorSession(evaluator, id));
    }

    @PostMapping("/final-assessment-results")
    public ResponseEntity<FinalAssessmentResultResponse> submitResult(
            @AuthenticationPrincipal User evaluator,
            @RequestBody @Valid SubmitFinalAssessmentResultRequest request) {
        return ResponseEntity.ok(finalAssessmentService.submitResult(evaluator, request));
    }
}
