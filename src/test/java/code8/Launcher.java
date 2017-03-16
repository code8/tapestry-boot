package code8;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Created by code8 on 12/10/15.
 */
@SpringBootApplication
public class Launcher {
    public static void main(String[] args) {
        new SpringApplicationBuilder(Launcher.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
