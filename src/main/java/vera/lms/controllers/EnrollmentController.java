package vera.lms.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import vera.lms.dtos.EnrollmentDto.AdminEnrollmentResponse;
import vera.lms.dtos.EnrollmentDto.EnrollStudentRequest;
import vera.lms.dtos.EnrollmentDto.EnrollmentResponse;
import vera.lms.dtos.EnrollmentDto.ExtendEnrollmentRequest;
import vera.lms.dtos.EnrollmentDto.UpdateEnrollmentRequest;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.mapping.EnrollmentMapper;
import vera.lms.models.Enrollment;
import vera.lms.models.User;
import vera.lms.services.EnrollmentService;

import java.util.List;

@RestController
@RequestMapping
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final EnrollmentMapper enrollmentMapper;

    @Autowired
    public EnrollmentController(EnrollmentService enrollmentService, EnrollmentMapper enrollmentMapper) {
        this.enrollmentService = enrollmentService;
        this.enrollmentMapper = enrollmentMapper;
    }

    @PostMapping("/api/enrollments")
    public ResponseEntity<EnrollmentResponse> enrollStudent(@RequestBody @Valid EnrollStudentRequest request) {
        Enrollment enrollment = enrollmentService.enrollStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentMapper.toResponse(enrollment));
    }

    @GetMapping("/api/admin/enrollments")
    public ResponseEntity<PageResponse<AdminEnrollmentResponse>> getAdminEnrollments(
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String programId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(enrollmentService.getAdminEnrollments(studentId, programId, status, page, size));
    }

    @PatchMapping("/api/enrollments/{id}")
    public ResponseEntity<EnrollmentResponse> updateEnrollmentStatus(
            @PathVariable Long id,
            @RequestBody @Valid UpdateEnrollmentRequest request) {
        Enrollment enrollment = enrollmentService.updateEnrollmentStatus(id, request);
        return ResponseEntity.ok(enrollmentMapper.toResponse(enrollment));
    }

    @PatchMapping("/api/admin/enrollments/{id}/extend")
    public ResponseEntity<EnrollmentResponse> extendEnrollment(
            @PathVariable Long id,
            @RequestBody @Valid ExtendEnrollmentRequest request) {
        Enrollment enrollment = enrollmentService.extendEnrollment(id, request);
        return ResponseEntity.ok(enrollmentMapper.toResponse(enrollment));
    }

    @GetMapping("/api/student/enrollments")
    public ResponseEntity<List<EnrollmentResponse>> getStudentEnrollments(
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(enrollmentService.getStudentEnrollmentResponses(student));
    }
}
