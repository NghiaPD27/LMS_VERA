package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.FinalAssessmentDto.*;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.services.FinalAssessmentService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class FinalAssessmentAdminController {

    private final FinalAssessmentService finalAssessmentService;

    public FinalAssessmentAdminController(FinalAssessmentService finalAssessmentService) {
        this.finalAssessmentService = finalAssessmentService;
    }

    @GetMapping("/final-assessment-eligible-students")
    public ResponseEntity<List<FinalAssessmentEligibleStudentResponse>> getEligibleStudents(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Instant weekStart,
            @RequestParam(required = false) Instant weekEnd) {
        return ResponseEntity.ok(finalAssessmentService.getEligibleStudents(programId, weekStart, weekEnd));
    }

    @PostMapping("/final-assessment-sessions")
    public ResponseEntity<FinalAssessmentSessionResponse> createSession(
            @RequestBody @Valid CreateFinalAssessmentSessionRequest request) {
        return ResponseEntity.ok(finalAssessmentService.createSession(request));
    }

    @GetMapping("/final-assessment-sessions")
    public ResponseEntity<PageResponse<FinalAssessmentSessionResponse>> getSessions(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant weekStart,
            @RequestParam(required = false) Instant weekEnd,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(finalAssessmentService.getAdminSessions(
                programId, status, weekStart, weekEnd, page, size));
    }

    @GetMapping("/final-assessment-sessions/{id}")
    public ResponseEntity<FinalAssessmentSessionResponse> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(finalAssessmentService.getAdminSession(id));
    }

    @PatchMapping("/final-assessment-sessions/{id}")
    public ResponseEntity<FinalAssessmentSessionResponse> updateSession(
            @PathVariable Long id,
            @RequestBody @Valid UpdateFinalAssessmentSessionRequest request) {
        return ResponseEntity.ok(finalAssessmentService.updateSession(id, request));
    }

    @PatchMapping("/final-assessment-sessions/{id}/status")
    public ResponseEntity<FinalAssessmentSessionResponse> updateSessionStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateFinalAssessmentSessionStatusRequest request) {
        return ResponseEntity.ok(finalAssessmentService.updateSessionStatus(id, request));
    }

    @PostMapping("/final-assessment-sessions/{id}/participants")
    public ResponseEntity<FinalAssessmentSessionResponse> addParticipants(
            @PathVariable Long id,
            @RequestBody @Valid AddFinalAssessmentParticipantsRequest request) {
        return ResponseEntity.ok(finalAssessmentService.addParticipants(id, request));
    }

    @DeleteMapping("/final-assessment-sessions/{id}/participants/{participantId}")
    public ResponseEntity<FinalAssessmentSessionResponse> removeParticipant(
            @PathVariable Long id,
            @PathVariable Long participantId) {
        return ResponseEntity.ok(finalAssessmentService.removeParticipant(id, participantId));
    }
}
