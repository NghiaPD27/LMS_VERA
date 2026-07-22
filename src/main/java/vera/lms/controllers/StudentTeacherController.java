package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.TeacherDto.CreateBookingRequest;
import vera.lms.dtos.TeacherDto.TeacherBookingResponse;
import vera.lms.dtos.TeacherDto.TeacherSlotResponse;
import vera.lms.models.User;
import vera.lms.services.TeacherSchedulingService;

import java.util.List;

@RestController
public class StudentTeacherController {

    private final TeacherSchedulingService teacherSchedulingService;

    public StudentTeacherController(TeacherSchedulingService teacherSchedulingService) {
        this.teacherSchedulingService = teacherSchedulingService;
    }

    @GetMapping("/api/student/teacher-slots")
    public ResponseEntity<List<TeacherSlotResponse>> getTeacherSlots(
            @AuthenticationPrincipal User student,
            @RequestParam Long lessonId) {
        return ResponseEntity.ok(teacherSchedulingService.getStudentTeacherSlots(student, lessonId));
    }

    @PostMapping("/api/student/bookings")
    public ResponseEntity<TeacherBookingResponse> createBooking(
            @AuthenticationPrincipal User student,
            @RequestBody @Valid CreateBookingRequest request) {
        return ResponseEntity.ok(teacherSchedulingService.createBooking(student, request));
    }

    @GetMapping("/api/student/bookings")
    public ResponseEntity<List<TeacherBookingResponse>> getBookings(
            @AuthenticationPrincipal User student,
            @RequestParam(required = false) Long lessonId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(teacherSchedulingService.getStudentBookings(student, lessonId, status));
    }

    @PatchMapping("/api/student/bookings/{id}/cancel")
    public ResponseEntity<TeacherBookingResponse> cancelBooking(
            @AuthenticationPrincipal User student,
            @PathVariable Long id) {
        return ResponseEntity.ok(teacherSchedulingService.cancelStudentBooking(student, id));
    }
}
