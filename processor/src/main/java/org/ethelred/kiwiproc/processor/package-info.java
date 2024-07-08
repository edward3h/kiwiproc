@GeneratePrism(DAO.class)
@GeneratePrism(SqlQuery.class)
        @GeneratePrism(SqlUpdate.class)
        @GeneratePrism(SqlBatch.class)
        @NullMarked
package org.ethelred.kiwiproc.processor;

import io.avaje.prism.GeneratePrism;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlBatch;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.jspecify.annotations.NullMarked;
