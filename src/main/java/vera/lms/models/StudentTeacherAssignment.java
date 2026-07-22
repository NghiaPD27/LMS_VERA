package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "student_teacher_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentTeacherAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false, unique = true)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}
