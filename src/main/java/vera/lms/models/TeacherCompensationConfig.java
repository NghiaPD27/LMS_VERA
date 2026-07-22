package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "teacher_compensation_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherCompensationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false, unique = true)
    private User teacher;

    @Column(name = "amount_per_session", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPerSession;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
