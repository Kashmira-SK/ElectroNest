package lk.sliit.electronest.catalog.controller;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ProductViewController {

    private final ProductService productService;

    public ProductViewController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public String showProducts(Model model) {
        List<Product> products = productService.getAllProducts();

        model.addAttribute("categories",
                products.stream()
                        .map(Product::getCategory)
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList());

        model.addAttribute("brands",
                products.stream()
                        .map(Product::getBrand)
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList());

        return "catalog/products";
    }
}
