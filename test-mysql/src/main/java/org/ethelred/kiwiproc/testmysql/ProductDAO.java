/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testmysql;

import java.util.List;
import org.ethelred.kiwiproc.annotation.DAO;
import org.ethelred.kiwiproc.annotation.SqlBatch;
import org.ethelred.kiwiproc.annotation.SqlQuery;
import org.ethelred.kiwiproc.annotation.SqlUpdate;
import org.jspecify.annotations.Nullable;

@DAO
public interface ProductDAO {
    record Product(int id, String name, double price) {}

    @SqlUpdate("INSERT INTO product (name, price) VALUES (:name, :price)")
    void insertProduct(String name, double price);

    @SqlQuery("SELECT id, name, price FROM product WHERE id = :id")
    @Nullable Product findById(int id);

    @SqlQuery("SELECT id, name, price FROM product ORDER BY id")
    List<Product> listAll();

    @SqlUpdate("DELETE FROM product WHERE id = :id")
    boolean deleteById(int id);

    @SqlBatch("INSERT INTO product (name, price) VALUES (:name, :price)")
    int[] batchInsertProducts(List<String> name, List<Double> price);

    @SqlUpdate("DELETE FROM product")
    void deleteAll();

    @SqlUpdate("INSERT INTO product (name, price, metadata) VALUES (:name, :price, :metadata)")
    void insertProductWithMetadata(String name, double price, String metadata);

    @SqlQuery("SELECT metadata FROM product WHERE id = :id")
    @Nullable String findMetadataById(int id);
}
