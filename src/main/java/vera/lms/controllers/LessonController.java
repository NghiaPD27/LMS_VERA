package vera.lms.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import vera.lms.dtos.LessonDto.LessonResponse;
import vera.lms.dtos.LessonDto.UpdateLessonRequest;
import vera.lms.mapping.LessonMapper;
import vera.lms.models.Lesson;
import vera.lms.services.LessonService;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;
    private final LessonMapper lessonMapper;

    @Autowired
    public LessonController(LessonService lessonService, LessonMapper lessonMapper) {
        this.lessonService = lessonService;
        this.lessonMapper = lessonMapper;
    }

    @GetMapping("/{id}")
    public ResponseEntity<LessonResponse> getLesson(@PathVariable Long id) {
        return ResponseEntity.ok(lessonService.getLessonResponse(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<LessonResponse> updateLesson(@PathVariable Long id, @RequestBody @Valid UpdateLessonRequest request) {
        Lesson lesson = lessonService.updateLesson(id, request);
        return ResponseEntity.ok(lessonMapper.toResponse(lesson));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLesson(@PathVariable Long id) {
        lessonService.deleteLesson(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<LessonResponse> publishLesson(@PathVariable Long id) {
        Lesson lesson = lessonService.publishLesson(id);
        return ResponseEntity.ok(lessonMapper.toResponse(lesson));
    }
}
