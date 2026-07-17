package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.TeacherProfile;

@Repository
public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {
}
