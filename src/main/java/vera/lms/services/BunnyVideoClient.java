package vera.lms.services;

import vera.lms.models.LessonVideo;

public interface BunnyVideoClient {
    String getPlaybackUrl(LessonVideo lessonVideo);
    BunnyUploadSession createUploadSession(String title, String fileType);
    BunnyVideoMetadata getVideoMetadata(LessonVideo lessonVideo);
}
