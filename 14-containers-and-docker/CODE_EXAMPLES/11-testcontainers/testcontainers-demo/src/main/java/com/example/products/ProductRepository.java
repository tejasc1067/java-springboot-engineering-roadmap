package com.example.products;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // IFNULL is a MySQL/H2 function. H2 accepts it, so every H2 test passes.
    // PostgreSQL has no IFNULL - this query fails the first time it runs in production.
    @Query(value = "select * from product where ifnull(stock, 0) > 0", nativeQuery = true)
    List<Product> findInStockBroken();

    // COALESCE is standard SQL: H2, PostgreSQL, MySQL, Oracle and SQL Server all have it.
    @Query(value = "select * from product where coalesce(stock, 0) > 0", nativeQuery = true)
    List<Product> findInStock();
}
