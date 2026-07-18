package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.LessonProgressStatus;

@Entity
@Table(name = "student_lesson_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLessonProgress {

    @EmbeddedId
    private StudentLessonProgressId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("lessonId")
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private LessonProgressStatus status = LessonProgressStatus.LOCKED;
}
