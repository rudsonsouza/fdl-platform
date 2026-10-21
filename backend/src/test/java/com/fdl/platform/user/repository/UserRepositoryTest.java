package com.fdl.platform.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fdl.platform.support.AbstractSqliteDataJpaTest;
import com.fdl.platform.support.Users;
import com.fdl.platform.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class UserRepositoryTest extends AbstractSqliteDataJpaTest {

    @Autowired
    UserRepository users;

    @Test
    void savesAndFindsByEmailIgnoringCase() {
        var saved = users.saveAndFlush(Users.ana());

        assertThat(users.findByEmailIgnoreCase("ANA@Exemplo.com")).get().extracting(User::getId)
                .isEqualTo(saved.getId());
        assertThat(users.existsByEmailIgnoreCase("Ana@EXEMPLO.com")).isTrue();
        assertThat(users.findByEmailIgnoreCase("outra@exemplo.com")).isEmpty();
    }

    @Test
    void persistsTheEmailNormalizedAndTheTimestamps() {
        var saved = users.saveAndFlush(User.create("  Ana  ", "  ANA@Exemplo.com ", "hash", Users.CLOCK));

        var found = users.findById(saved.getId()).orElseThrow();
        assertThat(found.getEmail()).isEqualTo("ana@exemplo.com");
        assertThat(found.getName()).isEqualTo("Ana");
        assertThat(found.getCreatedAt()).isEqualTo(Users.CLOCK.instant());
    }

    @Test
    void duplicateEmailIsRejectedByTheDatabaseEvenWithDifferentCase() {
        users.saveAndFlush(Users.ana());
        var duplicate = User.create("Outra Ana", "x@x.com", "hash", Users.CLOCK);
        // Contorna a normalização do domínio para provar o índice único (COLLATE NOCASE).
        org.springframework.test.util.ReflectionTestUtils.setField(duplicate, "email", "ANA@EXEMPLO.COM");

        assertThatThrownBy(() -> users.saveAndFlush(duplicate))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("SQLITE_CONSTRAINT");
    }
}
