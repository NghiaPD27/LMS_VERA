package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.CheckpointDto.CheckpointResultResponse;
import vera.lms.dtos.CheckpointDto.CheckpointSessionResponse;
import vera.lms.dtos.CheckpointDto.SubmitCheckpointResultRequest;
import vera.lms.models.User;
import vera.lms.services.CheckpointService;

import java.util.List;

@RestController
@RequestMapping("/api/evaluator")
public class CheckpointEvaluatorController {

    private final CheckpointService checkpointService;

    public CheckpointEvaluatorController(CheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    @GetMapping("/checkpoint-sessions")
    public ResponseEntity<List<CheckpointSessionResponse>> getCheckpointSessions(
            @AuthenticationPrincipal User evaluator) {
        return ResponseEntity.ok(checkpointService.getEvaluatorSessions(evaluator));
    }

    @GetMapping("/checkpoint-sessions/{id}")
    public ResponseEntity<CheckpointSessionResponse> getCheckpointSession(
            @AuthenticationPrincipal User evaluator,
            @PathVariable Long id) {
        return ResponseEntity.ok(checkpointService.getEvaluatorSession(evaluator, id));
    }

    @PostMapping("/checkpoint-results")
    public ResponseEntity<CheckpointResultResponse> submitCheckpointResult(
            @AuthenticationPrincipal User evaluator,
            @RequestBody @Valid SubmitCheckpointResultRequest request) {
        return ResponseEntity.ok(checkpointService.submitResult(evaluator, request));
    }
}
