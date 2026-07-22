package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.TeacherDto.AssignTeacherRequest;
import vera.lms.dtos.TeacherDto.TeacherAssignmentResponse;
import vera.lms.dtos.TeacherDto.TeacherCompensationResponse;
import vera.lms.dtos.TeacherDto.TeacherEarningsSummaryResponse;
import vera.lms.dtos.TeacherDto.UpsertTeacherCompensationRequest;
import vera.lms.services.TeacherSchedulingService;

@RestController
public class TeacherAdminController {

    private final TeacherSchedulingService teacherSchedulingService;

    public TeacherAdminController(TeacherSchedulingService teacherSchedulingService) {
        this.teacherSchedulingService = teacherSchedulingService;
    }

    @PutMapping("/api/admin/enrollments/{enrollmentId}/teacher-assignment")
    public ResponseEntity<TeacherAssignmentResponse> assignTeacher(
            @PathVariable Long enrollmentId,
            @RequestBody @Valid AssignTeacherRequest request) {
        return ResponseEntity.ok(teacherSchedulingService.assignTeacher(enrollmentId, request));
    }

    @PutMapping("/api/admin/teachers/{teacherId}/compensation")
    public ResponseEntity<TeacherCompensationResponse> upsertTeacherCompensation(
            @PathVariable Long teacherId,
            @RequestBody @Valid UpsertTeacherCompensationRequest request) {
        return ResponseEntity.ok(teacherSchedulingService.upsertCompensation(teacherId, request));
    }

    @GetMapping("/api/admin/teachers/{teacherId}/earnings")
    public ResponseEntity<TeacherEarningsSummaryResponse> getTeacherEarnings(@PathVariable Long teacherId) {
        return ResponseEntity.ok(teacherSchedulingService.getTeacherEarnings(teacherId));
    }
}
