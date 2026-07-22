package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.CheckpointDto.*;
import vera.lms.services.CheckpointService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class CheckpointAdminController {

    private final CheckpointService checkpointService;

    public CheckpointAdminController(CheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    @GetMapping("/checkpoint-eligible-students")
    public ResponseEntity<List<CheckpointEligibleStudentResponse>> getCheckpointEligibleStudents(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Integer blockNumber,
            @RequestParam(required = false) Instant weekStart,
            @RequestParam(required = false) Instant weekEnd) {
        return ResponseEntity.ok(checkpointService.getEligibleStudents(programId, blockNumber, weekStart, weekEnd));
    }

    @PostMapping("/checkpoint-sessions")
    public ResponseEntity<CheckpointSessionResponse> createCheckpointSession(
            @RequestBody @Valid CreateCheckpointSessionRequest request) {
        return ResponseEntity.ok(checkpointService.createSession(request));
    }

    @PostMapping("/checkpoint-sessions/{id}/participants")
    public ResponseEntity<CheckpointSessionResponse> addCheckpointParticipants(
            @PathVariable Long id,
            @RequestBody @Valid AddCheckpointParticipantsRequest request) {
        return ResponseEntity.ok(checkpointService.addParticipants(id, request));
    }
}
