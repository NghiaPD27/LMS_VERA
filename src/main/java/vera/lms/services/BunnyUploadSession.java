package vera.lms.services;

public record BunnyUploadSession(
        String videoId,
        String libraryId,
        String tusUploadUrl,
        String authorizationSignature,
        long authorizationExpire,
        String title,
        String fileType
) {}
