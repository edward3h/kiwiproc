package org.ethelred.kiwiproc.test;

import static com.google.common.truth.Truth.assertThat;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
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

    //    @Test
    //    void happyFindByArrayValues() throws SQLException {
    //        var owners = transactionalDao.call(dao -> dao.findOwnersByIds(List.of(2, 6, 99)));
    //        assertThat(owners).hasSize(2);
    //        var firstNames = owners.stream().map(Owner::first_name).toList();
    //        assertThat(firstNames).containsExactly("bob", "joe");
    //    }
}
