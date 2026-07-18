package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vera.lms.dtos.VideoDto.CreateVideoUploadSessionRequest;
import vera.lms.dtos.VideoDto.LessonVideoResponse;
import vera.lms.dtos.VideoDto.UpdateVideoProgressRequest;
import vera.lms.dtos.VideoDto.UpsertLessonVideoRequest;
import vera.lms.dtos.VideoDto.VideoUploadSessionResponse;
import vera.lms.dtos.VideoDto.VideoPlaybackResponse;
import vera.lms.dtos.VideoDto.VideoProgressResponse;
import vera.lms.models.User;
import vera.lms.services.VideoService;

@RestController
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/api/lessons/{lessonId}/video")
    public ResponseEntity<LessonVideoResponse> upsertLessonVideo(
            @PathVariable Long lessonId,
            @RequestBody @Valid UpsertLessonVideoRequest request) {
        return ResponseEntity.ok(videoService.upsertLessonVideo(lessonId, request));
    }

    @PostMapping("/api/lessons/{lessonId}/video-upload-session")
    public ResponseEntity<VideoUploadSessionResponse> createLessonVideoUploadSession(
            @PathVariable Long lessonId,
            @RequestBody @Valid CreateVideoUploadSessionRequest request) {
        return ResponseEntity.ok(videoService.createUploadSession(lessonId, request));
    }

    @PostMapping("/api/lessons/{lessonId}/video/sync")
    public ResponseEntity<LessonVideoResponse> syncLessonVideo(@PathVariable Long lessonId) {
        return ResponseEntity.ok(videoService.syncLessonVideo(lessonId));
    }

    @GetMapping("/api/lessons/{lessonId}/video-playback")
    public ResponseEntity<VideoPlaybackResponse> getLessonVideoPlayback(
            @PathVariable Long lessonId,
            @AuthenticationPrincipal User student) {
        return ResponseEntity.ok(videoService.getPlayback(lessonId, student));
    }

    @PostMapping("/api/lessons/{lessonId}/video-progress")
    public ResponseEntity<VideoProgressResponse> updateLessonVideoProgress(
            @PathVariable Long lessonId,
            @AuthenticationPrincipal User student,
            @RequestBody @Valid UpdateVideoProgressRequest request) {
        return ResponseEntity.ok(videoService.updateProgress(lessonId, student, request));
    }
}
