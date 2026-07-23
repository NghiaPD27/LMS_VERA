package vera.lms.dtos;

import java.time.Instant;

public class AuditDto {

    public record AuditLogResponse(
            Long id,
            Long actorId,
            String actorUsername,
            String action,
            String targetType,
            Long targetId,
            String details,
            Instant createdAt
    ) {}
}
