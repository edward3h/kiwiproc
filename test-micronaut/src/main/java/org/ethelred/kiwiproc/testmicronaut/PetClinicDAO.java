/* (C) Edward Harman 2024 */
package org.ethelred.kiwiproc.testmicronaut;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlBatch;
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
    Set<String> findPetNames();

    @SqlQuery("""
            SELECT id, name FROM types WHERE id = :id""")
    Optional<PetType> getPetType(int id);

    @SqlQuery(
            sql =
                    """
            SELECT t.id, t.name, coalesce(count(*), 0) AS count
            FROM types t JOIN pets p ON t.id = p.type_id GROUP BY 1,2""",
            valueColumn = "count")
    Map<PetType, Long> getPetTypesWithCount();

    @SqlQuery("""
                SELECT id, first_name, last_name FROM owners WHERE id = ANY(:ids)""")
    List<Owner> findOwnersByIds(List<Integer> ids);

    @SqlQuery(
            """
            SELECT o.first_name AS owner_first_name, array_agg(p.name) as pet_names
            FROM owners o JOIN pets p ON o.id = p.owner_id
            GROUP BY 1""")
    List<OwnerPets> findOwnersAndPets();

    record Visit(String petName, LocalDate visitDate, @Nullable String description) {}

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

    @SqlBatch(
            """
            INSERT INTO pets(name, type_id, owner_id)
            VALUES(:name, :type_id, :owner_id)""")
    List<Integer> addPets(List<String> name, List<Integer> typeId, int ownerId);
}
