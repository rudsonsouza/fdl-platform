package com.fdl.platform.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * O SQLite cria o arquivo do banco, mas não o diretório que o contém. Garante o diretório
 * antes de o pool de conexões abrir o banco (ex.: {@code ./data} no primeiro start).
 */
public class SqliteDataDirectoryInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String URL_PREFIX = "jdbc:sqlite:";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ensureParentDirectory(context.getEnvironment().getProperty("spring.datasource.url"));
    }

    static void ensureParentDirectory(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(URL_PREFIX)) {
            return;
        }
        var parent = Path.of(jdbcUrl.substring(URL_PREFIX.length())).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar o diretório do banco: " + parent, e);
        }
    }
}
