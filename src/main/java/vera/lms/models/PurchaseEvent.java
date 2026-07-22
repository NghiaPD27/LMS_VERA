package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.PurchaseStatus;

import java.time.Instant;

@Entity
@Table(name = "purchase_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private CoursePurchase purchase;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20)
    private PurchaseStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private PurchaseStatus newStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
