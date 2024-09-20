package org.ethelred.kiwiproc.test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
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
            SELECT t.id, t.name, count(*)
            FROM types t JOIN pets p ON t.id = p.type_id GROUP BY 1,2""")
    List<PetTypeWithCount> getPetTypesWithCountList();

    default Map<PetType, Long> getPetTypesWithCount() {
        return getPetTypesWithCountList().stream()
                .map(ptc -> Map.entry(new PetType(ptc.id(), ptc.name()), ptc.count()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SqlQuery("""
                SELECT id, first_name, last_name FROM owners WHERE id = ANY(:ids)""")
    List<Owner> findOwnersByIds(List<Integer> ids);

    @SqlQuery(
            """
            SELECT o.first_name AS owner_first_name, array_agg(p.name) as pet_names
            FROM owners o JOIN pets p ON o.id = p.owner_id
            GROUP BY 1""")
    List<OwnerPets> findOwnersAndPets();

    record Visit(String pet_name, @Nullable LocalDate visit_date, String description) {}

    @SqlQuery(
            """
            INSERT INTO visits (pet_id, visit_date, description)
            SELECT p.id as pet_id, :visit_date, :description FROM pets p WHERE p.name = :pet_name
            RETURNING id""")
    Optional<Integer> addVisit(Visit visit);

    @SqlQuery(
            """
            SELECT p.name as pet_name, v.visit_date, v.description
            FROM pets p JOIN visits v ON p.id = v.pet_id
            WHERE v.id = :id""")
    @Nullable Visit getVisitById(int id);

    @SqlUpdate("""
            UPDATE visits
            SET description = :description
            WHERE id = :id""")
    int setVisitDescription(int id, String description);

    @SqlUpdate("""
            INSERT INTO vets(first_name, last_name)
            VALUES (:firstName, :lastName)""")
    void addVet(String firstName, String lastName);
}
