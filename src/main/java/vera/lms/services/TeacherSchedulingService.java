package vera.lms.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.TeacherDto.*;
import vera.lms.enums.*;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ConflictException;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.*;
import vera.lms.repositories.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class TeacherSchedulingService {

    private static final Duration SESSION_DURATION = Duration.ofHours(1);
    private static final int CHECKPOINT_INTERVAL = 5;

    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final StudentLessonProgressRepository progressRepository;
    private final StudentTeacherAssignmentRepository assignmentRepository;
    private final TeacherAvailabilityRepository availabilityRepository;
    private final TeacherBookingRepository bookingRepository;
    private final TeacherReviewRepository reviewRepository;
    private final TeacherCompensationConfigRepository compensationRepository;
    private final TeacherEarningRepository earningRepository;

    public TeacherSchedulingService(
            EnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            LessonRepository lessonRepository,
            StudentLessonProgressRepository progressRepository,
            StudentTeacherAssignmentRepository assignmentRepository,
            TeacherAvailabilityRepository availabilityRepository,
            TeacherBookingRepository bookingRepository,
            TeacherReviewRepository reviewRepository,
            TeacherCompensationConfigRepository compensationRepository,
            TeacherEarningRepository earningRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.assignmentRepository = assignmentRepository;
        this.availabilityRepository = availabilityRepository;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.compensationRepository = compensationRepository;
        this.earningRepository = earningRepository;
    }

    public TeacherAssignmentResponse assignTeacher(Long enrollmentId, AssignTeacherRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id " + enrollmentId));
        User teacher = getTeacherUser(request.teacherId());

        StudentTeacherAssignment assignment = assignmentRepository.findByEnrollmentId(enrollmentId)
                .orElseGet(() -> StudentTeacherAssignment.builder()
                        .enrollment(enrollment)
                        .build());
        assignment.setTeacher(teacher);
        assignment.setAssignedAt(Instant.now());
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    public TeacherCompensationResponse upsertCompensation(Long teacherId, UpsertTeacherCompensationRequest request) {
        User teacher = getTeacherUser(teacherId);
        TeacherCompensationConfig config = compensationRepository.findByTeacherId(teacherId)
                .orElseGet(() -> TeacherCompensationConfig.builder()
                        .teacher(teacher)
                        .build());
        config.setAmountPerSession(request.amountPerSession());
        config.setCurrency(normalizeCurrency(request.currency()));
        return toCompensationResponse(compensationRepository.save(config));
    }

    @Transactional(readOnly = true)
    public TeacherEarningsSummaryResponse getTeacherEarnings(Long teacherId) {
        getTeacherUser(teacherId);
        List<TeacherEarningResponse> earnings = earningRepository.findByTeacherIdOrderByEarnedAtDesc(teacherId).stream()
                .map(this::toEarningResponse)
                .toList();
        String currency = earnings.isEmpty() ? "VND" : earnings.get(0).currency();
        return new TeacherEarningsSummaryResponse(
                teacherId,
                earningRepository.sumByTeacherId(teacherId),
                currency,
                earnings);
    }

    public TeacherAvailabilityResponse createAvailability(User teacher, CreateAvailabilityRequest request) {
        ensureTeacher(teacher);
        validateAvailabilityRange(request.startAt(), request.endAt());
        if (availabilityRepository.existsOverlappingAvailability(teacher.getId(), request.startAt(), request.endAt())) {
            throw new ConflictException("Teacher availability overlaps an existing availability range");
        }

        TeacherAvailability availability = TeacherAvailability.builder()
                .teacher(teacher)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .build();
        return toAvailabilityResponse(availabilityRepository.save(availability));
    }

    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> getTeacherStudents(User teacher) {
        ensureTeacher(teacher);
        return assignmentRepository.findByTeacherId(teacher.getId()).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherBookingResponse> getTeacherBookings(User teacher, String status) {
        ensureTeacher(teacher);
        if (status == null || status.trim().isEmpty()) {
            return bookingRepository.findByTeacherIdOrderByStartAtDesc(teacher.getId()).stream()
                    .map(this::toBookingResponse)
                    .toList();
        }
        BookingStatus bookingStatus = parseBookingStatus(status);
        return bookingRepository.findByTeacherIdAndStatusOrderByStartAtDesc(teacher.getId(), bookingStatus).stream()
                .map(this::toBookingResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherSlotResponse> getStudentTeacherSlots(User student, Long lessonId) {
        StudentLessonAccess access = ensureStudentCanBookTeacher(student, lessonId);
        StudentTeacherAssignment assignment = getAssignmentForEnrollment(access.enrollment().getId());
        Long teacherId = assignment.getTeacher().getId();
        Instant now = Instant.now();

        List<TeacherBooking> bookedSlots = bookingRepository.findTeacherBookingsFrom(teacherId, BookingStatus.BOOKED, now);
        List<TeacherSlotResponse> slots = new ArrayList<>();
        for (TeacherAvailability availability : availabilityRepository.findFutureAvailability(teacherId, now)) {
            Instant slotStart = availability.getStartAt();
            while (!slotStart.plus(SESSION_DURATION).isAfter(availability.getEndAt())) {
                Instant slotEnd = slotStart.plus(SESSION_DURATION);
                if (!slotStart.isBefore(now) && isSlotOpen(slotStart, bookedSlots)) {
                    slots.add(new TeacherSlotResponse(
                            teacherId,
                            displayName(assignment.getTeacher()),
                            availability.getId(),
                            slotStart,
                            slotEnd));
                }
                slotStart = slotEnd;
            }
        }
        return slots;
    }

    public TeacherBookingResponse createBooking(User student, CreateBookingRequest request) {
        StudentLessonAccess access = ensureStudentCanBookTeacher(student, request.lessonId());
        StudentTeacherAssignment assignment = getAssignmentForEnrollment(access.enrollment().getId());
        Instant slotStart = request.slotStartAt();
        validateExactHour(slotStart, "Slot start time must be on an exact hour");
        Instant slotEnd = slotStart.plus(SESSION_DURATION);

        TeacherAvailability availability = availabilityRepository
                .findContainingSlot(assignment.getTeacher().getId(), slotStart, slotEnd)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher slot is not available"));
        if (bookingRepository.existsByTeacherIdAndStartAtAndStatus(
                assignment.getTeacher().getId(), slotStart, BookingStatus.BOOKED)) {
            throw new ConflictException("Teacher slot is already booked");
        }
        if (bookingRepository.existsByStudentIdAndLessonIdAndStatus(
                student.getId(), access.lesson().getId(), BookingStatus.BOOKED)) {
            throw new ConflictException("Student already has a booked teacher session for this lesson");
        }

        TeacherBooking booking = TeacherBooking.builder()
                .student(student)
                .teacher(assignment.getTeacher())
                .enrollment(access.enrollment())
                .lesson(access.lesson())
                .availability(availability)
                .startAt(slotStart)
                .endAt(slotEnd)
                .status(BookingStatus.BOOKED)
                .build();
        return toBookingResponse(bookingRepository.save(booking));
    }

    public TeacherReviewResponse reviewBooking(User teacher, Long bookingId, ReviewBookingRequest request) {
        ensureTeacher(teacher);
        TeacherBooking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher booking not found with id " + bookingId));
        if (!booking.getTeacher().getId().equals(teacher.getId())) {
            throw new ForbiddenException("Teacher booking does not belong to current teacher");
        }
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new ConflictException("Only booked sessions can be reviewed");
        }
        if (reviewRepository.existsByBookingId(bookingId) || earningRepository.existsByBookingId(bookingId)) {
            throw new ConflictException("Teacher booking has already been reviewed");
        }

        TeacherCompensationConfig compensation = compensationRepository.findByTeacherId(teacher.getId())
                .orElseThrow(() -> new BadRequestException("Teacher compensation is not configured"));
        TeacherReviewResult result = parseReviewResult(request.result());

        booking.setStatus(BookingStatus.COMPLETED);
        booking = bookingRepository.save(booking);
        applyReviewToLessonProgress(booking, result);

        TeacherReview review = TeacherReview.builder()
                .booking(booking)
                .result(result)
                .comment(normalizeBlankToNull(request.comment()))
                .build();
        review = reviewRepository.save(review);

        TeacherEarning earning = TeacherEarning.builder()
                .teacher(teacher)
                .booking(booking)
                .amount(compensation.getAmountPerSession())
                .currency(compensation.getCurrency())
                .status(TeacherEarningStatus.EARNED)
                .build();
        earning = earningRepository.save(earning);

        return new TeacherReviewResponse(
                review.getId(),
                booking.getId(),
                review.getResult().name(),
                review.getComment(),
                review.getReviewedAt(),
                toBookingResponse(booking),
                toEarningResponse(earning));
    }

    private StudentLessonAccess ensureStudentCanBookTeacher(User student, Long lessonId) {
        if (student == null || student.getRole() == null || student.getRole().getName() != RoleName.STUDENT) {
            throw new ForbiddenException("Only students can book teacher sessions");
        }

        Lesson lesson = lessonRepository.findByIdAndStatusNot(lessonId, LessonStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + lessonId));
        Enrollment enrollment = enrollmentRepository
                .findAccessibleEnrollment(student.getId(), lesson.getProgram().getId(), EnrollmentStatus.ACTIVE, Instant.now())
                .orElseThrow(() -> new ForbiddenException("Course enrollment is expired or unavailable"));
        StudentLessonProgress progress = progressRepository
                .findByStudentIdAndLessonId(student.getId(), lessonId)
                .orElseThrow(() -> new ForbiddenException("Lesson is not ready for teacher booking"));
        if (progress.getStatus() != LessonProgressStatus.WAITING_FOR_TEACHER) {
            throw new ForbiddenException("Lesson is not waiting for teacher booking");
        }
        return new StudentLessonAccess(lesson, enrollment, progress);
    }

    private void applyReviewToLessonProgress(TeacherBooking booking, TeacherReviewResult result) {
        StudentLessonProgress progress = progressRepository
                .findByStudentIdAndLessonId(booking.getStudent().getId(), booking.getLesson().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lesson progress not found for booking"));

        if (result == TeacherReviewResult.NOT_APPROVED) {
            progress.setStatus(LessonProgressStatus.WAITING_FOR_TEACHER);
            progressRepository.save(progress);
            return;
        }

        if (isCheckpointGate(booking.getLesson())) {
            progress.setStatus(LessonProgressStatus.WAITING_FOR_CHECKPOINT);
            progressRepository.save(progress);
            return;
        }

        progress.setStatus(LessonProgressStatus.COMPLETED);
        progressRepository.save(progress);
        unlockNextPublishedLesson(booking);
    }

    private void unlockNextPublishedLesson(TeacherBooking booking) {
        List<Lesson> publishedLessons = lessonRepository.findByProgramIdAndStatusOrderByLessonNumberAsc(
                booking.getLesson().getProgram().getId(), LessonStatus.PUBLISHED);
        for (Lesson candidate : publishedLessons) {
            if (candidate.getLessonNumber() > booking.getLesson().getLessonNumber()) {
                progressRepository.findByStudentIdAndLessonId(booking.getStudent().getId(), candidate.getId())
                        .ifPresent(nextProgress -> {
                            if (nextProgress.getStatus() == LessonProgressStatus.LOCKED) {
                                nextProgress.setStatus(LessonProgressStatus.VIDEO_IN_PROGRESS);
                                progressRepository.save(nextProgress);
                            }
                        });
                return;
            }
        }
    }

    private boolean isCheckpointGate(Lesson lesson) {
        return lesson.getLessonNumber() > 0 && lesson.getLessonNumber() % CHECKPOINT_INTERVAL == 0;
    }

    private StudentTeacherAssignment getAssignmentForEnrollment(Long enrollmentId) {
        return assignmentRepository.findByEnrollmentId(enrollmentId)
                .orElseThrow(() -> new ForbiddenException("Teacher is not assigned for this enrollment"));
    }

    private User getTeacherUser(Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id " + teacherId));
        ensureTeacher(teacher);
        return teacher;
    }

    private void ensureTeacher(User teacher) {
        if (teacher == null || teacher.getRole() == null || teacher.getRole().getName() != RoleName.TEACHER) {
            throw new BadRequestException("User must be a TEACHER");
        }
    }

    private void validateAvailabilityRange(Instant startAt, Instant endAt) {
        validateExactHour(startAt, "Start time must be on an exact hour");
        validateExactHour(endAt, "End time must be on an exact hour");
        if (!endAt.isAfter(startAt)) {
            throw new BadRequestException("End time must be after start time");
        }
        long hours = Duration.between(startAt, endAt).toHours();
        if (hours < 1 || !startAt.plus(Duration.ofHours(hours)).equals(endAt)) {
            throw new BadRequestException("Availability duration must be a whole number of hours");
        }
    }

    private void validateExactHour(Instant value, String message) {
        if (value == null
                || value.atZone(ZoneOffset.UTC).getMinute() != 0
                || value.atZone(ZoneOffset.UTC).getSecond() != 0
                || value.atZone(ZoneOffset.UTC).getNano() != 0) {
            throw new BadRequestException(message);
        }
    }

    private boolean isSlotOpen(Instant slotStart, List<TeacherBooking> bookedSlots) {
        return bookedSlots.stream().noneMatch(booking -> booking.getStartAt().equals(slotStart));
    }

    private BookingStatus parseBookingStatus(String status) {
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid booking status: " + status);
        }
    }

    private TeacherReviewResult parseReviewResult(String result) {
        try {
            return TeacherReviewResult.valueOf(result.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid teacher review result: " + result);
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return "VND";
        }
        String normalized = currency.trim().toUpperCase();
        if (normalized.length() != 3) {
            throw new BadRequestException("Currency must be a 3-letter code");
        }
        return normalized;
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private TeacherAssignmentResponse toAssignmentResponse(StudentTeacherAssignment assignment) {
        Enrollment enrollment = assignment.getEnrollment();
        return new TeacherAssignmentResponse(
                assignment.getId(),
                enrollment.getId(),
                enrollment.getStudent().getId(),
                displayName(enrollment.getStudent()),
                enrollment.getProgram().getId(),
                enrollment.getProgram().getName(),
                assignment.getTeacher().getId(),
                displayName(assignment.getTeacher()),
                assignment.getAssignedAt());
    }

    private TeacherCompensationResponse toCompensationResponse(TeacherCompensationConfig config) {
        return new TeacherCompensationResponse(
                config.getId(),
                config.getTeacher().getId(),
                config.getAmountPerSession(),
                config.getCurrency(),
                config.getUpdatedAt());
    }

    private TeacherAvailabilityResponse toAvailabilityResponse(TeacherAvailability availability) {
        return new TeacherAvailabilityResponse(
                availability.getId(),
                availability.getTeacher().getId(),
                availability.getStartAt(),
                availability.getEndAt(),
                availability.getCreatedAt());
    }

    private TeacherBookingResponse toBookingResponse(TeacherBooking booking) {
        return new TeacherBookingResponse(
                booking.getId(),
                booking.getStudent().getId(),
                displayName(booking.getStudent()),
                booking.getTeacher().getId(),
                displayName(booking.getTeacher()),
                booking.getEnrollment().getId(),
                booking.getLesson().getId(),
                booking.getLesson().getName(),
                booking.getStartAt(),
                booking.getEndAt(),
                booking.getStatus().name(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }

    private TeacherEarningResponse toEarningResponse(TeacherEarning earning) {
        TeacherBooking booking = earning.getBooking();
        return new TeacherEarningResponse(
                earning.getId(),
                earning.getTeacher().getId(),
                booking.getId(),
                booking.getStudent().getId(),
                displayName(booking.getStudent()),
                booking.getLesson().getId(),
                booking.getLesson().getName(),
                earning.getAmount(),
                earning.getCurrency(),
                earning.getStatus().name(),
                earning.getEarnedAt());
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

    private record StudentLessonAccess(
            Lesson lesson,
            Enrollment enrollment,
            StudentLessonProgress progress
    ) {}
}
