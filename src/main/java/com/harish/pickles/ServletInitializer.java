package com.harish.pickles;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Entry point when the WAR is deployed to an external servlet container (Tomcat 10.1+). The
 * container finds this class and starts the application; {@link PicklesApplication#main} is only
 * used when the WAR is run on its own with {@code java -jar}.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(PicklesApplication.class);
    }
}
