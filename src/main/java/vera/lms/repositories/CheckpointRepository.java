package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.Checkpoint;

import java.util.Optional;

@Repository
public interface CheckpointRepository extends JpaRepository<Checkpoint, Long> {
    Optional<Checkpoint> findByProgramIdAndBlockNumber(Long programId, int blockNumber);
}
