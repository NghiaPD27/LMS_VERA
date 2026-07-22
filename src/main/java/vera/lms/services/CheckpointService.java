package vera.lms.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.CheckpointDto.*;
import vera.lms.enums.*;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ConflictException;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.*;
import vera.lms.repositories.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class CheckpointService {

    private static final int CHECKPOINT_INTERVAL = 5;
    private static final int MIN_BLOCK_NUMBER = 1;
    private static final int MAX_BLOCK_NUMBER = 4;

    private final CheckpointRepository checkpointRepository;
    private final CheckpointSessionRepository sessionRepository;
    private final CheckpointParticipantRepository participantRepository;
    private final CheckpointResultRepository resultRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final ProgramRepository programRepository;
    private final StudentLessonProgressRepository progressRepository;
    private final TeacherReviewRepository teacherReviewRepository;
    private final UserRepository userRepository;

    public CheckpointService(
            CheckpointRepository checkpointRepository,
            CheckpointSessionRepository sessionRepository,
            CheckpointParticipantRepository participantRepository,
            CheckpointResultRepository resultRepository,
            EnrollmentRepository enrollmentRepository,
            LessonRepository lessonRepository,
            ProgramRepository programRepository,
            StudentLessonProgressRepository progressRepository,
            TeacherReviewRepository teacherReviewRepository,
            UserRepository userRepository) {
        this.checkpointRepository = checkpointRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.resultRepository = resultRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonRepository = lessonRepository;
        this.programRepository = programRepository;
        this.progressRepository = progressRepository;
        this.teacherReviewRepository = teacherReviewRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CheckpointEligibleStudentResponse> getEligibleStudents(
            Long programId,
            Integer blockNumber,
            Instant weekStart,
            Instant weekEnd) {
        validateOptionalBlockNumber(blockNumber);
        validateWeekRange(weekStart, weekEnd);

        List<StudentLessonProgress> progresses = progressRepository.findCheckpointReadyProgresses(
                LessonProgressStatus.WAITING_FOR_CHECKPOINT,
                CHECKPOINT_INTERVAL,
                CHECKPOINT_INTERVAL,
                CHECKPOINT_INTERVAL * MAX_BLOCK_NUMBER,
                programId,
                blockNumber);

        List<CheckpointEligibleStudentResponse> responses = new ArrayList<>();
        for (StudentLessonProgress progress : progresses) {
            Lesson gateLesson = progress.getLesson();
            User student = progress.getStudent();
            Enrollment enrollment = enrollmentRepository
                    .findByStudentIdAndProgramIdAndStatus(student.getId(), gateLesson.getProgram().getId(), EnrollmentStatus.ACTIVE)
                    .orElse(null);
            if (enrollment == null || isExpired(enrollment)) {
                continue;
            }

            Integer progressBlock = gateLesson.getLessonNumber() / CHECKPOINT_INTERVAL;
            Checkpoint checkpoint = checkpointRepository
                    .findByProgramIdAndBlockNumber(gateLesson.getProgram().getId(), progressBlock)
                    .orElse(null);
            if (checkpoint != null && isAlreadyPassedOrPending(enrollment.getId(), checkpoint.getId())) {
                continue;
            }

            Instant eligibleAt = findEligibleAt(enrollment.getId(), gateLesson.getId());
            if (!isInsideWeekRange(eligibleAt, weekStart, weekEnd)) {
                continue;
            }

            responses.add(new CheckpointEligibleStudentResponse(
                    student.getId(),
                    displayName(student),
                    enrollment.getId(),
                    gateLesson.getProgram().getId(),
                    gateLesson.getProgram().getName(),
                    progressBlock,
                    startLessonNumber(progressBlock),
                    gateLesson.getLessonNumber(),
                    nextLessonNumber(progressBlock),
                    gateLesson.getId(),
                    gateLesson.getName(),
                    eligibleAt));
        }
        return responses;
    }

    public CheckpointSessionResponse createSession(CreateCheckpointSessionRequest request) {
        validateBlockNumber(request.blockNumber());
        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> new ResourceNotFoundException("Program not found with id " + request.programId()));
        User evaluator = getEvaluatorUser(request.evaluatorId());
        Checkpoint checkpoint = getOrCreateCheckpoint(program, request.blockNumber());

        CheckpointSession session = CheckpointSession.builder()
                .checkpoint(checkpoint)
                .evaluator(evaluator)
                .scheduledAt(request.scheduledAt())
                .meetLink(normalizeMeetLink(request.meetLink()))
                .build();
        session = sessionRepository.save(session);

        if (request.participantEnrollmentIds() != null && !request.participantEnrollmentIds().isEmpty()) {
            addParticipantsToSession(session, request.participantEnrollmentIds());
        }
        return toSessionResponse(session);
    }

    public CheckpointSessionResponse addParticipants(Long sessionId, AddCheckpointParticipantsRequest request) {
        CheckpointSession session = sessionRepository.findWithCheckpointById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint session not found with id " + sessionId));
        addParticipantsToSession(session, request.enrollmentIds());
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<CheckpointSessionResponse> getEvaluatorSessions(User evaluator) {
        ensureEvaluator(evaluator);
        return sessionRepository.findByEvaluatorIdOrderByScheduledAtDesc(evaluator.getId()).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    public CheckpointResultResponse submitResult(User evaluator, SubmitCheckpointResultRequest request) {
        ensureEvaluator(evaluator);
        CheckpointParticipant participant = participantRepository.findWithDetailsById(request.participantId())
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint participant not found with id " + request.participantId()));
        if (!participant.getSession().getEvaluator().getId().equals(evaluator.getId())) {
            throw new ForbiddenException("Checkpoint session does not belong to current evaluator");
        }
        if (resultRepository.existsByParticipantId(participant.getId())) {
            throw new ConflictException("Checkpoint participant already has a result");
        }

        AssessmentResult assessmentResult = parseAssessmentResult(request.result());
        CheckpointResult result = CheckpointResult.builder()
                .participant(participant)
                .evaluator(evaluator)
                .result(assessmentResult)
                .comment(normalizeBlankToNull(request.comment()))
                .build();
        result = resultRepository.save(result);
        applyResultToProgress(participant, assessmentResult);
        return toResultResponse(result);
    }

    private void addParticipantsToSession(CheckpointSession session, List<Long> enrollmentIds) {
        for (Long enrollmentId : enrollmentIds) {
            Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id " + enrollmentId));
            validateEnrollmentEligibility(enrollment, session.getCheckpoint());
            if (participantRepository.existsBySessionIdAndEnrollmentId(session.getId(), enrollmentId)) {
                throw new ConflictException("Enrollment is already in this checkpoint session");
            }
            if (participantRepository.existsPendingForEnrollmentAndCheckpoint(enrollmentId, session.getCheckpoint().getId())) {
                throw new ConflictException("Enrollment already has a pending checkpoint session");
            }
            CheckpointParticipant participant = CheckpointParticipant.builder()
                    .session(session)
                    .enrollment(enrollment)
                    .student(enrollment.getStudent())
                    .build();
            participantRepository.save(participant);
        }
    }

    private void validateEnrollmentEligibility(Enrollment enrollment, Checkpoint checkpoint) {
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE || isExpired(enrollment)) {
            throw new ForbiddenException("Course enrollment is expired or unavailable");
        }
        if (!enrollment.getProgram().getId().equals(checkpoint.getProgram().getId())) {
            throw new BadRequestException("Enrollment does not belong to checkpoint program");
        }
        if (resultRepository.existsByEnrollmentAndCheckpointAndResult(
                enrollment.getId(), checkpoint.getId(), AssessmentResult.PASS)) {
            throw new ConflictException("Enrollment already passed this checkpoint");
        }

        Lesson gateLesson = findGateLesson(checkpoint);
        StudentLessonProgress progress = progressRepository
                .findByStudentIdAndLessonId(enrollment.getStudent().getId(), gateLesson.getId())
                .orElseThrow(() -> new ForbiddenException("Student is not eligible for this checkpoint"));
        if (progress.getStatus() != LessonProgressStatus.WAITING_FOR_CHECKPOINT) {
            throw new ForbiddenException("Student is not waiting for checkpoint");
        }
    }

    private void applyResultToProgress(CheckpointParticipant participant, AssessmentResult result) {
        Checkpoint checkpoint = participant.getSession().getCheckpoint();
        Lesson gateLesson = findGateLesson(checkpoint);
        StudentLessonProgress gateProgress = progressRepository
                .findByStudentIdAndLessonId(participant.getStudent().getId(), gateLesson.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint gate progress not found"));

        if (result == AssessmentResult.NOT_PASS) {
            gateProgress.setStatus(LessonProgressStatus.WAITING_FOR_CHECKPOINT);
            progressRepository.save(gateProgress);
            return;
        }

        gateProgress.setStatus(LessonProgressStatus.COMPLETED);
        progressRepository.save(gateProgress);
        unlockNextLesson(participant.getStudent().getId(), checkpoint);
    }

    private void unlockNextLesson(Long studentId, Checkpoint checkpoint) {
        lessonRepository.findByProgramIdAndStatusOrderByLessonNumberAsc(
                        checkpoint.getProgram().getId(), LessonStatus.PUBLISHED).stream()
                .filter(lesson -> lesson.getLessonNumber() > checkpoint.getGateLessonNumber())
                .findFirst()
                .flatMap(lesson -> progressRepository.findByStudentIdAndLessonId(studentId, lesson.getId()))
                .ifPresent(progress -> {
                    if (progress.getStatus() == LessonProgressStatus.LOCKED) {
                        progress.setStatus(LessonProgressStatus.VIDEO_IN_PROGRESS);
                        progressRepository.save(progress);
                    }
                });
    }

    private Checkpoint getOrCreateCheckpoint(Program program, int blockNumber) {
        return checkpointRepository.findByProgramIdAndBlockNumber(program.getId(), blockNumber)
                .orElseGet(() -> checkpointRepository.save(Checkpoint.builder()
                        .program(program)
                        .blockNumber(blockNumber)
                        .startLessonNumber(startLessonNumber(blockNumber))
                        .gateLessonNumber(gateLessonNumber(blockNumber))
                        .nextLessonNumber(nextLessonNumber(blockNumber))
                        .build()));
    }

    private Lesson findGateLesson(Checkpoint checkpoint) {
        return lessonRepository.findByProgramIdAndStatusOrderByLessonNumberAsc(
                        checkpoint.getProgram().getId(), LessonStatus.PUBLISHED).stream()
                .filter(lesson -> lesson.getLessonNumber() == checkpoint.getGateLessonNumber())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Checkpoint gate lesson not found for lesson number " + checkpoint.getGateLessonNumber()));
    }

    private boolean isAlreadyPassedOrPending(Long enrollmentId, Long checkpointId) {
        return resultRepository.existsByEnrollmentAndCheckpointAndResult(enrollmentId, checkpointId, AssessmentResult.PASS)
                || participantRepository.existsPendingForEnrollmentAndCheckpoint(enrollmentId, checkpointId);
    }

    private Instant findEligibleAt(Long enrollmentId, Long lessonId) {
        return teacherReviewRepository
                .findTopByBookingEnrollmentIdAndBookingLessonIdAndResultOrderByReviewedAtDesc(
                        enrollmentId, lessonId, TeacherReviewResult.APPROVED)
                .map(TeacherReview::getReviewedAt)
                .orElse(null);
    }

    private boolean isInsideWeekRange(Instant eligibleAt, Instant weekStart, Instant weekEnd) {
        if (weekStart == null && weekEnd == null) {
            return true;
        }
        if (eligibleAt == null) {
            return false;
        }
        return (weekStart == null || !eligibleAt.isBefore(weekStart))
                && (weekEnd == null || eligibleAt.isBefore(weekEnd));
    }

    private CheckpointSessionResponse toSessionResponse(CheckpointSession session) {
        List<CheckpointParticipantResponse> participants = participantRepository
                .findBySessionIdOrderByIdAsc(session.getId()).stream()
                .map(this::toParticipantResponse)
                .toList();
        Checkpoint checkpoint = session.getCheckpoint();
        return new CheckpointSessionResponse(
                session.getId(),
                checkpoint.getId(),
                checkpoint.getProgram().getId(),
                checkpoint.getProgram().getName(),
                checkpoint.getBlockNumber(),
                checkpoint.getStartLessonNumber(),
                checkpoint.getGateLessonNumber(),
                checkpoint.getNextLessonNumber(),
                session.getEvaluator().getId(),
                displayName(session.getEvaluator()),
                session.getScheduledAt(),
                session.getMeetLink(),
                session.getCreatedAt(),
                participants);
    }

    private CheckpointParticipantResponse toParticipantResponse(CheckpointParticipant participant) {
        return new CheckpointParticipantResponse(
                participant.getId(),
                participant.getEnrollment().getId(),
                participant.getStudent().getId(),
                displayName(participant.getStudent()),
                participant.getAddedAt(),
                resultRepository.findByParticipantId(participant.getId())
                        .map(this::toResultResponse)
                        .orElse(null));
    }

    private CheckpointResultResponse toResultResponse(CheckpointResult result) {
        return new CheckpointResultResponse(
                result.getId(),
                result.getParticipant().getId(),
                result.getEvaluator().getId(),
                result.getResult().name(),
                result.getComment(),
                result.getEvaluatedAt());
    }

    private User getEvaluatorUser(Long evaluatorId) {
        User evaluator = userRepository.findById(evaluatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluator not found with id " + evaluatorId));
        ensureEvaluator(evaluator);
        return evaluator;
    }

    private void ensureEvaluator(User evaluator) {
        if (evaluator == null || evaluator.getRole() == null || evaluator.getRole().getName() != RoleName.EVALUATOR) {
            throw new BadRequestException("User must be an EVALUATOR");
        }
    }

    private AssessmentResult parseAssessmentResult(String result) {
        try {
            return AssessmentResult.valueOf(result.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid checkpoint result: " + result);
        }
    }

    private void validateOptionalBlockNumber(Integer blockNumber) {
        if (blockNumber != null) {
            validateBlockNumber(blockNumber);
        }
    }

    private void validateBlockNumber(Integer blockNumber) {
        if (blockNumber == null || blockNumber < MIN_BLOCK_NUMBER || blockNumber > MAX_BLOCK_NUMBER) {
            throw new BadRequestException("Block number must be between 1 and 4");
        }
    }

    private void validateWeekRange(Instant weekStart, Instant weekEnd) {
        if (weekStart != null && weekEnd != null && !weekEnd.isAfter(weekStart)) {
            throw new BadRequestException("Week end must be after week start");
        }
    }

    private boolean isExpired(Enrollment enrollment) {
        return enrollment.getExpiredAt() != null && enrollment.getExpiredAt().isBefore(Instant.now());
    }

    private String normalizeMeetLink(String value) {
        String normalized = normalizeBlankToNull(value);
        if (normalized == null) {
            throw new BadRequestException("Meet link is required");
        }
        return normalized;
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String displayName(User user) {
        if (user.getStudentProfile() != null) {
            return (user.getStudentProfile().getFirstName() + " " + user.getStudentProfile().getLastName()).trim();
        }
        if (user.getEvaluatorProfile() != null) {
            return (user.getEvaluatorProfile().getFirstName() + " " + user.getEvaluatorProfile().getLastName()).trim();
        }
        return user.getUsername();
    }

    private int startLessonNumber(int blockNumber) {
        return ((blockNumber - 1) * CHECKPOINT_INTERVAL) + 1;
    }

    private int gateLessonNumber(int blockNumber) {
        return blockNumber * CHECKPOINT_INTERVAL;
    }

    private int nextLessonNumber(int blockNumber) {
        return gateLessonNumber(blockNumber) + 1;
    }
}
