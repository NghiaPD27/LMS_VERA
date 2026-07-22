package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.CheckpointDto.*;
import vera.lms.dtos.PageDto.PageResponse;
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

    @GetMapping("/checkpoint-sessions")
    public ResponseEntity<PageResponse<CheckpointSessionResponse>> getCheckpointSessions(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Integer blockNumber,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant weekStart,
            @RequestParam(required = false) Instant weekEnd,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(checkpointService.getAdminSessions(
                programId, blockNumber, status, weekStart, weekEnd, page, size));
    }

    @GetMapping("/checkpoint-sessions/{id}")
    public ResponseEntity<CheckpointSessionResponse> getCheckpointSession(@PathVariable Long id) {
        return ResponseEntity.ok(checkpointService.getAdminSession(id));
    }

    @PatchMapping("/checkpoint-sessions/{id}")
    public ResponseEntity<CheckpointSessionResponse> updateCheckpointSession(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCheckpointSessionRequest request) {
        return ResponseEntity.ok(checkpointService.updateSession(id, request));
    }

    @PatchMapping("/checkpoint-sessions/{id}/status")
    public ResponseEntity<CheckpointSessionResponse> updateCheckpointSessionStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCheckpointSessionStatusRequest request) {
        return ResponseEntity.ok(checkpointService.updateSessionStatus(id, request));
    }

    @PostMapping("/checkpoint-sessions/{id}/participants")
    public ResponseEntity<CheckpointSessionResponse> addCheckpointParticipants(
            @PathVariable Long id,
            @RequestBody @Valid AddCheckpointParticipantsRequest request) {
        return ResponseEntity.ok(checkpointService.addParticipants(id, request));
    }

    @DeleteMapping("/checkpoint-sessions/{id}/participants/{participantId}")
    public ResponseEntity<CheckpointSessionResponse> removeCheckpointParticipant(
            @PathVariable Long id,
            @PathVariable Long participantId) {
        return ResponseEntity.ok(checkpointService.removeParticipant(id, participantId));
    }
}
