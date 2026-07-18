package vera.lms.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.ProgramDto.CreateProgramRequest;
import vera.lms.dtos.ProgramDto.ProgramResponse;
import vera.lms.enums.ProgramSalesStatus;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.Program;
import vera.lms.repositories.ProgramRepository;
import vera.lms.utils.PaginationUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class ProgramService {

    private final ProgramRepository programRepository;

    @Autowired
    public ProgramService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    public Program createProgram(CreateProgramRequest request) {
        validateProgramName(request.name());
        Program program = Program.builder()
                .name(request.name().trim())
                .description(request.description())
                .price(normalizePrice(request.price()))
                .currency(normalizeCurrency(request.currency()))
                .salesStatus(parseSalesStatusOrDefault(request.salesStatus()))
                .build();
        return programRepository.save(program);
    }

    @Transactional(readOnly = true)
    public List<Program> getPrograms() {
        return programRepository.findAll();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProgramResponse> getPrograms(String keyword, Integer page, Integer size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("id").descending());
        Page<Program> programs = programRepository.searchByKeyword(normalizeKeyword(keyword), pageable);
        List<ProgramResponse> content = programs.getContent().stream()
                .map(this::toProgramResponse)
                .toList();
        return new PageResponse<>(
                content,
                programs.getTotalElements(),
                programs.getTotalPages(),
                programs.getNumber(),
                programs.getSize());
    }

    @Transactional(readOnly = true)
    public PageResponse<ProgramResponse> getPublicPrograms(String keyword, Integer page, Integer size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("id").descending());
        Page<Program> programs = programRepository.searchByKeywordAndSalesStatus(
                normalizeKeyword(keyword), ProgramSalesStatus.PUBLISHED, pageable);
        List<ProgramResponse> content = programs.getContent().stream()
                .map(this::toProgramResponse)
                .toList();
        return new PageResponse<>(
                content,
                programs.getTotalElements(),
                programs.getTotalPages(),
                programs.getNumber(),
                programs.getSize());
    }

    @Transactional(readOnly = true)
    public Program getProgram(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found with id " + id));
    }

    @Transactional(readOnly = true)
    public Program getPublicProgram(Long id) {
        return programRepository.findByIdAndSalesStatus(id, ProgramSalesStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Public program not found with id " + id));
    }

    public Program updateProgram(Long id, CreateProgramRequest request) {
        Program program = getProgram(id);
        validateProgramName(request.name());
        program.setName(request.name().trim());
        program.setDescription(request.description());
        program.setPrice(normalizePrice(request.price()));
        program.setCurrency(normalizeCurrency(request.currency()));
        program.setSalesStatus(parseSalesStatusOrDefault(request.salesStatus()));
        return programRepository.save(program);
    }

    public void deleteProgram(Long id) {
        Program program = getProgram(id);
        programRepository.delete(program);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return keyword.trim();
    }

    private ProgramResponse toProgramResponse(Program program) {
        return new ProgramResponse(
                program.getId(),
                program.getName(),
                program.getDescription(),
                program.getPrice(),
                program.getCurrency(),
                program.getSalesStatus().name());
    }

    private void validateProgramName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("Program name cannot be empty");
        }
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Program price must be non-negative");
        }
        return price;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return "VND";
        }
        String normalized = currency.trim().toUpperCase();
        if (normalized.length() != 3) {
            throw new BadRequestException("Currency must be a 3-letter code");
        }
        return normalized;
    }

    private ProgramSalesStatus parseSalesStatusOrDefault(String salesStatus) {
        if (salesStatus == null || salesStatus.trim().isEmpty()) {
            return ProgramSalesStatus.DRAFT;
        }
        try {
            return ProgramSalesStatus.valueOf(salesStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid program sales status: " + salesStatus);
        }
    }
}
