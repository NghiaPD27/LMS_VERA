package vera.lms.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.ProgramDto.ProgramResponse;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.Program;
import vera.lms.repositories.ProgramRepository;
import vera.lms.utils.PaginationUtils;

import java.util.List;

@Service
@Transactional
public class ProgramService {

    private final ProgramRepository programRepository;

    @Autowired
    public ProgramService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    public Program createProgram(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("Program name cannot be empty");
        }
        Program program = Program.builder()
                .name(name)
                .description(description)
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
                .map(program -> new ProgramResponse(program.getId(), program.getName(), program.getDescription()))
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

    public Program updateProgram(Long id, String name, String description) {
        Program program = getProgram(id);
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("Program name cannot be empty");
        }
        program.setName(name);
        program.setDescription(description);
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
}
