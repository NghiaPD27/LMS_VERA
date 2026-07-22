package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.TeacherEarningStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "teacher_earnings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private TeacherBooking booking;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private TeacherEarningStatus status = TeacherEarningStatus.EARNED;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @PrePersist
    protected void onCreate() {
        if (earnedAt == null) {
            earnedAt = Instant.now();
        }
    }
}
