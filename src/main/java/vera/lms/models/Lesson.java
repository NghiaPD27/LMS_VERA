package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.LessonStatus;

@Entity
@Table(name = "lessons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "lesson_number", nullable = false)
    private int lessonNumber;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private LessonStatus status = LessonStatus.DRAFT;
}
