package vera.lms.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bunny.stream")
public record BunnyStreamProperties(
        String client,
        String apiKey,
        String libraryId,
        String playbackBaseUrl,
        String tusUploadUrl,
        Long uploadExpireSeconds
) {
    public String clientOrDefault() {
        return isBlank(client) ? "mock" : client.trim();
    }

    public String tusUploadUrlOrDefault() {
        return isBlank(tusUploadUrl) ? "https://video.bunnycdn.com/tusupload" : tusUploadUrl.trim();
    }

    public long uploadExpireSecondsOrDefault() {
        return uploadExpireSeconds == null || uploadExpireSeconds < 3600 ? 86400 : uploadExpireSeconds;
    }

    public String playbackBaseUrlOrDefault() {
        if (!isBlank(playbackBaseUrl)) {
            return playbackBaseUrl.trim();
        }
        if (!isBlank(libraryId)) {
            return "https://iframe.mediadelivery.net/play/" + libraryId.trim();
        }
        return "https://iframe.mediadelivery.net/play";
    }

    public void requireRealUploadConfig() {
        if (isBlank(apiKey) || isBlank(libraryId)) {
            throw new IllegalStateException("Bunny Stream API key and library ID must be configured");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
