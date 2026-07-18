package vera.lms.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.ProgramDto.ProgramResponse;
import vera.lms.mapping.ProgramMapper;
import vera.lms.models.Program;
import vera.lms.services.ProgramService;

@RestController
@RequestMapping("/api/public/programs")
public class PublicProgramController {

    private final ProgramService programService;
    private final ProgramMapper programMapper;

    public PublicProgramController(ProgramService programService, ProgramMapper programMapper) {
        this.programService = programService;
        this.programMapper = programMapper;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProgramResponse>> getPublicPrograms(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(programService.getPublicPrograms(keyword, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProgramResponse> getPublicProgram(@PathVariable Long id) {
        Program program = programService.getPublicProgram(id);
        return ResponseEntity.ok(programMapper.toResponse(program));
    }
}
