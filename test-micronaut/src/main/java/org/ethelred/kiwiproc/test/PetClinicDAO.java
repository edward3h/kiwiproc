package org.ethelred.kiwiproc.test;

import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlQuery;

import java.util.List;

@DAO
public interface PetClinicDAO {
    @SqlQuery("""
           SELECT id, name FROM types""")
    List<PetType> findPetTypes();
}
