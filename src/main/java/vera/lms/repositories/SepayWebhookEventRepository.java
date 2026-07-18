package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.SepayWebhookEvent;

@Repository
public interface SepayWebhookEventRepository extends JpaRepository<SepayWebhookEvent, Long> {
    boolean existsBySepayTransactionId(Long sepayTransactionId);
}
