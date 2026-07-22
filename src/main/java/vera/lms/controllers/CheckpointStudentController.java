package vera.lms.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vera.lms.dtos.CheckpointDto.StudentCheckpointStatusResponse;
import vera.lms.models.User;
import vera.lms.services.CheckpointService;

@RestController
public class CheckpointStudentController {

    private final CheckpointService checkpointService;

    public CheckpointStudentController(CheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    @GetMapping("/api/student/checkpoint-status")
    public ResponseEntity<StudentCheckpointStatusResponse> getCheckpointStatus(
            @AuthenticationPrincipal User student,
            @RequestParam Long lessonId) {
        return ResponseEntity.ok(checkpointService.getStudentCheckpointStatus(student, lessonId));
    }
}
