package vera.lms.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.ReportingDto.AdminDashboardResponse;
import vera.lms.dtos.ReportingDto.AdminStudentProgressDetailResponse;
import vera.lms.dtos.ReportingDto.AdminStudentProgressResponse;
import vera.lms.services.ReportingService;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportingController {

    private final ReportingService reportingService;

    public AdminReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(reportingService.getDashboard());
    }

    @GetMapping("/student-progress")
    public ResponseEntity<PageResponse<AdminStudentProgressResponse>> getStudentProgress(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) String enrollmentStatus,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Instant expiryFrom,
            @RequestParam(required = false) Instant expiryTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(reportingService.getStudentProgress(
                programId,
                enrollmentStatus,
                accountStatus,
                teacherId,
                expiryFrom,
                expiryTo,
                keyword,
                page,
                size));
    }

    @GetMapping("/student-progress/{enrollmentId}")
    public ResponseEntity<AdminStudentProgressDetailResponse> getStudentProgressDetail(
            @PathVariable Long enrollmentId) {
        return ResponseEntity.ok(reportingService.getStudentProgressDetail(enrollmentId));
    }
}
