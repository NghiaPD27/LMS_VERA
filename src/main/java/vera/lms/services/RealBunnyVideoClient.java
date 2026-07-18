package vera.lms.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vera.lms.configs.BunnyStreamProperties;
import vera.lms.enums.VideoStatus;
import vera.lms.models.LessonVideo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.bunny.stream.client", havingValue = "real")
public class RealBunnyVideoClient implements BunnyVideoClient {

    private final BunnyStreamProperties bunnyStreamProperties;
    private final RestClient restClient;

    public RealBunnyVideoClient(BunnyStreamProperties bunnyStreamProperties, RestClient.Builder restClientBuilder) {
        this.bunnyStreamProperties = bunnyStreamProperties;
        this.restClient = restClientBuilder.baseUrl("https://video.bunnycdn.com").build();
    }

    @Override
    public String getPlaybackUrl(LessonVideo lessonVideo) {
        String playbackBaseUrl = bunnyStreamProperties.playbackBaseUrlOrDefault();
        String normalizedBaseUrl = playbackBaseUrl.endsWith("/")
                ? playbackBaseUrl.substring(0, playbackBaseUrl.length() - 1)
                : playbackBaseUrl;
        return normalizedBaseUrl
                + "/"
                + lessonVideo.getBunnyVideoId()
                + "/playlist.m3u8";
    }

    @Override
    public BunnyUploadSession createUploadSession(String title, String fileType) {
        bunnyStreamProperties.requireRealUploadConfig();

        String libraryId = bunnyStreamProperties.libraryId().trim();
        CreateVideoResponse video = restClient.post()
                .uri("/library/{libraryId}/videos", libraryId)
                .header("AccessKey", bunnyStreamProperties.apiKey().trim())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .body(Map.of("title", title))
                .retrieve()
                .body(CreateVideoResponse.class);

        if (video == null || isBlank(video.guid())) {
            throw new IllegalStateException("Bunny Stream did not return a video ID");
        }

        long expiresAt = Instant.now().plusSeconds(bunnyStreamProperties.uploadExpireSecondsOrDefault()).getEpochSecond();
        String signature = sha256Hex(libraryId + bunnyStreamProperties.apiKey().trim() + expiresAt + video.guid());

        return new BunnyUploadSession(
                video.guid(),
                libraryId,
                bunnyStreamProperties.tusUploadUrlOrDefault(),
                signature,
                expiresAt,
                title,
                fileType);
    }

    @Override
    public BunnyVideoMetadata getVideoMetadata(LessonVideo lessonVideo) {
        bunnyStreamProperties.requireRealUploadConfig();

        VideoDetailsResponse video = restClient.get()
                .uri("/library/{libraryId}/videos/{videoId}", lessonVideo.getLibraryId(), lessonVideo.getBunnyVideoId())
                .header("AccessKey", bunnyStreamProperties.apiKey().trim())
                .retrieve()
                .body(VideoDetailsResponse.class);

        if (video == null) {
            throw new IllegalStateException("Bunny Stream did not return video metadata");
        }

        return new BunnyVideoMetadata(
                video.length() == null ? 0 : video.length(),
                buildThumbnailUrl(lessonVideo, video.thumbnailFileName()),
                mapBunnyStatus(video.status(), video.encodeProgress(), video.length()));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign Bunny Stream upload session", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String buildThumbnailUrl(LessonVideo lessonVideo, String thumbnailFileName) {
        if (isBlank(thumbnailFileName)) {
            return lessonVideo.getThumbnailUrl();
        }
        return "https://vz-" + lessonVideo.getLibraryId() + ".b-cdn.net/"
                + lessonVideo.getBunnyVideoId()
                + "/"
                + thumbnailFileName;
    }

    private VideoStatus mapBunnyStatus(Integer status, Integer encodeProgress, Integer length) {
        if (status != null && (status == 5 || status == 6)) {
            return VideoStatus.FAILED;
        }
        if ((length != null && length > 0) && ((encodeProgress != null && encodeProgress >= 100) || (status != null && status == 4))) {
            return VideoStatus.READY;
        }
        return VideoStatus.PROCESSING;
    }

    private record CreateVideoResponse(String guid) {}
    private record VideoDetailsResponse(Integer length, Integer status, Integer encodeProgress, String thumbnailFileName) {}
}
