package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Builder.Default
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void replaceQuestions(List<QuizQuestion> replacementQuestions) {
        for (int i = 0; i < replacementQuestions.size(); i++) {
            QuizQuestion replacement = replacementQuestions.get(i);
            QuizQuestion question;
            if (i < questions.size()) {
                question = questions.get(i);
            } else {
                question = new QuizQuestion();
                question.setQuiz(this);
                questions.add(question);
            }
            question.setQuestionText(replacement.getQuestionText());
            question.setPosition(i + 1);
            question.replaceOptions(replacement.getOptions());
        }

        while (questions.size() > replacementQuestions.size()) {
            questions.remove(questions.size() - 1);
        }
    }

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
