package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.AccountAccess;

@Repository
public interface AccountAccessRepository extends JpaRepository<AccountAccess, Long> {
}
