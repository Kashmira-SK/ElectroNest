package lk.sliit.electronest.search.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.search.service.ProductSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/search")
public class ProductSearchController {

    @Autowired
    private ProductSearchService productSearchService;

    @GetMapping("/products")
    public Page<Product> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return productSearchService.search(keyword, category, brand, minPrice, maxPrice, inStockOnly, pageable);
    }
}