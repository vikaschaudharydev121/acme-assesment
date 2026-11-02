package com.acme.salary.common.persistence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SQLite creates the database file but not its parent directory. Create it before the
 * DataSource initialises so a fresh checkout or an empty container volume just works.
 */
public class SqliteDataDirectoryInitializer implements EnvironmentPostProcessor {

    private static final String PREFIX = "jdbc:sqlite:";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || !url.startsWith(PREFIX)) {
            return;
        }
        String location = url.substring(PREFIX.length());
        int query = location.indexOf('?');
        if (query >= 0) {
            location = location.substring(0, query);
        }
        if (location.isBlank() || location.startsWith(":memory:") || location.startsWith("file:")) {
            return;
        }
        Path parent = Path.of(location).toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create SQLite data directory " + parent, e);
        }
    }
}
