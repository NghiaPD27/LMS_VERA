package vera.lms.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.configs.SepayProperties;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.PurchaseDto.CreatePurchaseRequest;
import vera.lms.dtos.PurchaseDto.PurchaseResponse;
import vera.lms.enums.EnrollmentStatus;
import vera.lms.enums.ProgramSalesStatus;
import vera.lms.enums.PurchaseStatus;
import vera.lms.enums.RoleName;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ConflictException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.CoursePurchase;
import vera.lms.models.Enrollment;
import vera.lms.models.Program;
import vera.lms.models.StudentProfile;
import vera.lms.models.User;
import vera.lms.repositories.CoursePurchaseRepository;
import vera.lms.repositories.EnrollmentRepository;
import vera.lms.repositories.ProgramRepository;
import vera.lms.utils.PaginationUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class PurchaseService {

    private final CoursePurchaseRepository purchaseRepository;
    private final ProgramRepository programRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final SepayProperties sepayProperties;

    public PurchaseService(
            CoursePurchaseRepository purchaseRepository,
            ProgramRepository programRepository,
            EnrollmentRepository enrollmentRepository,
            EnrollmentService enrollmentService,
            SepayProperties sepayProperties) {
        this.purchaseRepository = purchaseRepository;
        this.programRepository = programRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentService = enrollmentService;
        this.sepayProperties = sepayProperties;
    }

    public PurchaseResponse createPurchase(User student, CreatePurchaseRequest request) {
        validateStudent(student);
        if (enrollmentRepository.existsByStudentIdAndStatus(student.getId(), EnrollmentStatus.ACTIVE)) {
            throw new ConflictException("Student already has an active enrollment");
        }

        Program program = programRepository.findByIdAndSalesStatus(request.programId(), ProgramSalesStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Public program not found with id " + request.programId()));

        if (purchaseRepository.existsByStudentIdAndProgramIdAndStatus(
                student.getId(), program.getId(), PurchaseStatus.PENDING)) {
            throw new ConflictException("Student already has a pending purchase for this program");
        }

        CoursePurchase purchase = CoursePurchase.builder()
                .student(student)
                .program(program)
                .amount(program.getPrice())
                .currency(program.getCurrency())
                .programName(program.getName())
                .status(PurchaseStatus.PENDING)
                .paymentProvider("SEPAY")
                .build();
        purchase = purchaseRepository.save(purchase);
        String paymentCode = buildPaymentCode(purchase.getId());
        purchase.setPaymentCode(paymentCode);
        purchase.setPaymentContent(paymentCode);
        purchase.setPaymentQrUrl(buildPaymentQrUrl(purchase));
        return toResponse(purchaseRepository.save(purchase));
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> getStudentPurchases(User student) {
        validateStudent(student);
        return purchaseRepository.findByStudentIdOrderByIdDesc(student.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getStudentPurchase(User student, Long purchaseId) {
        validateStudent(student);
        CoursePurchase purchase = purchaseRepository.findByIdAndStudentId(purchaseId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found with id " + purchaseId));
        return toResponse(purchase);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseResponse> getAdminPurchases(
            String studentId,
            String programId,
            String status,
            Integer page,
            Integer size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("id").descending());
        Page<CoursePurchase> purchases = purchaseRepository.searchAdminPurchases(
                parseOptionalLong(studentId, "studentId"),
                parseOptionalLong(programId, "programId"),
                parseOptionalStatus(status),
                pageable);
        List<PurchaseResponse> content = purchases.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(
                content,
                purchases.getTotalElements(),
                purchases.getTotalPages(),
                purchases.getNumber(),
                purchases.getSize());
    }

    public PurchaseResponse markPaid(Long purchaseId) {
        CoursePurchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found with id " + purchaseId));

        if (purchase.getStatus() == PurchaseStatus.PAID) {
            return toResponse(purchase);
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new ConflictException("Only pending purchases can be marked paid");
        }
        if (enrollmentRepository.existsByStudentIdAndStatus(
                purchase.getStudent().getId(), EnrollmentStatus.ACTIVE)) {
            throw new ConflictException("Student already has an active enrollment");
        }

        return toResponse(completePaidPurchase(purchase, Instant.now(), null, null));
    }

    public PurchaseResponse markPaidFromSepay(
            CoursePurchase purchase,
            String providerTransactionId,
            String providerReferenceCode,
            Instant paidAt) {
        if (purchase.getStatus() == PurchaseStatus.PAID) {
            return toResponse(purchase);
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new ConflictException("Only pending purchases can be marked paid");
        }
        return toResponse(completePaidPurchase(purchase, paidAt, providerTransactionId, providerReferenceCode));
    }

    private PurchaseResponse toResponse(CoursePurchase purchase) {
        User student = purchase.getStudent();
        StudentProfile profile = student.getStudentProfile();
        String studentName = profile != null
                ? (profile.getFirstName() + " " + profile.getLastName()).trim()
                : student.getUsername();
        Enrollment enrollment = purchase.getEnrollment();
        return new PurchaseResponse(
                purchase.getId(),
                student.getId(),
                studentName,
                student.getEmail(),
                purchase.getProgram().getId(),
                purchase.getProgramName(),
                purchase.getAmount(),
                purchase.getCurrency(),
                purchase.getStatus().name(),
                purchase.getPaymentCode(),
                purchase.getPaymentQrUrl(),
                purchase.getPaymentProvider(),
                purchase.getPaymentContent(),
                enrollment != null ? enrollment.getId() : null,
                purchase.getCreatedAt(),
                purchase.getPaidAt());
    }

    private CoursePurchase completePaidPurchase(
            CoursePurchase purchase,
            Instant paidAt,
            String providerTransactionId,
            String providerReferenceCode) {
        if (enrollmentRepository.existsByStudentIdAndStatus(
                purchase.getStudent().getId(), EnrollmentStatus.ACTIVE)) {
            throw new ConflictException("Student already has an active enrollment");
        }

        Enrollment enrollment = enrollmentService.createActiveEnrollment(purchase.getStudent(), purchase.getProgram());
        purchase.setEnrollment(enrollment);
        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaidAt(paidAt != null ? paidAt : Instant.now());
        purchase.setProviderTransactionId(providerTransactionId);
        purchase.setProviderReferenceCode(providerReferenceCode);
        return purchaseRepository.save(purchase);
    }

    private void validateStudent(User user) {
        if (user == null || user.getRole() == null || user.getRole().getName() != RoleName.STUDENT) {
            throw new BadRequestException("Only STUDENT users can create or view student purchases");
        }
    }

    private Long parseOptionalLong(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid " + fieldName + ": " + value);
        }
    }

    private PurchaseStatus parseOptionalStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return PurchaseStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid purchase status: " + status);
        }
    }

    private String buildPaymentCode(Long purchaseId) {
        return sepayProperties.paymentCodePrefixOrDefault() + purchaseId;
    }

    private String buildPaymentQrUrl(CoursePurchase purchase) {
        String amount = toVndAmount(purchase.getAmount());
        String description = URLEncoder.encode(purchase.getPaymentContent(), StandardCharsets.UTF_8);
        return sepayProperties.qrBaseUrlOrDefault()
                + "?acc=" + encode(sepayProperties.bankAccount())
                + "&bank=" + encode(sepayProperties.bankCode())
                + "&amount=" + amount
                + "&des=" + description;
    }

    private String toVndAmount(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException e) {
            throw new BadRequestException("SePay payment amount must be a whole VND amount");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
