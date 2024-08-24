package org.ethelred.kiwiproc.test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.api.TransactionalDAO;
import org.jspecify.annotations.Nullable;

@DAO
public interface PetClinicDAO extends TransactionalDAO<PetClinicDAO> {
    @SqlQuery("""
           SELECT id, name FROM types""")
    List<PetType> findPetTypes();

    @SqlQuery("""
            SELECT name FROM pets""")
    Set<@Nullable String> findPetNames(); // TODO shouldn't need to declare nullable in a collection

    @SqlQuery("""
            SELECT id, name FROM types WHERE id = :id""")
    Optional<PetType> getPetType(int id);

    record PetTypeWithCount(
            int id, @Nullable String name, Long count) {} // TODO should be able to convert count to Integer

    @SqlQuery(
            """
            SELECT t.id, t.name, count(*) FROM types t JOIN pets p ON t.id = p.type_id GROUP BY 1,2""")
    List<PetTypeWithCount> getPetTypesWithCountList();

    default Map<PetType, Long> getPetTypesWithCount() {
        return getPetTypesWithCountList().stream()
                .map(ptc -> Map.entry(new PetType(ptc.id(), ptc.name()), ptc.count()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    //    @SqlQuery("""
    //            SELECT id, first_name, last_name FROM owners WHERE id = ANY(:ids)""")
    //    List<Owner> findOwnersByIds(List<Integer> ids);
}
