package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.EvaluatorProfile;

@Repository
public interface EvaluatorProfileRepository extends JpaRepository<EvaluatorProfile, Long> {
}
