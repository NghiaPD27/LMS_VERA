package vera.lms.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.EnrollmentDto.EnrollStudentRequest;
import vera.lms.dtos.EnrollmentDto.AdminEnrollmentResponse;
import vera.lms.dtos.EnrollmentDto.UpdateEnrollmentRequest;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.enums.EnrollmentStatus;
import vera.lms.enums.LessonProgressStatus;
import vera.lms.enums.LessonStatus;
import vera.lms.enums.RoleName;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ConflictException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.*;
import vera.lms.repositories.*;
import vera.lms.utils.PaginationUtils;

import java.util.List;

@Service
@Transactional
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final LessonRepository lessonRepository;
    private final StudentLessonProgressRepository progressRepository;

    @Autowired
    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            ProgramRepository programRepository,
            LessonRepository lessonRepository,
            StudentLessonProgressRepository progressRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.programRepository = programRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
    }

    public Enrollment enrollStudent(EnrollStudentRequest request) {
        User student = userRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id " + request.studentId()));
        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> new ResourceNotFoundException("Program not found with id " + request.programId()));

        if (student.getRole() == null || student.getRole().getName() != RoleName.STUDENT) {
            throw new BadRequestException("Only STUDENT users can be enrolled in a program");
        }

        if (enrollmentRepository.existsByStudentIdAndStatus(student.getId(), EnrollmentStatus.ACTIVE)) {
            throw new ConflictException("Student already has an active enrollment");
        }

        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .program(program)
                .status(EnrollmentStatus.ACTIVE)
                .build();
        enrollment = enrollmentRepository.save(enrollment);

        List<Lesson> lessons = lessonRepository.findByProgramIdAndStatusOrderByLessonNumberAsc(
                program.getId(), LessonStatus.PUBLISHED);
        boolean firstUnlocked = true;
        for (Lesson lesson : lessons) {
            StudentLessonProgressId progressId = StudentLessonProgressId.builder()
                    .studentId(student.getId())
                    .lessonId(lesson.getId())
                    .build();
            StudentLessonProgress progress = StudentLessonProgress.builder()
                    .id(progressId)
                    .student(student)
                    .lesson(lesson)
                    .status(firstUnlocked ? LessonProgressStatus.VIDEO_IN_PROGRESS : LessonProgressStatus.LOCKED)
                    .build();
            progressRepository.save(progress);
            firstUnlocked = false;
        }

        return enrollment;
    }

    public Enrollment updateEnrollmentStatus(Long id, UpdateEnrollmentRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id " + id));

        EnrollmentStatus newStatus;
        try {
            newStatus = EnrollmentStatus.valueOf(request.status().toUpperCase());
        } catch (Exception e) {
            throw new ConflictException("Invalid enrollment status: " + request.status());
        }

        enrollment.setStatus(newStatus);
        return enrollmentRepository.save(enrollment);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> getStudentEnrollments(User student) {
        return enrollmentRepository.findByStudentId(student.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminEnrollmentResponse> getAdminEnrollments(
            String studentId,
            String programId,
            String status,
            Integer page,
            Integer size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("id").descending());
        Page<Enrollment> enrollments = enrollmentRepository.searchAdminEnrollments(
                parseOptionalLong(studentId, "studentId"),
                parseOptionalLong(programId, "programId"),
                parseOptionalStatus(status),
                pageable);
        List<AdminEnrollmentResponse> content = enrollments.getContent().stream()
                .map(this::toAdminEnrollmentResponse)
                .toList();
        return new PageResponse<>(
                content,
                enrollments.getTotalElements(),
                enrollments.getTotalPages(),
                enrollments.getNumber(),
                enrollments.getSize());
    }

    @Transactional(readOnly = true)
    public List<AdminEnrollmentResponse> getAdminStudentEnrollments(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id " + studentId));
        if (student.getRole() == null || student.getRole().getName() != RoleName.STUDENT) {
            throw new ResourceNotFoundException("Student not found with id " + studentId);
        }
        return enrollmentRepository.findByStudentIdOrderByIdDesc(studentId).stream()
                .map(this::toAdminEnrollmentResponse)
                .toList();
    }

    private AdminEnrollmentResponse toAdminEnrollmentResponse(Enrollment enrollment) {
        User student = enrollment.getStudent();
        StudentProfile profile = student.getStudentProfile();
        String studentName = profile != null
                ? (profile.getFirstName() + " " + profile.getLastName()).trim()
                : student.getUsername();
        Program program = enrollment.getProgram();
        return new AdminEnrollmentResponse(
                enrollment.getId(),
                student.getId(),
                studentName,
                student.getEmail(),
                program.getId(),
                program.getName(),
                enrollment.getStatus().name());
    }

    private Long parseOptionalLong(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid " + fieldName + ": " + value);
        }
    }

    private EnrollmentStatus parseOptionalStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return EnrollmentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid enrollment status: " + status);
        }
    }
}
