package vera.lms.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class StudentLessonProgressId implements Serializable {

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "lesson_id")
    private Long lessonId;
}
