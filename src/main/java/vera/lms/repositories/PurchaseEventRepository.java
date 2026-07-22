package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.PurchaseEvent;

import java.util.List;

@Repository
public interface PurchaseEventRepository extends JpaRepository<PurchaseEvent, Long> {
    @EntityGraph(attributePaths = {"purchase"})
    List<PurchaseEvent> findByPurchaseIdOrderByCreatedAtDesc(Long purchaseId);
}
