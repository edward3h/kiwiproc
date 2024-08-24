package org.ethelred.kiwiproc.test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.api.DAOProvider;
import org.junit.jupiter.api.Test;

@MicronautTest(environments = "test")
public class PetClinicTest {
    @Inject
    DAOProvider<PetClinicDAO> daoProvider;

    @Test
    void happySelectTypes() throws SQLException {
        var types = daoProvider.call(PetClinicDAO::findPetTypes);
        assertThat(types).isNotNull();
        assertThat(types).isNotEmpty();
        var typeNames = types.stream().map(PetType::name).collect(Collectors.toSet());
        assertThat(typeNames).contains("cat");
    }

    @Test
    void happySelectOneType() throws SQLException {
        var type = daoProvider.call(dao -> dao.getPetType(3));
        assertThat(type).isPresent();
        assertThat(type.get().name()).isEqualTo("lizard");
    }

    @Test
    void happySelectPetNames() throws SQLException {
        var names = daoProvider.call(PetClinicDAO::findPetNames);
        assertThat(names).isNotEmpty();
        assertThat(names).contains("Jewel");
    }

    @Test
    void happyTestDefaultMethod() throws SQLException {
        var countsByType = daoProvider.call(PetClinicDAO::getPetTypesWithCount);
        assertThat(countsByType).hasSize(6);
        assertThat(countsByType).containsEntry(new PetType(2, "dog"), 4L);
    }

    @Test
    void happyFindByArrayValues() throws SQLException {
        var owners = daoProvider.call(dao -> dao.findOwnersByIds(List.of(2, 6, 99)));
        assertThat(owners).hasSize(2);
        var firstNames = owners.stream().map(Owner::first_name).toList();
        assertThat(firstNames).containsExactly("bob", "joe");
    }
}
