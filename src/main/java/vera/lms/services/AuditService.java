package vera.lms.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.AuditDto.AuditLogResponse;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.enums.AuditAction;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.AuditLog;
import vera.lms.models.User;
import vera.lms.repositories.AuditLogRepository;
import vera.lms.utils.PaginationUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(AuditAction action, String targetType, Long targetId, String details) {
        Actor actor = currentActor();
        auditLogRepository.save(AuditLog.builder()
                .actorId(actor.id())
                .actorUsername(actor.username())
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(
            String action,
            Long actorId,
            String targetType,
            Long targetId,
            Instant from,
            Instant to,
            Integer page,
            Integer size) {
        validateRange(from, to);
        AuditAction auditAction = parseOptionalAction(action);
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("createdAt").descending());
        Page<AuditLog> logs = auditLogRepository.findAll(
                auditSpecification(auditAction, actorId, normalizeBlankToNull(targetType), targetId, from, to),
                pageable);
        List<AuditLogResponse> content = logs.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(content, logs.getTotalElements(), logs.getTotalPages(), logs.getNumber(), logs.getSize());
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLog(Long id) {
        return auditLogRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found with id " + id));
    }

    private Specification<AuditLog> auditSpecification(
            AuditAction action,
            Long actorId,
            String targetType,
            Long targetId,
            Instant from,
            Instant to) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }
            if (actorId != null) {
                predicates.add(criteriaBuilder.equal(root.get("actorId"), actorId));
            }
            if (targetType != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("targetType")), targetType.toUpperCase()));
            }
            if (targetId != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetId"), targetId));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new Actor(null, null);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return new Actor(user.getId(), user.getUsername());
        }
        return new Actor(null, authentication.getName());
    }

    private AuditAction parseOptionalAction(String action) {
        String normalized = normalizeBlankToNull(action);
        if (normalized == null) {
            return null;
        }
        try {
            return AuditAction.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid audit action: " + action);
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new BadRequestException("Audit log end time must be after start time");
        }
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getActorUsername(),
                log.getAction().name(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetails(),
                log.getCreatedAt());
    }

    private record Actor(Long id, String username) {}
}
