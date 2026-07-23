package vera.lms.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.configs.SepayProperties;
import vera.lms.dtos.FinalAssessmentDto.*;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.enums.*;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ConflictException;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.*;
import vera.lms.repositories.*;
import vera.lms.utils.PaginationUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class FinalAssessmentService {

    private final FinalAssessmentSessionRepository sessionRepository;
    private final FinalAssessmentParticipantRepository participantRepository;
    private final FinalAssessmentResultRepository resultRepository;
    private final FinalAssessmentRetakePaymentRepository retakePaymentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final StudentLessonProgressRepository progressRepository;
    private final TeacherReviewRepository teacherReviewRepository;
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final AccountAccessRepository accountAccessRepository;
    private final SepayProperties sepayProperties;
    private final AuditService auditService;

    public FinalAssessmentService(
            FinalAssessmentSessionRepository sessionRepository,
            FinalAssessmentParticipantRepository participantRepository,
            FinalAssessmentResultRepository resultRepository,
            FinalAssessmentRetakePaymentRepository retakePaymentRepository,
            EnrollmentRepository enrollmentRepository,
            LessonRepository lessonRepository,
            StudentLessonProgressRepository progressRepository,
            TeacherReviewRepository teacherReviewRepository,
            UserRepository userRepository,
            ProgramRepository programRepository,
            AccountAccessRepository accountAccessRepository,
            SepayProperties sepayProperties,
            AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.resultRepository = resultRepository;
        this.retakePaymentRepository = retakePaymentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.teacherReviewRepository = teacherReviewRepository;
        this.userRepository = userRepository;
        this.programRepository = programRepository;
        this.accountAccessRepository = accountAccessRepository;
        this.sepayProperties = sepayProperties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FinalAssessmentEligibleStudentResponse> getEligibleStudents(
            Long programId,
            Instant weekStart,
            Instant weekEnd) {
        validateWeekRange(weekStart, weekEnd);
        List<FinalAssessmentEligibleStudentResponse> responses = new ArrayList<>();
        for (Enrollment enrollment : enrollmentRepository.findAll()) {
            if (programId != null && !enrollment.getProgram().getId().equals(programId)) {
                continue;
            }
            Eligibility eligibility = eligibilityForEnrollment(enrollment);
            if (!eligibility.eligible() || !isInsideWeekRange(eligibility.eligibleAt(), weekStart, weekEnd)) {
                continue;
            }
            User student = enrollment.getStudent();
            Lesson finalLesson = eligibility.finalLesson();
            responses.add(new FinalAssessmentEligibleStudentResponse(
                    student.getId(),
                    displayName(student),
                    enrollment.getId(),
                    enrollment.getProgram().getId(),
                    enrollment.getProgram().getName(),
                    finalLesson.getId(),
                    finalLesson.getLessonNumber(),
                    finalLesson.getName(),
                    eligibility.retake(),
                    eligibility.retakePayment() != null ? eligibility.retakePayment().getId() : null,
                    eligibility.eligibleAt()));
        }
        responses.sort(Comparator
                .comparing(FinalAssessmentEligibleStudentResponse::programId)
                .thenComparing(FinalAssessmentEligibleStudentResponse::eligibleAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FinalAssessmentEligibleStudentResponse::studentId));
        return responses;
    }

    public FinalAssessmentSessionResponse createSession(CreateFinalAssessmentSessionRequest request) {
        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> new ResourceNotFoundException("Program not found with id " + request.programId()));
        User evaluator = getEvaluatorUser(request.evaluatorId());
        FinalAssessmentSession session = FinalAssessmentSession.builder()
                .program(program)
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

    @Transactional(readOnly = true)
    public PageResponse<FinalAssessmentSessionResponse> getAdminSessions(
            Long programId,
            String status,
            Instant weekStart,
            Instant weekEnd,
            Integer page,
            Integer size) {
        validateWeekRange(weekStart, weekEnd);
        FinalAssessmentSessionStatus sessionStatus = parseOptionalSessionStatus(status);
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("scheduledAt").descending());
        Page<FinalAssessmentSession> sessions = sessionRepository.findAll(
                adminSessionSpecification(programId, sessionStatus, weekStart, weekEnd),
                pageable);
        List<FinalAssessmentSessionResponse> content = sessions.getContent().stream()
                .map(this::toSessionResponse)
                .toList();
        return new PageResponse<>(
                content,
                sessions.getTotalElements(),
                sessions.getTotalPages(),
                sessions.getNumber(),
                sessions.getSize());
    }

    @Transactional(readOnly = true)
    public FinalAssessmentSessionResponse getAdminSession(Long sessionId) {
        FinalAssessmentSession session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Final assessment session not found with id " + sessionId));
        return toSessionResponse(session);
    }

    public FinalAssessmentSessionResponse updateSession(Long sessionId, UpdateFinalAssessmentSessionRequest request) {
        FinalAssessmentSession session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Final assessment session not found with id " + sessionId));
        ensureSessionEditable(session);
        if (request.evaluatorId() != null) {
            session.setEvaluator(getEvaluatorUser(request.evaluatorId()));
        }
        if (request.scheduledAt() != null) {
            session.setScheduledAt(request.scheduledAt());
        }
        if (request.meetLink() != null) {
            session.setMeetLink(normalizeMeetLink(request.meetLink()));
        }
        return toSessionResponse(sessionRepository.save(session));
    }

    public FinalAssessmentSessionResponse updateSessionStatus(Long sessionId, UpdateFinalAssessmentSessionStatusRequest request) {
        FinalAssessmentSession session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Final assessment session not found with id " + sessionId));
        FinalAssessmentSessionStatus status = parseSessionStatus(request.status());
        if (status != FinalAssessmentSessionStatus.CANCELLED) {
            throw new BadRequestException("Only CANCELLED status can be set manually");
        }
        if (hasAnyResult(session.getId())) {
            throw new ConflictException("Final assessment session with results cannot be cancelled");
        }
        session.setStatus(FinalAssessmentSessionStatus.CANCELLED);
        return toSessionResponse(sessionRepository.save(session));
    }

    public FinalAssessmentSessionResponse addParticipants(Long sessionId, AddFinalAssessmentParticipantsRequest request) {
        FinalAssessmentSession session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Final assessment session not found with id " + sessionId));
        addParticipantsToSession(session, request.enrollmentIds());
        return toSessionResponse(session);
    }

    public FinalAssessmentSessionResponse removeParticipant(Long sessionId, Long participantId) {
        FinalAssessmentSession session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Final assessment session not found with id " + sessionId));
        FinalAssessmentParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Final assessment participant not found with id " + participantId));
        if (!participant.getSession().getId().equals(sessionId)) {
            throw new BadRequestException("Final assessment participant does not belong to this session");
        }
        if (resultRepository.existsByParticipantId(participantId)) {
            throw new ConflictException("Final assessment participant with a result cannot be removed");
        }
        if (session.getStatus() == FinalAssessmentSessionStatus.CANCELLED) {
            throw new ConflictException("Cancelled final assessment sessions cannot be edited");
        }
        participantRepository.delete(participant);
        session.setStatus(FinalAssessmentSessionStatus.PENDING);
        return toSessionResponse(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<FinalAssessmentSessionResponse> getEvaluatorSessions(User evaluator) {
        ensureEvaluator(evaluator);
        return sessionRepository.findByEvaluatorIdOrderByScheduledAtDesc(evaluator.getId()).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinalAssessmentSessionResponse getEvaluatorSession(User evaluator, Long sessionId) {
        ensureEvaluator(evaluator);
        FinalAssessmentSession session = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Final assessment session not found with id " + sessionId));
        if (!session.getEvaluator().getId().equals(evaluator.getId())) {
            throw new ForbiddenException("Final assessment session does not belong to current evaluator");
        }
        return toSessionResponse(session);
    }

    public FinalAssessmentResultResponse submitResult(User evaluator, SubmitFinalAssessmentResultRequest request) {
        ensureEvaluator(evaluator);
        FinalAssessmentParticipant participant = participantRepository.findWithDetailsById(request.participantId())
                .orElseThrow(() -> new ResourceNotFoundException("Final assessment participant not found with id " + request.participantId()));
        FinalAssessmentSession session = participant.getSession();
        if (!session.getEvaluator().getId().equals(evaluator.getId())) {
            throw new ForbiddenException("Final assessment session does not belong to current evaluator");
        }
        if (session.getStatus() == FinalAssessmentSessionStatus.CANCELLED) {
            throw new ConflictException("Cancelled final assessment sessions cannot be reviewed");
        }
        if (resultRepository.existsByParticipantId(participant.getId())) {
            throw new ConflictException("Final assessment participant already has a result");
        }

        AssessmentResult assessmentResult = parseAssessmentResult(request.result());
        FinalAssessmentResult result = FinalAssessmentResult.builder()
                .participant(participant)
                .evaluator(evaluator)
                .result(assessmentResult)
                .comment(normalizeBlankToNull(request.comment()))
                .build();
        result = resultRepository.save(result);
        applyResultToEnrollment(participant.getEnrollment(), assessmentResult);
        updateSessionCompletion(session);
        auditService.record(
                AuditAction.FINAL_ASSESSMENT_RESULT_SUBMITTED,
                "FINAL_ASSESSMENT_PARTICIPANT",
                participant.getId(),
                "result=" + assessmentResult.name() + ", enrollmentId=" + participant.getEnrollment().getId()
                        + ", sessionId=" + session.getId());
        return toResultResponse(result);
    }

    @Transactional(readOnly = true)
    public StudentFinalAssessmentStatusResponse getStudentStatus(User student, Long enrollmentId) {
        ensureStudent(student);
        Enrollment enrollment = getStudentEnrollment(student, enrollmentId);
        Eligibility eligibility = eligibilityForEnrollment(enrollment);
        List<FinalAssessmentParticipant> participants = participantRepository.findEnrollmentParticipantsNewestFirst(enrollmentId);
        FinalAssessmentParticipant participant = participants.isEmpty() ? null : participants.get(0);
        FinalAssessmentSession session = participant != null ? participant.getSession() : null;
        FinalAssessmentResult result = latestResult(enrollmentId);
        FinalAssessmentRetakePayment latestPayment = retakePaymentRepository
                .searchStudentPayments(student.getId(), enrollmentId, null).stream()
                .findFirst()
                .orElse(null);
        return new StudentFinalAssessmentStatusResponse(
                enrollment.getId(),
                enrollment.getProgram().getId(),
                enrollment.getProgram().getName(),
                enrollment.getStatus().name(),
                eligibility.eligible(),
                enrollment.getStatus() == EnrollmentStatus.WAITING_FOR_REASSESSMENT,
                session != null ? session.getId() : null,
                participant != null ? participant.getId() : null,
                session != null ? session.getStatus().name() : null,
                session != null ? session.getScheduledAt() : null,
                session != null ? session.getMeetLink() : null,
                session != null ? session.getEvaluator().getId() : null,
                session != null ? displayName(session.getEvaluator()) : null,
                result != null ? result.getResult().name() : null,
                result != null ? result.getComment() : null,
                result != null ? result.getEvaluatedAt() : null,
                latestPayment != null ? toRetakePaymentResponse(latestPayment) : null);
    }

    public FinalAssessmentRetakePaymentResponse createRetakePayment(
            User student,
            CreateFinalAssessmentRetakePaymentRequest request) {
        ensureStudent(student);
        Enrollment enrollment = getStudentEnrollment(student, request.enrollmentId());
        if (enrollment.getStatus() != EnrollmentStatus.WAITING_FOR_REASSESSMENT) {
            throw new ConflictException("Enrollment is not waiting for final assessment reassessment");
        }
        if (resultRepository.existsByEnrollmentAndResult(enrollment.getId(), AssessmentResult.PASS)) {
            throw new ConflictException("Enrollment already passed final assessment");
        }
        if (retakePaymentRepository.existsByEnrollmentIdAndStatus(enrollment.getId(), PurchaseStatus.PENDING)) {
            throw new ConflictException("Enrollment already has a pending final assessment retake payment");
        }
        BigDecimal amount = enrollment.getProgram().getFinalAssessmentRetakePrice();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Final assessment retake price is not configured");
        }

        FinalAssessmentRetakePayment payment = FinalAssessmentRetakePayment.builder()
                .enrollment(enrollment)
                .student(student)
                .program(enrollment.getProgram())
                .amount(amount)
                .currency(enrollment.getProgram().getCurrency())
                .status(PurchaseStatus.PENDING)
                .paymentProvider("SEPAY")
                .build();
        payment = retakePaymentRepository.save(payment);
        payment.setPaymentCode(buildRetakePaymentCode(payment.getId()));
        payment.setPaymentContent(payment.getPaymentCode());
        payment.setPaymentQrUrl(buildRetakePaymentQrUrl(payment));
        return toRetakePaymentResponse(retakePaymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public List<FinalAssessmentRetakePaymentResponse> getStudentRetakePayments(
            User student,
            Long enrollmentId,
            String status) {
        ensureStudent(student);
        if (enrollmentId != null) {
            getStudentEnrollment(student, enrollmentId);
        }
        PurchaseStatus paymentStatus = parseOptionalPaymentStatus(status);
        return retakePaymentRepository.searchStudentPayments(student.getId(), enrollmentId, paymentStatus).stream()
                .map(this::toRetakePaymentResponse)
                .toList();
    }

    public FinalAssessmentRetakePaymentResponse markRetakePaymentPaidFromSepay(
            FinalAssessmentRetakePayment payment,
            String providerTransactionId,
            String providerReferenceCode,
            Instant paidAt) {
        if (payment.getStatus() == PurchaseStatus.PAID) {
            return toRetakePaymentResponse(payment);
        }
        if (payment.getStatus() != PurchaseStatus.PENDING) {
            throw new ConflictException("Only pending final assessment retake payments can be marked paid");
        }
        payment.setStatus(PurchaseStatus.PAID);
        payment.setPaidAt(paidAt != null ? paidAt : Instant.now());
        payment.setProviderTransactionId(providerTransactionId);
        payment.setProviderReferenceCode(providerReferenceCode);
        return toRetakePaymentResponse(retakePaymentRepository.save(payment));
    }

    private void addParticipantsToSession(FinalAssessmentSession session, List<Long> enrollmentIds) {
        if (session.getStatus() != FinalAssessmentSessionStatus.PENDING) {
            throw new ConflictException("Only pending final assessment sessions can be edited");
        }
        for (Long enrollmentId : enrollmentIds) {
            Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id " + enrollmentId));
            if (!enrollment.getProgram().getId().equals(session.getProgram().getId())) {
                throw new BadRequestException("Enrollment does not belong to final assessment session program");
            }
            if (participantRepository.existsBySessionIdAndEnrollmentId(session.getId(), enrollmentId)) {
                throw new ConflictException("Enrollment is already in this final assessment session");
            }
            if (participantRepository.existsPendingForEnrollment(enrollmentId)) {
                throw new ConflictException("Enrollment already has a pending final assessment session");
            }
            Eligibility eligibility = eligibilityForEnrollment(enrollment);
            if (!eligibility.eligible()) {
                throw new ForbiddenException("Student is not eligible for final assessment");
            }
            participantRepository.save(FinalAssessmentParticipant.builder()
                    .session(session)
                    .enrollment(enrollment)
                    .student(enrollment.getStudent())
                    .retakePayment(eligibility.retakePayment())
                    .build());
        }
    }

    private Eligibility eligibilityForEnrollment(Enrollment enrollment) {
        if (enrollment == null || !isAccountActive(enrollment.getStudent()) || isExpired(enrollment)) {
            return Eligibility.notEligible();
        }
        if (resultRepository.existsByEnrollmentAndResult(enrollment.getId(), AssessmentResult.PASS)
                || participantRepository.existsPendingForEnrollment(enrollment.getId())) {
            return Eligibility.notEligible();
        }

        Lesson finalLesson = findFinalPublishedLesson(enrollment.getProgram());
        if (finalLesson == null || !hasCompletedAllPublishedLessons(enrollment) || !isFinalLessonTeacherApproved(enrollment, finalLesson)) {
            return Eligibility.notEligible();
        }

        if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
            Instant eligibleAt = teacherReviewRepository
                    .findTopByBookingEnrollmentIdAndBookingLessonIdAndResultOrderByReviewedAtDesc(
                            enrollment.getId(), finalLesson.getId(), TeacherReviewResult.APPROVED)
                    .map(TeacherReview::getReviewedAt)
                    .orElse(null);
            return new Eligibility(true, false, null, finalLesson, eligibleAt);
        }

        if (enrollment.getStatus() == EnrollmentStatus.WAITING_FOR_REASSESSMENT) {
            List<FinalAssessmentRetakePayment> unusedPaidPayments =
                    retakePaymentRepository.findUnusedPaidPayments(enrollment.getId());
            if (unusedPaidPayments.isEmpty()) {
                return Eligibility.notEligible();
            }
            FinalAssessmentRetakePayment payment = unusedPaidPayments.get(0);
            return new Eligibility(true, true, payment, finalLesson, payment.getPaidAt());
        }

        return Eligibility.notEligible();
    }

    private boolean hasCompletedAllPublishedLessons(Enrollment enrollment) {
        List<Lesson> lessons = lessonRepository.findByProgramIdAndStatusOrderByLessonNumberAsc(
                enrollment.getProgram().getId(), LessonStatus.PUBLISHED);
        if (lessons.isEmpty()) {
            return false;
        }
        for (Lesson lesson : lessons) {
            StudentLessonProgress progress = progressRepository
                    .findByStudentIdAndLessonId(enrollment.getStudent().getId(), lesson.getId())
                    .orElse(null);
            if (progress == null || progress.getStatus() != LessonProgressStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private boolean isFinalLessonTeacherApproved(Enrollment enrollment, Lesson finalLesson) {
        return teacherReviewRepository
                .findTopByBookingEnrollmentIdAndBookingLessonIdAndResultOrderByReviewedAtDesc(
                        enrollment.getId(), finalLesson.getId(), TeacherReviewResult.APPROVED)
                .isPresent();
    }

    private Lesson findFinalPublishedLesson(Program program) {
        return lessonRepository.findByProgramIdAndStatusOrderByLessonNumberAsc(program.getId(), LessonStatus.PUBLISHED).stream()
                .max(Comparator.comparing(Lesson::getLessonNumber))
                .orElse(null);
    }

    private void applyResultToEnrollment(Enrollment enrollment, AssessmentResult result) {
        enrollment.setStatus(result == AssessmentResult.PASS
                ? EnrollmentStatus.COMPLETED
                : EnrollmentStatus.WAITING_FOR_REASSESSMENT);
        enrollmentRepository.save(enrollment);
    }

    private FinalAssessmentRetakePaymentResponse toRetakePaymentResponse(FinalAssessmentRetakePayment payment) {
        User student = payment.getStudent();
        return new FinalAssessmentRetakePaymentResponse(
                payment.getId(),
                payment.getEnrollment().getId(),
                student.getId(),
                displayName(student),
                payment.getProgram().getId(),
                payment.getProgram().getName(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getPaymentCode(),
                payment.getPaymentQrUrl(),
                payment.getPaymentProvider(),
                payment.getPaymentContent(),
                payment.getCreatedAt(),
                payment.getPaidAt());
    }

    private FinalAssessmentSessionResponse toSessionResponse(FinalAssessmentSession session) {
        List<FinalAssessmentParticipantResponse> participants = participantRepository
                .findBySessionIdOrderByIdAsc(session.getId()).stream()
                .map(this::toParticipantResponse)
                .toList();
        int resultSubmittedCount = (int) resultRepository.countByParticipantSessionId(session.getId());
        return new FinalAssessmentSessionResponse(
                session.getId(),
                session.getProgram().getId(),
                session.getProgram().getName(),
                session.getEvaluator().getId(),
                displayName(session.getEvaluator()),
                session.getScheduledAt(),
                session.getMeetLink(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                participants.size(),
                resultSubmittedCount,
                session.getStatus() == FinalAssessmentSessionStatus.PENDING && resultSubmittedCount == 0,
                participants);
    }

    private FinalAssessmentParticipantResponse toParticipantResponse(FinalAssessmentParticipant participant) {
        return new FinalAssessmentParticipantResponse(
                participant.getId(),
                participant.getEnrollment().getId(),
                participant.getStudent().getId(),
                displayName(participant.getStudent()),
                participant.getRetakePayment() != null,
                participant.getRetakePayment() != null ? participant.getRetakePayment().getId() : null,
                participant.getAddedAt(),
                resultRepository.findByParticipantId(participant.getId())
                        .map(this::toResultResponse)
                        .orElse(null));
    }

    private FinalAssessmentResultResponse toResultResponse(FinalAssessmentResult result) {
        return new FinalAssessmentResultResponse(
                result.getId(),
                result.getParticipant().getId(),
                result.getEvaluator().getId(),
                result.getResult().name(),
                result.getComment(),
                result.getEvaluatedAt());
    }

    private FinalAssessmentResult latestResult(Long enrollmentId) {
        List<FinalAssessmentResult> results = resultRepository.findByEnrollmentIdOrderByEvaluatedAtDesc(enrollmentId);
        return results.isEmpty() ? null : results.get(0);
    }

    private Specification<FinalAssessmentSession> adminSessionSpecification(
            Long programId,
            FinalAssessmentSessionStatus status,
            Instant weekStart,
            Instant weekEnd) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (programId != null) {
                predicates.add(criteriaBuilder.equal(root.get("program").get("id"), programId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (weekStart != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("scheduledAt"), weekStart));
            }
            if (weekEnd != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("scheduledAt"), weekEnd));
            }
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private User getEvaluatorUser(Long evaluatorId) {
        User evaluator = userRepository.findById(evaluatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluator not found with id " + evaluatorId));
        ensureEvaluator(evaluator);
        return evaluator;
    }

    private Enrollment getStudentEnrollment(User student, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id " + enrollmentId));
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new ForbiddenException("Enrollment does not belong to current student");
        }
        return enrollment;
    }

    private void ensureSessionEditable(FinalAssessmentSession session) {
        if (session.getStatus() != FinalAssessmentSessionStatus.PENDING) {
            throw new ConflictException("Only pending final assessment sessions can be edited");
        }
        if (hasAnyResult(session.getId())) {
            throw new ConflictException("Final assessment session with results cannot be edited");
        }
    }

    private boolean hasAnyResult(Long sessionId) {
        return resultRepository.countByParticipantSessionId(sessionId) > 0;
    }

    private void updateSessionCompletion(FinalAssessmentSession session) {
        long participants = participantRepository.countBySessionId(session.getId());
        long results = resultRepository.countByParticipantSessionId(session.getId());
        if (participants > 0 && participants == results) {
            session.setStatus(FinalAssessmentSessionStatus.COMPLETED);
            sessionRepository.save(session);
        }
    }

    private boolean isAccountActive(User user) {
        if (user == null || !user.isEnabled()) {
            return false;
        }
        return accountAccessRepository.findById(user.getId())
                .map(access -> access.getStatus() == AccountStatus.ACTIVE)
                .orElse(false);
    }

    private boolean isExpired(Enrollment enrollment) {
        return enrollment.getExpiredAt() != null && enrollment.getExpiredAt().isBefore(Instant.now());
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

    private void validateWeekRange(Instant weekStart, Instant weekEnd) {
        if (weekStart != null && weekEnd != null && !weekEnd.isAfter(weekStart)) {
            throw new BadRequestException("Week end must be after week start");
        }
    }

    private FinalAssessmentSessionStatus parseOptionalSessionStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        return parseSessionStatus(status);
    }

    private FinalAssessmentSessionStatus parseSessionStatus(String status) {
        try {
            return FinalAssessmentSessionStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid final assessment session status: " + status);
        }
    }

    private AssessmentResult parseAssessmentResult(String result) {
        try {
            return AssessmentResult.valueOf(result.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid final assessment result: " + result);
        }
    }

    private PurchaseStatus parseOptionalPaymentStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return PurchaseStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid final assessment retake payment status: " + status);
        }
    }

    private void ensureEvaluator(User evaluator) {
        if (evaluator == null || evaluator.getRole() == null || evaluator.getRole().getName() != RoleName.EVALUATOR) {
            throw new BadRequestException("User must be an EVALUATOR");
        }
    }

    private void ensureStudent(User student) {
        if (student == null || student.getRole() == null || student.getRole().getName() != RoleName.STUDENT) {
            throw new BadRequestException("Only STUDENT users can access final assessment student APIs");
        }
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
        if (user.getTeacherProfile() != null) {
            return (user.getTeacherProfile().getFirstName() + " " + user.getTeacherProfile().getLastName()).trim();
        }
        return user.getUsername();
    }

    private String buildRetakePaymentCode(Long paymentId) {
        return sepayProperties.retakePaymentCodePrefixOrDefault() + paymentId;
    }

    private String buildRetakePaymentQrUrl(FinalAssessmentRetakePayment payment) {
        String amount = toVndAmount(payment.getAmount());
        String description = URLEncoder.encode(payment.getPaymentContent(), StandardCharsets.UTF_8);
        return sepayProperties.qrBaseUrlOrDefault()
                + "?acc=" + encode(sepayProperties.bankAccount())
                + "&bank=" + encode(sepayProperties.bankCode())
                + "&amount=" + amount
                + "&des=" + description;
    }

    private String toVndAmount(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException e) {
            throw new BadRequestException("SePay payment amount must be a whole VND amount");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private record Eligibility(
            boolean eligible,
            boolean retake,
            FinalAssessmentRetakePayment retakePayment,
            Lesson finalLesson,
            Instant eligibleAt) {
        static Eligibility notEligible() {
            return new Eligibility(false, false, null, null, null);
        }
    }
}
