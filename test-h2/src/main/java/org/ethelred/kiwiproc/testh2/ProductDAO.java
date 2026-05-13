/* (C) Edward Harman 2025 */
package org.ethelred.kiwiproc.testh2;

import java.util.Collection;
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

    @SqlQuery("SELECT id, name, price FROM product ORDER BY id")
    Collection<Product> listAllAsCollection();

    @SqlQuery("SELECT id, name, price FROM product ORDER BY id")
    Iterable<Product> listAllAsIterable();

    @SqlQuery("SELECT name FROM product ORDER BY id")
    String[] listAllNamesAsArray();

    @SqlQuery(value = "SELECT id, name, price FROM product ORDER BY id", fetchSize = 5)
    List<Product> listAllWithFetchSize();

    @SqlBatch(value = "INSERT INTO product (name, price) VALUES (:name, :price)", batchSize = 2)
    void batchInsertWithSize(List<String> name, List<Double> price);

    @SqlUpdate("DELETE FROM product")
    void deleteAll();
}
