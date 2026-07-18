package vera.lms.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.models.Program;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {
    @Query("""
            SELECT p FROM Program p
            WHERE (
                :keyword IS NULL OR :keyword = ''
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            """)
    Page<Program> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
