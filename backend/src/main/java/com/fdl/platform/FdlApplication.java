package com.fdl.platform;

import com.fdl.platform.shared.config.SqliteDataDirectoryInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class FdlApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(FdlApplication.class)
                .initializers(new SqliteDataDirectoryInitializer())
                .run(args);
    }
}
