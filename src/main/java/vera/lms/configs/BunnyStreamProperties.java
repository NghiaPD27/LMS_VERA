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
            return normalizePlaybackBaseUrl(playbackBaseUrl.trim());
        }
        if (!isBlank(libraryId)) {
            return "https://vz-" + libraryId.trim() + ".b-cdn.net";
        }
        return "https://vz.b-cdn.net";
    }

    public void requireRealUploadConfig() {
        if (isBlank(apiKey) || isBlank(libraryId)) {
            throw new IllegalStateException("Bunny Stream API key and library ID must be configured");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizePlaybackBaseUrl(String value) {
        String normalized = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        String iframePrefix = "https://iframe.mediadelivery.net/play/";
        if (normalized.startsWith(iframePrefix)) {
            String configuredLibraryId = normalized.substring(iframePrefix.length());
            int slashIndex = configuredLibraryId.indexOf('/');
            if (slashIndex >= 0) {
                configuredLibraryId = configuredLibraryId.substring(0, slashIndex);
            }
            if (!configuredLibraryId.isBlank()) {
                return "https://vz-" + configuredLibraryId + ".b-cdn.net";
            }
        }
        return normalized;
    }
}
