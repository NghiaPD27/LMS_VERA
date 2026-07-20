package vera.lms.configs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BunnyStreamPropertiesTest {

    @Test
    void playbackBaseUrlDefaultsToBunnyPullZoneHost() {
        BunnyStreamProperties properties = new BunnyStreamProperties(
                "real",
                "api-key",
                "708365",
                "",
                "https://video.bunnycdn.com/tusupload",
                86400L);

        assertEquals("https://vz-708365.b-cdn.net", properties.playbackBaseUrlOrDefault());
    }

    @Test
    void playbackBaseUrlConvertsIframeUrlToPullZoneHost() {
        BunnyStreamProperties properties = new BunnyStreamProperties(
                "real",
                "api-key",
                "708365",
                "https://iframe.mediadelivery.net/play/708365",
                "https://video.bunnycdn.com/tusupload",
                86400L);

        assertEquals("https://vz-708365.b-cdn.net", properties.playbackBaseUrlOrDefault());
    }

    @Test
    void playbackBaseUrlKeepsCustomPullZoneHost() {
        BunnyStreamProperties properties = new BunnyStreamProperties(
                "real",
                "api-key",
                "708365",
                "https://videos.example.com/",
                "https://video.bunnycdn.com/tusupload",
                86400L);

        assertEquals("https://videos.example.com", properties.playbackBaseUrlOrDefault());
    }
}
