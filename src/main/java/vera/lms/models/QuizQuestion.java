package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "position", nullable = false)
    private int position;

    @Builder.Default
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuizOption> options = new ArrayList<>();

    public void replaceOptions(List<QuizOption> replacementOptions) {
        for (int i = 0; i < replacementOptions.size(); i++) {
            QuizOption replacement = replacementOptions.get(i);
            QuizOption option;
            if (i < options.size()) {
                option = options.get(i);
            } else {
                option = new QuizOption();
                option.setQuestion(this);
                options.add(option);
            }
            option.setOptionText(replacement.getOptionText());
            option.setCorrect(replacement.isCorrect());
            option.setPosition(i + 1);
        }

        while (options.size() > replacementOptions.size()) {
            options.remove(options.size() - 1);
        }
    }
}
