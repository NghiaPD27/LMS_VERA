package vera.lms.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vera.lms.enums.VideoStatus;
import vera.lms.models.LessonVideo;

@Component
@ConditionalOnProperty(name = "app.bunny.stream.client", havingValue = "mock", matchIfMissing = true)
public class MockBunnyVideoClient implements BunnyVideoClient {

    @Override
    public String getPlaybackUrl(LessonVideo lessonVideo) {
        return "https://mock-bunny.local/libraries/"
                + lessonVideo.getLibraryId()
                + "/videos/"
                + lessonVideo.getBunnyVideoId()
                + "/playback.m3u8";
    }

    @Override
    public BunnyUploadSession createUploadSession(String title, String fileType) {
        String normalizedTitle = title == null || title.trim().isEmpty() ? "Untitled Video" : title.trim();
        String videoId = "mock-video-" + Math.abs(normalizedTitle.hashCode());
        return new BunnyUploadSession(
                videoId,
                "mock-library",
                "https://video.bunnycdn.com/tusupload",
                "mock-signature-" + videoId,
                java.time.Instant.now().plusSeconds(86400).getEpochSecond(),
                normalizedTitle,
                fileType);
    }

    @Override
    public BunnyVideoMetadata getVideoMetadata(LessonVideo lessonVideo) {
        int durationSeconds = lessonVideo.getDurationSeconds() > 0 ? lessonVideo.getDurationSeconds() : 600;
        String thumbnailUrl = lessonVideo.getThumbnailUrl() != null
                ? lessonVideo.getThumbnailUrl()
                : "https://mock-bunny.local/libraries/"
                        + lessonVideo.getLibraryId()
                        + "/videos/"
                        + lessonVideo.getBunnyVideoId()
                        + "/thumbnail.jpg";
        return new BunnyVideoMetadata(durationSeconds, thumbnailUrl, VideoStatus.READY);
    }
}
