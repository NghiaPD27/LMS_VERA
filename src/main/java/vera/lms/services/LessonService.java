package vera.lms.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.LessonDto.CreateLessonRequest;
import vera.lms.dtos.LessonDto.LessonResponse;
import vera.lms.dtos.LessonDto.UpdateLessonRequest;
import vera.lms.enums.AuditAction;
import vera.lms.enums.EnrollmentStatus;
import vera.lms.enums.LessonProgressStatus;
import vera.lms.enums.LessonStatus;
import vera.lms.enums.RoleName;
import vera.lms.exceptions.ConflictException;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.Enrollment;
import vera.lms.models.Lesson;
import vera.lms.models.Program;
import vera.lms.models.StudentLessonProgress;
import vera.lms.models.StudentLessonProgressId;
import vera.lms.models.User;
import vera.lms.repositories.EnrollmentRepository;
import vera.lms.repositories.LessonRepository;
import vera.lms.repositories.LessonVideoRepository;
import vera.lms.repositories.ProgramRepository;
import vera.lms.repositories.QuizRepository;
import vera.lms.repositories.StudentLessonProgressRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class LessonService {

    private final LessonRepository lessonRepository;
    private final ProgramRepository programRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentLessonProgressRepository progressRepository;
    private final LessonVideoRepository lessonVideoRepository;
    private final QuizRepository quizRepository;
    private final AuditService auditService;

    public LessonService(
            LessonRepository lessonRepository,
            ProgramRepository programRepository,
            EnrollmentRepository enrollmentRepository,
            StudentLessonProgressRepository progressRepository,
            LessonVideoRepository lessonVideoRepository,
            QuizRepository quizRepository,
            AuditService auditService) {
        this.lessonRepository = lessonRepository;
        this.programRepository = programRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.lessonVideoRepository = lessonVideoRepository;
        this.quizRepository = quizRepository;
        this.auditService = auditService;
    }

    public Lesson createLesson(Long programId, CreateLessonRequest request) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found with id " + programId));

        ensureLessonNumberAvailable(program.getId(), request.lessonNumber(), null);

        Lesson lesson = Lesson.builder()
                .program(program)
                .name(request.name())
                .lessonNumber(request.lessonNumber())
                .content(request.content())
                .status(LessonStatus.DRAFT)
                .build();

        return lessonRepository.save(lesson);
    }

    public Lesson updateLesson(Long id, UpdateLessonRequest request) {
        Lesson lesson = getEditableLesson(id);

        if (lesson.getLessonNumber() != request.lessonNumber()) {
            if (progressRepository.existsByLessonIdAndStatus(id, LessonProgressStatus.COMPLETED)) {
                throw new ConflictException("Cannot change lesson number as some students have already completed this lesson");
            }
            ensureLessonNumberAvailable(lesson.getProgram().getId(), request.lessonNumber(), id);
        }

        lesson.setName(request.name());
        lesson.setLessonNumber(request.lessonNumber());
        lesson.setContent(request.content());
        return lessonRepository.save(lesson);
    }

    public Lesson publishLesson(Long id) {
        Lesson lesson = getEditableLesson(id);
        if (lesson.getStatus() != LessonStatus.PUBLISHED) {
            lesson.setStatus(LessonStatus.PUBLISHED);
            lesson = lessonRepository.save(lesson);
            syncProgressToActiveEnrollments(lesson);
            auditService.record(AuditAction.LESSON_PUBLISHED, "LESSON", lesson.getId(), "programId=" + lesson.getProgram().getId());
        }
        return lesson;
    }

    public void deleteLesson(Long id) {
        Lesson lesson = getEditableLesson(id);
        if (progressRepository.countByLessonId(id) > 0) {
            lesson.setStatus(LessonStatus.ARCHIVED);
            lessonRepository.save(lesson);
            auditService.record(AuditAction.LESSON_ARCHIVED, "LESSON", lesson.getId(), "Archived instead of deleted because progress exists");
        } else {
            auditService.record(AuditAction.LESSON_DELETED, "LESSON", lesson.getId(), "Hard deleted lesson without progress");
            lessonRepository.delete(lesson);
        }
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> getLessonsForProgram(Long programId) {
        ensureProgramExists(programId);
        return lessonRepository.findByProgramIdAndStatusNotOrderByLessonNumberAsc(programId, LessonStatus.ARCHIVED).stream()
                .map(lesson -> toLessonResponse(lesson, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> getLessonsForStudent(Long programId, User student) {
        ensureStudentEnrolled(programId, student);

        List<Lesson> lessons = lessonRepository.findByProgramIdAndStatusOrderByLessonNumberAsc(
                programId, LessonStatus.PUBLISHED);
        Map<Long, LessonProgressStatus> progressByLessonId = progressRepository.findByStudentId(student.getId()).stream()
                .filter(progress -> progress.getLesson().getProgram().getId().equals(programId))
                .collect(Collectors.toMap(
                        progress -> progress.getLesson().getId(),
                        StudentLessonProgress::getStatus,
                        (current, ignored) -> current));

        return lessons.stream()
                .map(lesson -> toStudentLessonResponse(
                        lesson,
                        progressByLessonId.getOrDefault(lesson.getId(), LessonProgressStatus.LOCKED)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Lesson getLesson(Long id) {
        return lessonRepository.findByIdAndStatusNot(id, LessonStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + id));
    }

    @Transactional(readOnly = true)
    public LessonResponse getLessonResponse(Long id) {
        return toLessonResponse(getLesson(id), null);
    }

    private Lesson getEditableLesson(Long id) {
        return lessonRepository.findByIdAndStatusNot(id, LessonStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + id));
    }

    private void ensureProgramExists(Long programId) {
        if (!programRepository.existsById(programId)) {
            throw new ResourceNotFoundException("Program not found with id " + programId);
        }
    }

    private void ensureLessonNumberAvailable(Long programId, int lessonNumber, Long currentLessonId) {
        boolean exists = currentLessonId == null
                ? lessonRepository.existsByProgramIdAndLessonNumberAndStatusNot(programId, lessonNumber, LessonStatus.ARCHIVED)
                : lessonRepository.existsByProgramIdAndLessonNumberAndStatusNotAndIdNot(
                        programId, lessonNumber, LessonStatus.ARCHIVED, currentLessonId);
        if (exists) {
            throw new ConflictException("Lesson number already exists in this program");
        }
    }

    private void ensureStudentEnrolled(Long programId, User student) {
        if (student == null || student.getRole() == null || student.getRole().getName() != RoleName.STUDENT) {
            throw new ForbiddenException("Only students can access lesson progress");
        }

        boolean isEnrolled = enrollmentRepository.existsAccessibleEnrollment(
                student.getId(), programId, EnrollmentStatus.ACTIVE, Instant.now());
        if (!isEnrolled) {
            throw new ForbiddenException("Course enrollment is expired or unavailable");
        }
    }

    private LessonResponse toStudentLessonResponse(Lesson lesson, LessonProgressStatus progressStatus) {
        return toLessonResponse(lesson, progressStatus);
    }

    private LessonResponse toLessonResponse(Lesson lesson, LessonProgressStatus progressStatus) {
        var video = lessonVideoRepository.findByLessonId(lesson.getId()).orElse(null);
        var quiz = quizRepository.findByLessonId(lesson.getId()).orElse(null);
        return new LessonResponse(
                lesson.getId(),
                lesson.getProgram().getId(),
                lesson.getName(),
                lesson.getLessonNumber(),
                lesson.getContent(),
                lesson.getStatus().name(),
                progressStatus != null ? progressStatus.name() : null,
                progressStatus != null ? progressStatus == LessonProgressStatus.LOCKED : null,
                video != null,
                video != null ? video.getStatus().name() : null,
                video != null ? video.getDurationSeconds() : null,
                quiz != null,
                quiz != null ? quiz.getQuestions().size() : 0);
    }

    private void syncProgressToActiveEnrollments(Lesson lesson) {
        List<Enrollment> activeEnrollments = enrollmentRepository.findByProgramIdAndStatus(
                lesson.getProgram().getId(), EnrollmentStatus.ACTIVE);
        Instant now = Instant.now();
        for (Enrollment enrollment : activeEnrollments) {
            if (enrollment.getExpiredAt() != null && enrollment.getExpiredAt().isBefore(now)) {
                continue;
            }
            createProgressIfMissing(enrollment.getStudent(), lesson);
        }
    }

    private void createProgressIfMissing(User student, Lesson lesson) {
        StudentLessonProgressId progressId = StudentLessonProgressId.builder()
                .studentId(student.getId())
                .lessonId(lesson.getId())
                .build();

        if (progressRepository.existsById(progressId)) {
            return;
        }

        StudentLessonProgress progress = StudentLessonProgress.builder()
                .id(progressId)
                .student(student)
                .lesson(lesson)
                .status(determineInitialStatus(student.getId(), lesson))
                .build();
        progressRepository.save(progress);
    }

    private LessonProgressStatus determineInitialStatus(Long studentId, Lesson lesson) {
        List<Lesson> publishedLessons = lessonRepository.findByProgramIdAndStatusOrderByLessonNumberAsc(
                lesson.getProgram().getId(), LessonStatus.PUBLISHED);

        Optional<Lesson> previousLesson = publishedLessons.stream()
                .filter(candidate -> candidate.getLessonNumber() < lesson.getLessonNumber())
                .reduce((first, second) -> second);

        if (previousLesson.isEmpty()) {
            return LessonProgressStatus.VIDEO_IN_PROGRESS;
        }

        return progressRepository.findByStudentIdAndLessonId(studentId, previousLesson.get().getId())
                .filter(progress -> progress.getStatus() == LessonProgressStatus.COMPLETED)
                .map(progress -> LessonProgressStatus.VIDEO_IN_PROGRESS)
                .orElse(LessonProgressStatus.LOCKED);
    }
}
