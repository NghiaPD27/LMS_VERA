package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "checkpoints",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_checkpoints_program_block",
                columnNames = {"program_id", "block_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Checkpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "block_number", nullable = false)
    private int blockNumber;

    @Column(name = "start_lesson_number", nullable = false)
    private int startLessonNumber;

    @Column(name = "gate_lesson_number", nullable = false)
    private int gateLessonNumber;

    @Column(name = "next_lesson_number", nullable = false)
    private int nextLessonNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
