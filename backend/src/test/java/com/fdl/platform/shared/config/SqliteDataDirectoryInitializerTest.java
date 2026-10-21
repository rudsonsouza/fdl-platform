package com.fdl.platform.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.support.StaticApplicationContext;

class SqliteDataDirectoryInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsMissingParentDirectoryOfSqliteFile() {
        var db = tempDir.resolve("nested/data/fdl.db");

        SqliteDataDirectoryInitializer.ensureParentDirectory("jdbc:sqlite:" + db);

        assertThat(db.getParent()).isDirectory();
        assertThat(db).doesNotExist();
    }

    @Test
    void initializerReadsDatasourceUrlFromEnvironment() {
        var db = tempDir.resolve("from-env/fdl.db");
        try (var context = new StaticApplicationContext()) {
            TestPropertyValues.of("spring.datasource.url=jdbc:sqlite:" + db).applyTo(context);

            new SqliteDataDirectoryInitializer().initialize(context);
        }

        assertThat(db.getParent()).isDirectory();
    }

    @Test
    void failsWithClearMessageWhenDirectoryCannotBeCreated() throws Exception {
        var blocker = Files.createFile(tempDir.resolve("not-a-directory"));
        var db = blocker.resolve("fdl.db");

        assertThatThrownBy(() -> SqliteDataDirectoryInitializer.ensureParentDirectory("jdbc:sqlite:" + db))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("diretório do banco");
    }

    @Test
    void ignoresRelativeFileWithoutParentAndNullAndNonSqliteUrls() {
        assertThatCode(() -> {
            SqliteDataDirectoryInitializer.ensureParentDirectory("jdbc:sqlite:");
            SqliteDataDirectoryInitializer.ensureParentDirectory(null);
            SqliteDataDirectoryInitializer.ensureParentDirectory("jdbc:h2:mem:test");
        }).doesNotThrowAnyException();
    }
}
