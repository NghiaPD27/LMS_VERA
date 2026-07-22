package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.models.TeacherAvailability;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherAvailabilityRepository extends JpaRepository<TeacherAvailability, Long> {
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM TeacherAvailability a
            WHERE a.teacher.id = :teacherId
            AND a.startAt < :endAt
            AND a.endAt > :startAt
            """)
    boolean existsOverlappingAvailability(
            @Param("teacherId") Long teacherId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
            SELECT a FROM TeacherAvailability a
            WHERE a.teacher.id = :teacherId
            AND a.startAt <= :slotStart
            AND a.endAt >= :slotEnd
            """)
    Optional<TeacherAvailability> findContainingSlot(
            @Param("teacherId") Long teacherId,
            @Param("slotStart") Instant slotStart,
            @Param("slotEnd") Instant slotEnd);

    @Query("""
            SELECT a FROM TeacherAvailability a
            WHERE a.teacher.id = :teacherId
            AND a.endAt > :from
            ORDER BY a.startAt ASC
            """)
    List<TeacherAvailability> findFutureAvailability(
            @Param("teacherId") Long teacherId,
            @Param("from") Instant from);
}
