package vera.lms.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.ReportingDto.*;
import vera.lms.enums.*;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.*;
import vera.lms.repositories.*;
import vera.lms.utils.PaginationUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final UserRepository userRepository;
    private final AccountAccessRepository accountAccessRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CoursePurchaseRepository purchaseRepository;
    private final TeacherBookingRepository teacherBookingRepository;
    private final CheckpointSessionRepository checkpointSessionRepository;
    private final FinalAssessmentSessionRepository finalAssessmentSessionRepository;
    private final StudentLessonProgressRepository progressRepository;
    private final StudentTeacherAssignmentRepository assignmentRepository;

    public ReportingService(
            UserRepository userRepository,
            AccountAccessRepository accountAccessRepository,
            EnrollmentRepository enrollmentRepository,
            CoursePurchaseRepository purchaseRepository,
            TeacherBookingRepository teacherBookingRepository,
            CheckpointSessionRepository checkpointSessionRepository,
            FinalAssessmentSessionRepository finalAssessmentSessionRepository,
            StudentLessonProgressRepository progressRepository,
            StudentTeacherAssignmentRepository assignmentRepository) {
        this.userRepository = userRepository;
        this.accountAccessRepository = accountAccessRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.purchaseRepository = purchaseRepository;
        this.teacherBookingRepository = teacherBookingRepository;
        this.checkpointSessionRepository = checkpointSessionRepository;
        this.finalAssessmentSessionRepository = finalAssessmentSessionRepository;
        this.progressRepository = progressRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public AdminDashboardResponse getDashboard() {
        Instant now = Instant.now();
        return new AdminDashboardResponse(
                userRepository.countByRoleName(RoleName.STUDENT),
                userRepository.countByRoleName(RoleName.TEACHER),
                userRepository.countByRoleName(RoleName.EVALUATOR),
                accountAccessRepository.countByStatus(AccountStatus.ACTIVE),
                accountAccessRepository.countByStatus(AccountStatus.SUSPENDED),
                accountAccessRepository.countByStatus(AccountStatus.EXPIRED),
                enrollmentRepository.count(),
                enrollmentRepository.countByStatus(EnrollmentStatus.ACTIVE),
                enrollmentRepository.countByStatusAndExpiredAtBefore(EnrollmentStatus.ACTIVE, now),
                enrollmentRepository.countByStatus(EnrollmentStatus.COMPLETED),
                enrollmentRepository.countByStatus(EnrollmentStatus.WAITING_FOR_REASSESSMENT),
                purchaseRepository.countByStatus(PurchaseStatus.PENDING),
                purchaseRepository.countByStatus(PurchaseStatus.PAID),
                teacherBookingRepository.countByStatus(BookingStatus.BOOKED),
                checkpointSessionRepository.countByStatus(CheckpointSessionStatus.PENDING),
                finalAssessmentSessionRepository.countByStatus(FinalAssessmentSessionStatus.PENDING));
    }

    public PageResponse<AdminStudentProgressResponse> getStudentProgress(
            Long programId,
            String enrollmentStatus,
            String accountStatus,
            Long teacherId,
            Instant expiryFrom,
            Instant expiryTo,
            String keyword,
            Integer page,
            Integer size) {
        validateRange(expiryFrom, expiryTo);
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("id").descending());
        Page<Enrollment> enrollments = enrollmentRepository.searchAdminStudentProgress(
                programId,
                parseOptionalEnrollmentStatus(enrollmentStatus),
                parseOptionalAccountStatus(accountStatus),
                teacherId,
                expiryFrom,
                expiryTo,
                normalizeKeyword(keyword),
                pageable);
        List<AdminStudentProgressResponse> content = enrollments.getContent().stream()
                .map(this::toStudentProgressResponse)
                .toList();
        return new PageResponse<>(
                content,
                enrollments.getTotalElements(),
                enrollments.getTotalPages(),
                enrollments.getNumber(),
                enrollments.getSize());
    }

    public AdminStudentProgressDetailResponse getStudentProgressDetail(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id " + enrollmentId));
        List<AdminStudentLessonProgressResponse> lessons = progressRepository.findByStudentId(enrollment.getStudent().getId()).stream()
                .filter(progress -> progress.getLesson().getProgram().getId().equals(enrollment.getProgram().getId()))
                .sorted(Comparator.comparing(progress -> progress.getLesson().getLessonNumber()))
                .map(progress -> new AdminStudentLessonProgressResponse(
                        progress.getLesson().getId(),
                        progress.getLesson().getLessonNumber(),
                        progress.getLesson().getName(),
                        progress.getLesson().getStatus().name(),
                        progress.getStatus().name()))
                .toList();
        return new AdminStudentProgressDetailResponse(toStudentProgressResponse(enrollment), lessons);
    }

    private AdminStudentProgressResponse toStudentProgressResponse(Enrollment enrollment) {
        ProgressSnapshot progress = progressSnapshot(enrollment);
        StudentTeacherAssignment assignment = assignmentRepository.findByEnrollmentId(enrollment.getId()).orElse(null);
        User teacher = assignment != null ? assignment.getTeacher() : null;
        User student = enrollment.getStudent();
        AccountAccess access = student.getAccountAccess();
        return new AdminStudentProgressResponse(
                enrollment.getId(),
                student.getId(),
                displayName(student),
                student.getEmail(),
                student.isEnabled(),
                access != null && access.getStatus() != null ? access.getStatus().name() : null,
                enrollment.getProgram().getId(),
                enrollment.getProgram().getName(),
                enrollment.getStatus().name(),
                enrollment.getEnrolledAt(),
                enrollment.getExpiredAt(),
                progress.progressPercent(),
                progress.currentLessonNumber(),
                progress.currentLessonName(),
                progress.currentLessonStatus(),
                progress.nextAction(),
                teacher != null ? teacher.getId() : null,
                teacher != null ? displayName(teacher) : null,
                assignment != null ? assignment.getAssignedAt() : null);
    }

    private ProgressSnapshot progressSnapshot(Enrollment enrollment) {
        List<StudentLessonProgress> progresses = progressRepository.findByStudentId(enrollment.getStudent().getId()).stream()
                .filter(progress -> progress.getLesson().getProgram().getId().equals(enrollment.getProgram().getId()))
                .toList();
        long total = progresses.size();
        long completed = progresses.stream()
                .filter(progress -> progress.getStatus() == LessonProgressStatus.COMPLETED)
                .count();
        StudentLessonProgress current = progresses.stream()
                .filter(progress -> progress.getStatus() != LessonProgressStatus.COMPLETED
                        && progress.getStatus() != LessonProgressStatus.LOCKED)
                .min(Comparator.comparing(progress -> progress.getLesson().getLessonNumber()))
                .orElseGet(() -> progresses.stream()
                        .filter(progress -> progress.getStatus() == LessonProgressStatus.LOCKED)
                        .min(Comparator.comparing(progress -> progress.getLesson().getLessonNumber()))
                        .orElse(null));
        int progressPercent = total == 0 ? 0 : (int) Math.floor((completed * 100.0) / total);
        return new ProgressSnapshot(
                progressPercent,
                current != null ? current.getLesson().getLessonNumber() : null,
                current != null ? current.getLesson().getName() : null,
                current != null ? current.getStatus().name() : null,
                current != null ? nextAction(current.getStatus()) : "COMPLETED");
    }

    private String nextAction(LessonProgressStatus status) {
        return switch (status) {
            case VIDEO_IN_PROGRESS -> "WATCH_VIDEO";
            case QUIZ_AVAILABLE -> "TAKE_QUIZ";
            case WAITING_FOR_TEACHER -> "BOOK_TEACHER";
            case WAITING_FOR_CHECKPOINT -> "WAIT_CHECKPOINT";
            case LOCKED -> "WAIT_UNLOCK";
            case COMPLETED -> "COMPLETED";
        };
    }

    private EnrollmentStatus parseOptionalEnrollmentStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return EnrollmentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid enrollment status: " + status);
        }
    }

    private AccountStatus parseOptionalAccountStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return AccountStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid account status: " + status);
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new BadRequestException("Expiry end must be after expiry start");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return keyword.trim();
    }

    private String displayName(User user) {
        if (user.getStudentProfile() != null) {
            return (user.getStudentProfile().getFirstName() + " " + user.getStudentProfile().getLastName()).trim();
        }
        if (user.getTeacherProfile() != null) {
            return (user.getTeacherProfile().getFirstName() + " " + user.getTeacherProfile().getLastName()).trim();
        }
        return user.getUsername();
    }

    private record ProgressSnapshot(
            Integer progressPercent,
            Integer currentLessonNumber,
            String currentLessonName,
            String currentLessonStatus,
            String nextAction
    ) {}
}
