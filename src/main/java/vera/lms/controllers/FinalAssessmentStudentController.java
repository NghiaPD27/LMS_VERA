package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.FinalAssessmentDto.CreateFinalAssessmentRetakePaymentRequest;
import vera.lms.dtos.FinalAssessmentDto.FinalAssessmentRetakePaymentResponse;
import vera.lms.dtos.FinalAssessmentDto.StudentFinalAssessmentStatusResponse;
import vera.lms.models.User;
import vera.lms.services.FinalAssessmentService;

import java.util.List;

@RestController
@RequestMapping("/api/student")
public class FinalAssessmentStudentController {

    private final FinalAssessmentService finalAssessmentService;

    public FinalAssessmentStudentController(FinalAssessmentService finalAssessmentService) {
        this.finalAssessmentService = finalAssessmentService;
    }

    @GetMapping("/final-assessment-status")
    public ResponseEntity<StudentFinalAssessmentStatusResponse> getStatus(
            @AuthenticationPrincipal User student,
            @RequestParam Long enrollmentId) {
        return ResponseEntity.ok(finalAssessmentService.getStudentStatus(student, enrollmentId));
    }

    @PostMapping("/final-assessment-retake-payments")
    public ResponseEntity<FinalAssessmentRetakePaymentResponse> createRetakePayment(
            @AuthenticationPrincipal User student,
            @RequestBody @Valid CreateFinalAssessmentRetakePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(finalAssessmentService.createRetakePayment(student, request));
    }

    @GetMapping("/final-assessment-retake-payments")
    public ResponseEntity<List<FinalAssessmentRetakePaymentResponse>> getRetakePayments(
            @AuthenticationPrincipal User student,
            @RequestParam(required = false) Long enrollmentId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(finalAssessmentService.getStudentRetakePayments(student, enrollmentId, status));
    }
}
