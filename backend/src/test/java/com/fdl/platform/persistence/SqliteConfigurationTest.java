package com.fdl.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fdl.platform.support.AbstractSqliteDataJpaTest;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteConfigurationTest extends AbstractSqliteDataJpaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Test
    void journalModeIsWal() {
        assertThat(pragma("journal_mode")).isEqualToIgnoringCase("wal");
    }

    @Test
    void foreignKeysAreEnforced() {
        assertThat(pragma("foreign_keys")).isEqualTo("1");
    }

    @Test
    void busyTimeoutIsFiveSeconds() {
        assertThat(pragma("busy_timeout")).isEqualTo("5000");
    }

    @Test
    void poolHasASingleConnection() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(1);
    }

    private String pragma(String name) {
        return jdbc.queryForObject("PRAGMA " + name, String.class);
    }
}
