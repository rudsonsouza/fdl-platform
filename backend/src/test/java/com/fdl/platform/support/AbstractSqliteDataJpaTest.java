package com.fdl.platform.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

/**
 * Base dos testes de repositório: SQLite real em arquivo temporário (não {@code :memory:}, que
 * não é compartilhado entre conexões), schema criado pelo Flyway e cada teste revertido ao fim.
 *
 * <p>O diretório é criado uma vez por execução da JVM, e não por classe com {@code @TempDir}:
 * o Spring reaproveita o contexto entre as subclasses, e um diretório por classe seria apagado
 * enquanto o contexto em cache ainda aponta para ele.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public abstract class AbstractSqliteDataJpaTest {

    private static final Path DB_DIR = createDatabaseDirectory();

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_DIR.resolve("fdl-test.db"));
    }

    private static Path createDatabaseDirectory() {
        try {
            var dir = Files.createTempDirectory("fdl-test-db-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> FileSystemUtils.deleteRecursively(dir.toFile())));
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
