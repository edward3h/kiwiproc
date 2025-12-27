/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testmicronaut;

import static com.google.common.truth.Truth.assertThat;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.exception.UncheckedSQLException;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test", rebuildContext = true)
public class PetClinicTest {
    @Inject
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

    @Test
    void happyAddPets() {
        var message = "Don't commit an update during test.";
        try {
            dao.run(innerDao -> {
                var result = innerDao.addPets(List.of("Hero", "Moth", "Rosie"), List.of(1, 1, 1), 3);
                assertThat(result).hasSize(3);
                throw new SQLException(message);
            });
        } catch (UncheckedSQLException e) {
            assertThat(e.getCause().getMessage()).isEqualTo(message);
        }
    }
}
