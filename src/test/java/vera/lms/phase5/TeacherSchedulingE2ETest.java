package vera.lms.phase5;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vera.lms.BaseIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherSchedulingE2ETest extends BaseIntegrationTest {

    private static final Instant SLOT_START = Instant.parse("2030-01-01T17:00:00Z");
    private static final Instant SLOT_END = Instant.parse("2030-01-01T19:00:00Z");

    private Long seedStudent(String username) {
        Long studentId = createUser(username, username + "@student.test", "Password123", "STUDENT", true);
        createAccountAccess(studentId, "ACTIVE", false);
        createStudentProfile(studentId, "Phase5", username, "0901234567");
        return studentId;
    }

    private Long seedTeacher(String username) {
        Long teacherId = createUser(username, username + "@teacher.test", "Password123", "TEACHER", true);
        createAccountAccess(teacherId, "ACTIVE", false);
        createTeacherProfile(teacherId, "Teacher", username, "0907654321", "Phase 5 teacher");
        return teacherId;
    }

    private Long seedProgram(String name) {
        jdbcTemplate.update("""
                INSERT INTO programs (name, description, price, currency, sales_status)
                VALUES (?, ?, ?, ?, ?)
                """, name, "Teacher scheduling program", 1800000, "VND", "PUBLISHED");
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedLesson(Long programId, int lessonNumber) {
        jdbcTemplate.update("""
                INSERT INTO lessons (program_id, name, lesson_number, content, status)
                VALUES (?, ?, ?, ?, 'PUBLISHED')
                """, programId, "Teacher Lesson " + lessonNumber, lessonNumber, "Lesson content");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM lessons WHERE program_id = ? AND lesson_number = ?",
                Long.class,
                programId,
                lessonNumber);
    }

    private Long seedEnrollment(Long studentId, Long programId) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (student_id, program_id, status, enrolled_at, expired_at)
                VALUES (?, ?, 'ACTIVE', ?, ?)
                """,
                studentId,
                programId,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM enrollments WHERE student_id = ? AND program_id = ?",
                Long.class,
                studentId,
                programId);
    }

    private void seedLessonProgress(Long studentId, Long lessonId, String status) {
        jdbcTemplate.update("""
                INSERT INTO student_lesson_progress (student_id, lesson_id, status)
                VALUES (?, ?, ?)
                """, studentId, lessonId, status);
    }

    private Fixture seedReadyForBooking(String suffix) {
        Long studentId = seedStudent("student_user");
        Long teacherId = seedTeacher("teacher_user");
        Long programId = seedProgram("Teacher Program " + suffix);
        Long lessonId = seedLesson(programId, 1);
        Long nextLessonId = seedLesson(programId, 2);
        Long enrollmentId = seedEnrollment(studentId, programId);
        seedLessonProgress(studentId, lessonId, "WAITING_FOR_TEACHER");
        seedLessonProgress(studentId, nextLessonId, "LOCKED");
        return new Fixture(studentId, teacherId, programId, lessonId, nextLessonId, enrollmentId);
    }

    private void assignTeacher(Long enrollmentId, Long teacherId) throws Exception {
        mockMvc.perform(put("/api/admin/enrollments/" + enrollmentId + "/teacher-assignment")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentId").value(enrollmentId))
                .andExpect(jsonPath("$.teacherId").value(teacherId));
    }

    private void configureCompensation(Long teacherId, int amount) throws Exception {
        mockMvc.perform(put("/api/admin/teachers/" + teacherId + "/compensation")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerSession\":" + amount + ",\"currency\":\"VND\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andExpect(jsonPath("$.amountPerSession").value(amount));
    }

    private void createAvailability() throws Exception {
        mockMvc.perform(post("/api/teacher/availability")
                        .header("Authorization", "Bearer teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2030-01-01T17:00:00Z",
                                  "endAt": "2030-01-01T19:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value("2030-01-01T17:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2030-01-01T19:00:00Z"));
    }

    private Long createBooking(Long lessonId) throws Exception {
        mockMvc.perform(post("/api/student/bookings")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lessonId": %d,
                                  "slotStartAt": "2030-01-01T17:00:00Z"
                                }
                                """.formatted(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lessonId))
                .andExpect(jsonPath("$.startAt").value("2030-01-01T17:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2030-01-01T18:00:00Z"))
                .andExpect(jsonPath("$.status").value("BOOKED"));
        return jdbcTemplate.queryForObject("SELECT id FROM teacher_bookings WHERE lesson_id = ?", Long.class, lessonId);
    }

    @Test
    void testAdminAssignsTeacherAndConfiguresCompensation() throws Exception {
        Fixture fixture = seedReadyForBooking("Admin");

        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        configureCompensation(fixture.teacherId(), 40000);

        mockMvc.perform(get("/api/teacher/students")
                        .header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enrollmentId").value(fixture.enrollmentId()))
                .andExpect(jsonPath("$[0].studentId").value(fixture.studentId()));
    }

    @Test
    void testTeacherAvailabilitySplitsIntoOneHourStudentSlots() throws Exception {
        Fixture fixture = seedReadyForBooking("Slots");
        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        createAvailability();

        mockMvc.perform(get("/api/student/teacher-slots")
                        .header("Authorization", "Bearer student-token")
                        .param("lessonId", fixture.lessonId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startAt").value("2030-01-01T17:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2030-01-01T18:00:00Z"))
                .andExpect(jsonPath("$[1].startAt").value("2030-01-01T18:00:00Z"))
                .andExpect(jsonPath("$[1].endAt").value("2030-01-01T19:00:00Z"));
    }

    @Test
    void testStudentCannotSeeSlotsBeforeWaitingForTeacher() throws Exception {
        Long studentId = seedStudent("student_user");
        Long teacherId = seedTeacher("teacher_user");
        Long programId = seedProgram("Not Ready Program");
        Long lessonId = seedLesson(programId, 1);
        Long enrollmentId = seedEnrollment(studentId, programId);
        seedLessonProgress(studentId, lessonId, "QUIZ_AVAILABLE");
        assignTeacher(enrollmentId, teacherId);
        createAvailability();

        mockMvc.perform(get("/api/student/teacher-slots")
                        .header("Authorization", "Bearer student-token")
                        .param("lessonId", lessonId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Lesson is not waiting for teacher booking"));
    }

    @Test
    void testStudentBooksAssignedTeacherAndCannotDuplicateLessonBooking() throws Exception {
        Fixture fixture = seedReadyForBooking("Booking");
        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        createAvailability();

        createBooking(fixture.lessonId());

        mockMvc.perform(post("/api/student/bookings")
                        .header("Authorization", "Bearer student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lessonId": %d,
                                  "slotStartAt": "2030-01-01T18:00:00Z"
                                }
                                """.formatted(fixture.lessonId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Student already has a booked teacher session for this lesson"));
    }

    @Test
    void testStudentCanReloadCurrentLessonBookingAndCancelIt() throws Exception {
        Fixture fixture = seedReadyForBooking("Reload Booking");
        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        createAvailability();
        Long bookingId = createBooking(fixture.lessonId());

        mockMvc.perform(get("/api/student/bookings")
                        .header("Authorization", "Bearer student-token")
                        .param("lessonId", fixture.lessonId().toString())
                        .param("status", "BOOKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].teacherId").value(fixture.teacherId()))
                .andExpect(jsonPath("$[0].startAt").value("2030-01-01T17:00:00Z"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/student/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void testTeacherCanViewAvailabilitySlotsWithBookingInfoAndCancelOpenAvailability() throws Exception {
        Fixture fixture = seedReadyForBooking("Availability Manage");
        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        createAvailability();
        Long bookingId = createBooking(fixture.lessonId());

        mockMvc.perform(get("/api/teacher/availability")
                        .header("Authorization", "Bearer teacher-token")
                        .param("from", "2030-01-01T00:00:00Z")
                        .param("to", "2030-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("BOOKED"))
                .andExpect(jsonPath("$[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$[0].studentName").value("Phase5 student_user"))
                .andExpect(jsonPath("$[1].status").value("OPEN"));

        Long availabilityId = jdbcTemplate.queryForObject("SELECT id FROM teacher_availability", Long.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/teacher/availability/" + availabilityId)
                        .header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Teacher availability has booked slots and cannot be cancelled"));
    }

    @Test
    void testTeacherSlotCannotBeDoubleBookedByAnotherStudent() throws Exception {
        Fixture fixture = seedReadyForBooking("Double Booking");
        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        createAvailability();
        createBooking(fixture.lessonId());

        Long secondStudentId = seedStudent("second_student");
        Long secondProgramId = seedProgram("Second Student Program");
        Long secondLessonId = seedLesson(secondProgramId, 1);
        Long secondEnrollmentId = seedEnrollment(secondStudentId, secondProgramId);
        seedLessonProgress(secondStudentId, secondLessonId, "WAITING_FOR_TEACHER");
        assignTeacher(secondEnrollmentId, fixture.teacherId());

        mockMvc.perform(post("/api/student/bookings")
                        .header("Authorization", "Bearer mock-student-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lessonId": %d,
                                  "slotStartAt": "2030-01-01T17:00:00Z"
                                }
                                """.formatted(secondLessonId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Teacher slot is already booked"));
    }

    @Test
    void testTeacherCannotReviewAnotherTeachersBooking() throws Exception {
        Long studentId = seedStudent("student_user");
        Long currentTeacherId = seedTeacher("teacher_user");
        Long otherTeacherId = seedTeacher("other_teacher");
        Long programId = seedProgram("Forbidden Review Program");
        Long lessonId = seedLesson(programId, 1);
        Long enrollmentId = seedEnrollment(studentId, programId);
        seedLessonProgress(studentId, lessonId, "WAITING_FOR_TEACHER");
        assignTeacher(enrollmentId, otherTeacherId);

        jdbcTemplate.update("""
                INSERT INTO teacher_availability (teacher_id, start_at, end_at)
                VALUES (?, ?, ?)
                """, otherTeacherId, SLOT_START, SLOT_END);
        Long availabilityId = jdbcTemplate.queryForObject("SELECT id FROM teacher_availability", Long.class);
        jdbcTemplate.update("""
                INSERT INTO teacher_bookings
                    (student_id, teacher_id, enrollment_id, lesson_id, availability_id, start_at, end_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'BOOKED')
                """, studentId, otherTeacherId, enrollmentId, lessonId, availabilityId, SLOT_START, SLOT_START.plus(1, ChronoUnit.HOURS));
        Long bookingId = jdbcTemplate.queryForObject("SELECT id FROM teacher_bookings", Long.class);
        configureCompensation(currentTeacherId, 40000);

        mockMvc.perform(post("/api/teacher/bookings/" + bookingId + "/review")
                        .header("Authorization", "Bearer teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"APPROVED\",\"comment\":\"done\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Teacher booking does not belong to current teacher"));
    }

    @Test
    void testApprovedReviewCompletesLessonUnlocksNextLessonAndCreatesEarning() throws Exception {
        Fixture fixture = seedReadyForBooking("Approve");
        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        configureCompensation(fixture.teacherId(), 40000);
        createAvailability();
        Long bookingId = createBooking(fixture.lessonId());

        mockMvc.perform(post("/api/teacher/bookings/" + bookingId + "/review")
                        .header("Authorization", "Bearer teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"APPROVED\",\"comment\":\"Good session\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPROVED"))
                .andExpect(jsonPath("$.booking.status").value("COMPLETED"))
                .andExpect(jsonPath("$.earning.amount").value(40000));

        assertEquals("COMPLETED", lessonProgressStatus(fixture.studentId(), fixture.lessonId()));
        assertEquals("VIDEO_IN_PROGRESS", lessonProgressStatus(fixture.studentId(), fixture.nextLessonId()));

        mockMvc.perform(get("/api/admin/teachers/" + fixture.teacherId() + "/earnings")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarned").value(40000))
                .andExpect(jsonPath("$.earnings[0].bookingId").value(bookingId));
    }

    @Test
    void testNotApprovedReviewKeepsLessonWaitingAndStillCreatesEarning() throws Exception {
        Fixture fixture = seedReadyForBooking("Not Approve");
        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        configureCompensation(fixture.teacherId(), 50000);
        createAvailability();
        Long bookingId = createBooking(fixture.lessonId());

        mockMvc.perform(post("/api/teacher/bookings/" + bookingId + "/review")
                        .header("Authorization", "Bearer teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"NOT_APPROVED\",\"comment\":\"Needs another session\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_APPROVED"))
                .andExpect(jsonPath("$.earning.amount").value(50000));

        assertEquals("WAITING_FOR_TEACHER", lessonProgressStatus(fixture.studentId(), fixture.lessonId()));
        assertEquals("LOCKED", lessonProgressStatus(fixture.studentId(), fixture.nextLessonId()));
    }

    @Test
    void testMissingTeacherCompensationBlocksReview() throws Exception {
        Fixture fixture = seedReadyForBooking("Missing Pay");
        assignTeacher(fixture.enrollmentId(), fixture.teacherId());
        createAvailability();
        Long bookingId = createBooking(fixture.lessonId());

        mockMvc.perform(post("/api/teacher/bookings/" + bookingId + "/review")
                        .header("Authorization", "Bearer teacher-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Teacher compensation is not configured"));
    }

    private String lessonProgressStatus(Long studentId, Long lessonId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM student_lesson_progress
                WHERE student_id = ? AND lesson_id = ?
                """, String.class, studentId, lessonId);
    }

    private record Fixture(
            Long studentId,
            Long teacherId,
            Long programId,
            Long lessonId,
            Long nextLessonId,
            Long enrollmentId
    ) {}
}
