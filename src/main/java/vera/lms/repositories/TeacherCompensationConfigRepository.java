package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.TeacherCompensationConfig;

import java.util.Optional;

@Repository
public interface TeacherCompensationConfigRepository extends JpaRepository<TeacherCompensationConfig, Long> {
    Optional<TeacherCompensationConfig> findByTeacherId(Long teacherId);
}
