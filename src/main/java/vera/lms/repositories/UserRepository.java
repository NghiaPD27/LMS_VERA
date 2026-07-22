package vera.lms.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.RoleName;
import vera.lms.models.User;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByIdAndRoleName(Long id, RoleName roleName);

    @Query("""
            SELECT u FROM User u
            JOIN u.role r
            JOIN u.studentProfile sp
            WHERE r.name = :roleName
            AND (
                :keyword IS NULL OR :keyword = ''
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(sp.firstName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(sp.lastName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            """)
    Page<User> searchByRoleAndStudentKeyword(
            @Param("roleName") RoleName roleName,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT u FROM User u
            JOIN u.role r
            LEFT JOIN u.teacherProfile tp
            WHERE r.name = :roleName
            AND (
                :keyword IS NULL OR :keyword = ''
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(tp.firstName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(tp.lastName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            """)
    Page<User> searchByRoleAndTeacherKeyword(
            @Param("roleName") RoleName roleName,
            @Param("keyword") String keyword,
            Pageable pageable);
}
