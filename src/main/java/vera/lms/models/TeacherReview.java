package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.TeacherReviewResult;

import java.time.Instant;

@Entity
@Table(name = "teacher_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private TeacherBooking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private TeacherReviewResult result;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (reviewedAt == null) {
            reviewedAt = Instant.now();
        }
    }
}
