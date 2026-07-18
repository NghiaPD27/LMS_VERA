package vera.lms.services;

import vera.lms.enums.VideoStatus;

public record BunnyVideoMetadata(
        int durationSeconds,
        String thumbnailUrl,
        VideoStatus status
) {}
