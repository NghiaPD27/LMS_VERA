package vera.lms.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import vera.lms.dtos.LessonDto.CreateLessonRequest;
import vera.lms.dtos.LessonDto.LessonResponse;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.ProgramDto.CreateProgramRequest;
import vera.lms.dtos.ProgramDto.ProgramResponse;
import vera.lms.enums.RoleName;
import vera.lms.mapping.ProgramMapper;
import vera.lms.models.Lesson;
import vera.lms.models.Program;
import vera.lms.models.User;
import vera.lms.services.LessonService;
import vera.lms.services.ProgramService;

import java.util.List;

@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramService programService;
    private final LessonService lessonService;
    private final ProgramMapper programMapper;

    @Autowired
    public ProgramController(
            ProgramService programService,
            LessonService lessonService,
            ProgramMapper programMapper) {
        this.programService = programService;
        this.lessonService = lessonService;
        this.programMapper = programMapper;
    }

    @PostMapping
    public ResponseEntity<ProgramResponse> createProgram(@RequestBody @Valid CreateProgramRequest request) {
        Program program = programService.createProgram(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(programMapper.toResponse(program));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProgramResponse> getProgram(@PathVariable Long id) {
        Program program = programService.getProgram(id);
        return ResponseEntity.ok(programMapper.toResponse(program));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProgramResponse>> getPrograms(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(programService.getPrograms(keyword, page, size));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProgramResponse> updateProgram(@PathVariable Long id, @RequestBody @Valid CreateProgramRequest request) {
        Program program = programService.updateProgram(id, request);
        return ResponseEntity.ok(programMapper.toResponse(program));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProgram(@PathVariable Long id) {
        programService.deleteProgram(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{programId}/lessons")
    public ResponseEntity<LessonResponse> createLesson(
            @PathVariable Long programId,
            @RequestBody @Valid CreateLessonRequest request) {
        Lesson lesson = lessonService.createLesson(programId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.getLessonResponse(lesson.getId()));
    }

    @GetMapping("/{programId}/lessons")
    public ResponseEntity<List<LessonResponse>> getProgramLessons(
            @PathVariable Long programId,
            @AuthenticationPrincipal User currentUser) {
        List<LessonResponse> response = currentUser.getRole().getName() == RoleName.ADMIN
                ? lessonService.getLessonsForProgram(programId)
                : lessonService.getLessonsForStudent(programId, currentUser);
        return ResponseEntity.ok(response);
    }
}
