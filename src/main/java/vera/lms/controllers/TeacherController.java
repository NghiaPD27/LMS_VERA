package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.TeacherDto.CreateAvailabilityRequest;
import vera.lms.dtos.TeacherDto.ReviewBookingRequest;
import vera.lms.dtos.TeacherDto.TeacherAssignmentResponse;
import vera.lms.dtos.TeacherDto.TeacherAvailabilityResponse;
import vera.lms.dtos.TeacherDto.TeacherBookingResponse;
import vera.lms.dtos.TeacherDto.TeacherReviewResponse;
import vera.lms.models.User;
import vera.lms.services.TeacherSchedulingService;

import java.util.List;

@RestController
public class TeacherController {

    private final TeacherSchedulingService teacherSchedulingService;

    public TeacherController(TeacherSchedulingService teacherSchedulingService) {
        this.teacherSchedulingService = teacherSchedulingService;
    }

    @PostMapping("/api/teacher/availability")
    public ResponseEntity<TeacherAvailabilityResponse> createAvailability(
            @AuthenticationPrincipal User teacher,
            @RequestBody @Valid CreateAvailabilityRequest request) {
        return ResponseEntity.ok(teacherSchedulingService.createAvailability(teacher, request));
    }

    @GetMapping("/api/teacher/students")
    public ResponseEntity<List<TeacherAssignmentResponse>> getTeacherStudents(@AuthenticationPrincipal User teacher) {
        return ResponseEntity.ok(teacherSchedulingService.getTeacherStudents(teacher));
    }

    @GetMapping("/api/teacher/bookings")
    public ResponseEntity<List<TeacherBookingResponse>> getTeacherBookings(
            @AuthenticationPrincipal User teacher,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(teacherSchedulingService.getTeacherBookings(teacher, status));
    }

    @PostMapping("/api/teacher/bookings/{bookingId}/review")
    public ResponseEntity<TeacherReviewResponse> reviewBooking(
            @AuthenticationPrincipal User teacher,
            @PathVariable Long bookingId,
            @RequestBody @Valid ReviewBookingRequest request) {
        return ResponseEntity.ok(teacherSchedulingService.reviewBooking(teacher, bookingId, request));
    }
}
