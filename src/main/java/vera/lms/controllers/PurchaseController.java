package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.PurchaseDto.CreatePurchaseRequest;
import vera.lms.dtos.PurchaseDto.PurchaseResponse;
import vera.lms.models.User;
import vera.lms.services.PurchaseService;

import java.util.List;

@RestController
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/api/student/purchases")
    public ResponseEntity<PurchaseResponse> createStudentPurchase(
            @AuthenticationPrincipal User student,
            @RequestBody @Valid CreatePurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.createPurchase(student, request));
    }

    @GetMapping("/api/student/purchases")
    public ResponseEntity<List<PurchaseResponse>> getStudentPurchases(@AuthenticationPrincipal User student) {
        return ResponseEntity.ok(purchaseService.getStudentPurchases(student));
    }

    @GetMapping("/api/student/purchases/{id}")
    public ResponseEntity<PurchaseResponse> getStudentPurchase(
            @AuthenticationPrincipal User student,
            @PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.getStudentPurchase(student, id));
    }

    @GetMapping("/api/admin/purchases")
    public ResponseEntity<PageResponse<PurchaseResponse>> getAdminPurchases(
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String programId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(purchaseService.getAdminPurchases(studentId, programId, status, page, size));
    }

    @PostMapping("/api/admin/purchases/{id}/mark-paid")
    public ResponseEntity<PurchaseResponse> markPurchasePaid(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.markPaid(id));
    }
}
