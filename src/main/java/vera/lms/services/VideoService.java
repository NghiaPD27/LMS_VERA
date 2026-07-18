package vera.lms.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.VideoDto.LessonVideoResponse;
import vera.lms.dtos.VideoDto.CreateVideoUploadSessionRequest;
import vera.lms.dtos.VideoDto.UpdateVideoProgressRequest;
import vera.lms.dtos.VideoDto.UpsertLessonVideoRequest;
import vera.lms.dtos.VideoDto.VideoUploadSessionResponse;
import vera.lms.dtos.VideoDto.VideoPlaybackResponse;
import vera.lms.dtos.VideoDto.VideoProgressResponse;
import vera.lms.enums.EnrollmentStatus;
import vera.lms.enums.LessonProgressStatus;
import vera.lms.enums.LessonStatus;
import vera.lms.enums.RoleName;
import vera.lms.enums.VideoStatus;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.Lesson;
import vera.lms.models.LessonVideo;
import vera.lms.models.StudentLessonProgress;
import vera.lms.models.User;
import vera.lms.models.VideoProgress;
import vera.lms.repositories.EnrollmentRepository;
import vera.lms.repositories.LessonRepository;
import vera.lms.repositories.LessonVideoRepository;
import vera.lms.repositories.StudentLessonProgressRepository;
import vera.lms.repositories.VideoProgressRepository;

import java.time.Instant;

@Service
@Transactional
public class VideoService {

    private static final int VIDEO_COMPLETION_THRESHOLD_PERCENT = 90;
    private static final int MAX_SINGLE_PROGRESS_JUMP_SECONDS = 120;
    private static final int PLAYBACK_POSITION_GRACE_SECONDS = 5;

    private final LessonRepository lessonRepository;
    private final LessonVideoRepository lessonVideoRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final StudentLessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BunnyVideoService bunnyVideoService;

    public VideoService(
            LessonRepository lessonRepository,
            LessonVideoRepository lessonVideoRepository,
            VideoProgressRepository videoProgressRepository,
            StudentLessonProgressRepository lessonProgressRepository,
            EnrollmentRepository enrollmentRepository,
            BunnyVideoService bunnyVideoService) {
        this.lessonRepository = lessonRepository;
        this.lessonVideoRepository = lessonVideoRepository;
        this.videoProgressRepository = videoProgressRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.bunnyVideoService = bunnyVideoService;
    }

    public LessonVideoResponse upsertLessonVideo(Long lessonId, UpsertLessonVideoRequest request) {
        Lesson lesson = lessonRepository.findByIdAndStatusNot(lessonId, LessonStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + lessonId));

