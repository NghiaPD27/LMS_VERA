package vera.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LmsVeraApplication {

    public static void main(String[] args) {
        SpringApplication.run(LmsVeraApplication.class, args);
    }

}
