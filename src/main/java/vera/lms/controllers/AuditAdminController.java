package vera.lms.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.AuditDto.AuditLogResponse;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.services.AuditService;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditAdminController {

    private final AuditService auditService;

    public AuditAdminController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(auditService.getAuditLogs(action, actorId, targetType, targetId, from, to, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getAuditLog(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getAuditLog(id));
    }
}