        LessonVideo lessonVideo = lessonVideoRepository.findByLessonId(lessonId)
                .orElseGet(() -> LessonVideo.builder().lesson(lesson).build());
        lessonVideo.setBunnyVideoId(request.bunnyVideoId().trim());
        lessonVideo.setLibraryId(request.libraryId().trim());
        lessonVideo.setDurationSeconds(request.durationSeconds());
        lessonVideo.setThumbnailUrl(normalizeBlankToNull(request.thumbnailUrl()));
        lessonVideo.setStatus(parseVideoStatusOrDefault(request.status()));
        return toLessonVideoResponse(lessonVideoRepository.save(lessonVideo));
    }

    public VideoUploadSessionResponse createUploadSession(Long lessonId, CreateVideoUploadSessionRequest request) {
        Lesson lesson = lessonRepository.findByIdAndStatusNot(lessonId, LessonStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + lessonId));

        String title = normalizeUploadTitle(request.title(), lesson);
        String fileType = normalizeFileType(request.fileType());
        BunnyUploadSession uploadSession = bunnyVideoService.createUploadSession(title, fileType);

        LessonVideo lessonVideo = lessonVideoRepository.findByLessonId(lessonId)
                .orElseGet(() -> LessonVideo.builder().lesson(lesson).build());
        lessonVideo.setBunnyVideoId(uploadSession.videoId());
        lessonVideo.setLibraryId(uploadSession.libraryId());
        lessonVideo.setDurationSeconds(0);
        lessonVideo.setThumbnailUrl(null);
        lessonVideo.setStatus(VideoStatus.PROCESSING);
        lessonVideo = lessonVideoRepository.save(lessonVideo);

        return new VideoUploadSessionResponse(
                lessonId,
                lessonVideo.getId(),
                uploadSession.videoId(),
                uploadSession.libraryId(),
                uploadSession.tusUploadUrl(),
                uploadSession.authorizationSignature(),
                uploadSession.authorizationExpire(),
                uploadSession.title(),
                uploadSession.fileType(),
                lessonVideo.getStatus().name());
    }

    public LessonVideoResponse syncLessonVideo(Long lessonId) {
        LessonVideo lessonVideo = getLessonVideo(lessonId);
        BunnyVideoMetadata metadata = bunnyVideoService.getVideoMetadata(lessonVideo);

        lessonVideo.setDurationSeconds(metadata.durationSeconds());
        lessonVideo.setThumbnailUrl(metadata.thumbnailUrl());
        lessonVideo.setStatus(metadata.status());
        return toLessonVideoResponse(lessonVideoRepository.save(lessonVideo));
    }

    @Transactional(readOnly = true)
    public VideoPlaybackResponse getPlayback(Long lessonId, User student) {
        LessonVideo lessonVideo = getLessonVideo(lessonId);
        ensureVideoReady(lessonVideo);
        ensureStudentCanAccessLessonVideo(lessonId, student);
        return new VideoPlaybackResponse(
                lessonId,
                lessonVideo.getId(),
                bunnyVideoService.getPlaybackUrl(lessonVideo),
                lessonVideo.getDurationSeconds(),
                lessonVideo.getThumbnailUrl(),
                lessonVideo.getStatus().name());
    }

    public VideoProgressResponse updateProgress(Long lessonId, User student, UpdateVideoProgressRequest request) {
        LessonVideo lessonVideo = getLessonVideo(lessonId);
        ensureVideoReady(lessonVideo);
        StudentLessonProgress lessonProgress = ensureStudentCanAccessLessonVideo(lessonId, student);

        VideoProgress progress = videoProgressRepository
                .findByStudentIdAndLessonVideoId(student.getId(), lessonVideo.getId())
                .orElseGet(() -> VideoProgress.builder()
                        .student(student)
                        .lessonVideo(lessonVideo)
                        .build());

        int reportedFurthestSecond = Math.max(request.currentSecond(), request.furthestWatchedSecond());
        validateProgressReport(lessonVideo, progress, request, reportedFurthestSecond);

        int clampedCurrentSecond = Math.min(request.currentSecond(), lessonVideo.getDurationSeconds());
        int clampedFurthestSecond = Math.min(reportedFurthestSecond, lessonVideo.getDurationSeconds());
        int watchedPercentage = calculateWatchedPercentage(clampedFurthestSecond, lessonVideo.getDurationSeconds());

        progress.setCurrentSecond(clampedCurrentSecond);
        progress.setFurthestWatchedSecond(clampedFurthestSecond);
        progress.setWatchedPercentage(watchedPercentage);
        progress.setCompleted(watchedPercentage >= VIDEO_COMPLETION_THRESHOLD_PERCENT);
        progress = videoProgressRepository.save(progress);

        if (progress.isCompleted() && lessonProgress.getStatus() == LessonProgressStatus.VIDEO_IN_PROGRESS) {
            lessonProgress.setStatus(LessonProgressStatus.QUIZ_AVAILABLE);
            lessonProgress = lessonProgressRepository.save(lessonProgress);
        }

        return toVideoProgressResponse(progress, lessonProgress);
    }

    private LessonVideo getLessonVideo(Long lessonId) {
        return lessonVideoRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found for lesson id " + lessonId));
    }

    private StudentLessonProgress ensureStudentCanAccessLessonVideo(Long lessonId, User student) {
        if (student == null || student.getRole() == null || student.getRole().getName() != RoleName.STUDENT) {
            throw new ForbiddenException("Only students can access lesson video");
        }

        Lesson lesson = lessonRepository.findByIdAndStatusNot(lessonId, LessonStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + lessonId));
        if (lesson.getStatus() != LessonStatus.PUBLISHED) {
            throw new ForbiddenException("Lesson is not published");
        }

        boolean isEnrolled = enrollmentRepository.existsAccessibleEnrollment(
                student.getId(), lesson.getProgram().getId(), EnrollmentStatus.ACTIVE, Instant.now());
        if (!isEnrolled) {
            throw new ForbiddenException("Course enrollment is expired or unavailable");
        }

        StudentLessonProgress progress = lessonProgressRepository
                .findByStudentIdAndLessonId(student.getId(), lessonId)
                .orElseThrow(() -> new ForbiddenException("Lesson is locked"));
        if (progress.getStatus() == LessonProgressStatus.LOCKED) {
            throw new ForbiddenException("Lesson is locked");
        }
        return progress;
    }

    private void ensureVideoReady(LessonVideo lessonVideo) {
        if (lessonVideo.getStatus() != VideoStatus.READY) {
            throw new ForbiddenException("Lesson video is not ready for playback");
        }
    }

    private void validateProgressReport(
            LessonVideo lessonVideo,
            VideoProgress progress,
            UpdateVideoProgressRequest request,
            int reportedFurthestSecond) {
        if (request.currentSecond() > lessonVideo.getDurationSeconds() + PLAYBACK_POSITION_GRACE_SECONDS) {
            throw new BadRequestException("Current second exceeds video duration");
        }
        if (request.furthestWatchedSecond() > lessonVideo.getDurationSeconds()) {
            throw new BadRequestException("Furthest watched second exceeds video duration");
        }
        if (request.furthestWatchedSecond() < progress.getFurthestWatchedSecond()) {
            throw new BadRequestException("Furthest watched second cannot decrease");
        }
        int jumpSeconds = reportedFurthestSecond - progress.getFurthestWatchedSecond();
        if (!progress.isCompleted() && jumpSeconds > MAX_SINGLE_PROGRESS_JUMP_SECONDS) {
            throw new BadRequestException("Video progress jump is too large");
        }
    }

    private int calculateWatchedPercentage(int furthestSecond, int durationSeconds) {
        return Math.min(100, (int) Math.floor((furthestSecond * 100.0) / durationSeconds));
    }

    private VideoStatus parseVideoStatusOrDefault(String status) {
        if (status == null || status.trim().isEmpty()) {
            return VideoStatus.READY;
        }
        try {
            return VideoStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid video status: " + status);
        }
    }

    private String normalizeUploadTitle(String title, Lesson lesson) {
        if (title == null || title.trim().isEmpty()) {
            return lesson.getProgram().getName() + " - " + lesson.getName();
        }
        return title.trim();
    }

    private String normalizeFileType(String fileType) {
        String normalized = fileType == null ? "" : fileType.trim().toLowerCase();
        if (!normalized.startsWith("video/")) {
            throw new BadRequestException("File type must be a video MIME type");
        }
        return normalized;
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private LessonVideoResponse toLessonVideoResponse(LessonVideo lessonVideo) {
        return new LessonVideoResponse(
                lessonVideo.getId(),
                lessonVideo.getLesson().getId(),
                lessonVideo.getBunnyVideoId(),
                lessonVideo.getLibraryId(),
                lessonVideo.getDurationSeconds(),
                lessonVideo.getThumbnailUrl(),
                lessonVideo.getStatus().name(),
                lessonVideo.getCreatedAt(),
                lessonVideo.getUpdatedAt());
    }

    private VideoProgressResponse toVideoProgressResponse(VideoProgress progress, StudentLessonProgress lessonProgress) {
        return new VideoProgressResponse(
                progress.getLessonVideo().getLesson().getId(),
                progress.getLessonVideo().getId(),
                progress.getCurrentSecond(),
                progress.getFurthestWatchedSecond(),
                progress.getWatchedPercentage(),
                progress.isCompleted(),
                lessonProgress.getStatus().name(),
                progress.getUpdatedAt());
    }
}
