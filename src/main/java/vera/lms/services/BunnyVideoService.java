package vera.lms.services;

import org.springframework.stereotype.Service;
import vera.lms.models.LessonVideo;

@Service
public class BunnyVideoService {

    private final BunnyVideoClient bunnyVideoClient;

    public BunnyVideoService(BunnyVideoClient bunnyVideoClient) {
        this.bunnyVideoClient = bunnyVideoClient;
    }

    public String getPlaybackUrl(LessonVideo lessonVideo) {
        return bunnyVideoClient.getPlaybackUrl(lessonVideo);
    }

    public BunnyUploadSession createUploadSession(String title, String fileType) {
        return bunnyVideoClient.createUploadSession(title, fileType);
    }

    public BunnyVideoMetadata getVideoMetadata(LessonVideo lessonVideo) {
        return bunnyVideoClient.getVideoMetadata(lessonVideo);
    }
}
