package com.example.springcache;

import org.springframework.stereotype.Service;

import java.util.List;

/** Fixed self-invocation: a different bean, so each findById goes through ProductService's proxy. */
@Service
public class ProductPageService {

    private final ProductService productService;

    public ProductPageService(ProductService productService) {
        this.productService = productService;
    }

    public List<Product> findAll(List<Long> ids) {
        return ids.stream().map(productService::findById).toList();
    }
}
