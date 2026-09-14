package lk.sliit.electronest.search.service;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.search.repository.ProductSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ProductSearchService {

    @Autowired
    private ProductSearchRepository productRepository;

    public Page<Product> search(String keyword, String category, String brand,
                                BigDecimal minPrice, BigDecimal maxPrice,
                                Boolean inStockOnly, Pageable pageable) {
        return productRepository.findAll(
                ProductSpecification.filter(keyword, category, brand, minPrice, maxPrice, inStockOnly),
                pageable
        );
    }
}