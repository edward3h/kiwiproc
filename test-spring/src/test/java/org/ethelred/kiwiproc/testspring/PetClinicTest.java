/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testspring;

import static com.google.common.truth.Truth.assertThat;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class PetClinicTest {
    @Autowired
    PetClinicDAO dao;

    @Test
    void happySelectTypes() throws SQLException {
        var types = dao.findPetTypes();
        assertThat(types).isNotNull();
        assertThat(types).isNotEmpty();
        var typeNames = types.stream().map(PetType::name).collect(Collectors.toSet());
        assertThat(typeNames).contains("cat");
    }

    @Test
    void happySelectOneType() throws SQLException {
        var type = dao.call(dao -> dao.getPetType(3));
        assertThat(type).isPresent();
        assertThat(type.get().name()).isEqualTo("lizard");
    }

    @Test
    void happySelectPetNames() throws SQLException {
        var names = dao.call(PetClinicDAO::findPetNames);
        assertThat(names).isNotEmpty();
        assertThat(names).contains("Jewel");
    }

    @Test
    void happyTestDefaultMethod() throws SQLException {
        var countsByType = dao.call(PetClinicDAO::getPetTypesWithCount);
        assertThat(countsByType).hasSize(6);
        assertThat(countsByType).containsEntry(new PetType(2, "dog"), 4L);
    }

    @Test
    void happyFindByArrayValues() throws SQLException {
        var owners = dao.findOwnersByIds(List.of(2, 6, 99));
        assertThat(owners).hasSize(2);
        var firstNames = owners.stream().map(Owner::firstName).toList();
        assertThat(firstNames).containsExactly("Betty", "Jean");
    }

    @Test
    void happyOwnersWithPets() {
        var ownersPets = dao.findOwnersAndPets();
        assertThat(ownersPets).hasSize(10);
        assertThat(ownersPets).contains(new OwnerPets("Eduardo", List.of("Rosy", "Jewel")));
    }

    @Test
    void happySetVisitDescription() {
        var affected = dao.setVisitDescription(1, "updated description");
        assertThat(affected).isEqualTo(1);

        var notAffected = dao.setVisitDescription(-999, "no match");
        assertThat(notAffected).isEqualTo(0);
    }

    @Test
    void callRollsBackOnException() {
        var visitBefore = dao.getVisitById(1);
        Assertions.assertThrows(
                UncheckedSQLException.class,
                () -> dao.<Void>call(innerDao -> {
                    innerDao.setVisitDescription(1, "Should be rolled back");
                    throw new SQLException("force rollback");
                }));
        var visitAfter = dao.getVisitById(1);
        assertThat(visitAfter.description()).isEqualTo(visitBefore.description());
    }

    @Test
    void happyAddVisit() {
        var visitId =
                dao.addVisit(new PetClinicDAO.Visit("Jewel", LocalDate.of(2025, Month.JUNE, 13), "Some text here."));
        assertThat(visitId).isPresent();
        visitId.ifPresent(id -> {
            assertThat(id).isEqualTo(5);

            var visit = dao.getVisitById(id);
            assertThat(visit).isNotNull();
            assertThat(visit.description()).contains("Some text");
        });
    }
}
