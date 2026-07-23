package vera.lms.dtos;

import java.time.Instant;
import java.util.List;

public class ReportingDto {

    public record AdminDashboardResponse(
            long totalStudents,
            long totalTeachers,
            long totalEvaluators,
            long activeAccounts,
            long suspendedAccounts,
            long expiredAccounts,
            long totalEnrollments,
            long activeEnrollments,
            long expiredActiveEnrollments,
            long completedEnrollments,
            long waitingReassessmentEnrollments,
            long pendingPurchases,
            long paidPurchases,
            long bookedTeacherBookings,
            long pendingCheckpointSessions,
            long pendingFinalAssessmentSessions
    ) {}

    public record AdminStudentProgressResponse(
            Long enrollmentId,
            Long studentId,
            String studentName,
            String studentEmail,
            boolean studentEnabled,
            String accountStatus,
            Long programId,
            String programName,
            String enrollmentStatus,
            Instant enrolledAt,
            Instant expiredAt,
            Integer progressPercent,
            Integer currentLessonNumber,
            String currentLessonName,
            String currentLessonStatus,
            String nextAction,
            Long teacherId,
            String teacherName,
            Instant teacherAssignedAt
    ) {}

    public record AdminStudentProgressDetailResponse(
            AdminStudentProgressResponse summary,
            List<AdminStudentLessonProgressResponse> lessons
    ) {}

    public record AdminStudentLessonProgressResponse(
            Long lessonId,
            Integer lessonNumber,
            String lessonName,
            String lessonStatus,
            String progressStatus
    ) {}
}
