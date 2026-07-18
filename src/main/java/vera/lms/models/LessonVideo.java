package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.VideoStatus;

import java.time.Instant;

@Entity
@Table(name = "lesson_videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson;

    @Column(name = "bunny_video_id", nullable = false, length = 100)
    private String bunnyVideoId;

    @Column(name = "library_id", nullable = false, length = 100)
    private String libraryId;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private VideoStatus status = VideoStatus.READY;

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
