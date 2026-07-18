package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "video_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_video_id", nullable = false)
    private LessonVideo lessonVideo;

    @Builder.Default
    @Column(name = "current_second", nullable = false)
    private int currentSecond = 0;

    @Builder.Default
    @Column(name = "furthest_watched_second", nullable = false)
    private int furthestWatchedSecond = 0;

    @Builder.Default
    @Column(name = "watched_percentage", nullable = false)
    private int watchedPercentage = 0;

    @Builder.Default
    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
