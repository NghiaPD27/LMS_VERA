package vera.lms.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.LessonDto.CreateLessonRequest;
import vera.lms.dtos.LessonDto.UpdateLessonRequest;
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
import vera.lms.repositories.ProgramRepository;
import vera.lms.repositories.StudentLessonProgressRepository;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class LessonService {

    private final LessonRepository lessonRepository;
    private final ProgramRepository programRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentLessonProgressRepository progressRepository;

    public LessonService(
            LessonRepository lessonRepository,
            ProgramRepository programRepository,
            EnrollmentRepository enrollmentRepository,
            StudentLessonProgressRepository progressRepository) {
        this.lessonRepository = lessonRepository;
        this.programRepository = programRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
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
        }
        return lesson;
    }

    public void deleteLesson(Long id) {
        Lesson lesson = getEditableLesson(id);
        if (progressRepository.countByLessonId(id) > 0) {
            lesson.setStatus(LessonStatus.ARCHIVED);
            lessonRepository.save(lesson);
        } else {
            lessonRepository.delete(lesson);
        }
    }

    @Transactional(readOnly = true)
    public List<Lesson> getLessonsForProgram(Long programId) {
        ensureProgramExists(programId);
        return lessonRepository.findByProgramIdAndStatusNotOrderByLessonNumberAsc(programId, LessonStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public List<Lesson> getLessonsForStudent(Long programId, User student) {
        ensureStudentEnrolled(programId, student);

        List<StudentLessonProgress> progresses = progressRepository.findByStudentId(student.getId());
        return progresses.stream()
                .filter(progress -> progress.getLesson().getProgram().getId().equals(programId))
                .filter(progress -> progress.getLesson().getStatus() == LessonStatus.PUBLISHED)
                .filter(progress -> progress.getStatus() != LessonProgressStatus.LOCKED)
                .map(StudentLessonProgress::getLesson)
                .sorted((left, right) -> Integer.compare(left.getLessonNumber(), right.getLessonNumber()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Lesson getLesson(Long id) {
        return lessonRepository.findByIdAndStatusNot(id, LessonStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + id));
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

        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndProgramIdAndStatus(
                student.getId(), programId, EnrollmentStatus.ACTIVE);
        if (!isEnrolled) {
            throw new ForbiddenException("Student is not enrolled in this program");
        }
    }

    private void syncProgressToActiveEnrollments(Lesson lesson) {
        List<Enrollment> activeEnrollments = enrollmentRepository.findByProgramIdAndStatus(
                lesson.getProgram().getId(), EnrollmentStatus.ACTIVE);
        for (Enrollment enrollment : activeEnrollments) {
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
