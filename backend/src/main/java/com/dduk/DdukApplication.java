package com.dduk;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class DdukApplication {

    public static void main(String[] args) {
        Path[] dotenvPaths = new Path[] {
                Path.of(".env"),
                Path.of("..", ".env")
        };

        for (Path dotenvPath : dotenvPaths) {
            loadDotenv(dotenvPath);
        }

        SpringApplication.run(DdukApplication.class, args);
    }

    private static void loadDotenv(Path dotenvPath) {
        if (!Files.exists(dotenvPath)) {
            return;
        }

        Dotenv dotenv = Dotenv.configure()
                .directory(dotenvPath.toAbsolutePath().getParent().toString())
                .filename(dotenvPath.getFileName().toString())
                .ignoreIfMissing()
                .load();

        for (DotenvEntry entry : dotenv.entries()) {
            System.setProperty(entry.getKey(), System.getProperty(entry.getKey(), entry.getValue()));
        }
    }
}
