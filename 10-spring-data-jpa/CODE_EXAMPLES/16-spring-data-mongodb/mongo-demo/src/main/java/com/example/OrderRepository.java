package com.example;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

// Same idea as JpaRepository (topic 03): declare methods, Spring Data writes the queries.
// The difference: these become MongoDB queries (JSON filters), not SQL.
public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByCustomerId(String customerId);

    // "Items" + "Sku": a path INTO the embedded array. Matches orders where ANY item has this sku.
    List<Order> findByItemsSku(String sku);

    // Nested field on an embedded object.
    List<Order> findByShippingAddressCityAndStatus(String city, String status);

    // When the method name gets silly, write the filter yourself - the MongoDB equivalent of @Query in JPQL.
    @Query("{ 'items': { $elemMatch: { 'quantity': { $gte: ?0 } } } }")
    List<Order> findWithAnyItemQuantityAtLeast(int quantity);
}
