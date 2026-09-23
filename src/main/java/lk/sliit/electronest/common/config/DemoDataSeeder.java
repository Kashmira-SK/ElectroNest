package lk.sliit.electronest.common.config;

import lk.sliit.electronest.catalog.model.Product;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private final lk.sliit.electronest.cart.promo.PromoCodeRepository promoCodes;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            UserRepository userRepository,
            VendorRepository vendorRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder,
            lk.sliit.electronest.cart.promo.PromoCodeRepository promoCodes) {
        this.promoCodes = promoCodes;

        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (promoCodes.findByCodeIgnoreCase("WELCOME10").isEmpty()) {
            var promo = new lk.sliit.electronest.cart.promo.PromoCode();
            promo.setCode("WELCOME10");
            promo.setDiscountType(lk.sliit.electronest.cart.promo.PromoCode.DiscountType.PERCENTAGE);
            promo.setDiscountValue(new BigDecimal("10"));
            promoCodes.save(promo);
        }

        createOrUpdateUser(
                "ElectroNest Admin",
                "admin@electronest.lk",
                "Admin@123",
                "0700000001",
                Role.ADMIN
        );

        User customer = createOrUpdateUser(
                "Demo Customer",
                "customer@electronest.lk",
                "Customer@123",
                "0700000002",
                Role.CUSTOMER
        );

        customer.setDeliveryName("Demo Customer");
        customer.setDeliveryPhone("0700000002");
        customer.setDeliveryAddressLine1("25 Galle Road");
        customer.setDeliveryAddressLine2(null);
        customer.setDeliveryCity("Colombo");
        customer.setDeliveryPostalCode("00300");
        customer.setDeliveryCountry("Sri Lanka");
        userRepository.save(customer);

        User vendorUser = createOrUpdateUser(
                "Demo Vendor",
                "vendor@electronest.lk",
                "Vendor@123",
                "0700000003",
                Role.VENDOR
        );

        Vendor vendor = vendorRepository.findByUser_Id(vendorUser.getId())
                .orElseGet(Vendor::new);

        vendor.setUser(vendorUser);
        vendor.setBusinessName("ElectroNest Demo Electronics");
        vendor.setRegistrationNumber("DEMO-VENDOR-001");
        vendor.setBusinessAddress("100 Tech Avenue, Colombo");
        vendor.setContactPhone("0700000003");
        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setRejectionReason(null);

        vendor = vendorRepository.save(vendor);

        if (productRepository.findByVendorId(vendor.getId()).isEmpty()) {
            productRepository.saveAll(List.of(
                    product(
                            "WH-1000XM5 Wireless Headphones",
                            "Sony",
                            "Audio",
                            "Wireless noise-cancelling over-ear headphones with long battery life.",
                            "124900.00",
                            12,
                            vendor.getId()
                    ),
                    product(
                            "MX Master 3S",
                            "Logitech",
                            "Accessories",
                            "Wireless productivity mouse with quiet clicks and multi-device support.",
                            "32900.00",
                            18,
                            vendor.getId()
                    ),
                    product(
                            "Keychron K2 Pro",
                            "Keychron",
                            "Accessories",
                            "Compact mechanical keyboard with wireless and wired connectivity.",
                            "38900.00",
                            9,
                            vendor.getId()
                    ),
                    product(
                            "980 PRO 1TB NVMe SSD",
                            "Samsung",
                            "Storage",
                            "PCIe 4.0 NVMe solid-state drive with 1TB storage capacity.",
                            "28400.00",
                            14,
                            vendor.getId()
                    ),
                    product(
                            "TUF Gaming VG27AQ3A",
                            "ASUS",
                            "Monitors",
                            "27-inch QHD gaming monitor with high refresh rate and adaptive sync.",
                            "109900.00",
                            6,
                            vendor.getId()
                    ),
                    product(
                            "AirPods Pro",
                            "Apple",
                            "Audio",
                            "Wireless in-ear headphones with active noise cancellation.",
                            "89900.00",
                            10,
                            vendor.getId()
                    ),
                    product(
                            "G502 X",
                            "Logitech",
                            "Accessories",
                            "Performance wired gaming mouse with programmable controls.",
                            "21900.00",
                            20,
                            vendor.getId()
                    ),
                    product(
                            "Portable SSD T7 1TB",
                            "Samsung",
                            "Storage",
                            "Compact USB-C portable solid-state drive for fast external storage.",
                            "36900.00",
                            7,
                            vendor.getId()
                    )
            ));
        }

        System.out.println();
        System.out.println("=========================================================");
        System.out.println(" ElectroNest demo environment ready");
        System.out.println();
        System.out.println(" Admin:    admin@electronest.lk / Admin@123");
        System.out.println(" Customer: customer@electronest.lk / Customer@123");
        System.out.println(" Vendor:   vendor@electronest.lk / Vendor@123");
        System.out.println();
        System.out.println(" http://localhost:8081");
        System.out.println("=========================================================");
        System.out.println();
    }

    private User createOrUpdateUser(
            String fullName,
            String email,
            String rawPassword,
            String contactNumber,
            Role role) {

        User user = userRepository.findByEmail(email)
                .orElseGet(User::new);

        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setContactNumber(contactNumber);
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);

        return userRepository.save(user);
    }

    private Product product(
            String name,
            String brand,
            String category,
            String description,
            String price,
            int stock,
            Long vendorId) {

        Product product = new Product();

        product.setName(name);
        product.setBrand(brand);
        product.setCategory(category);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setImageUrl(null);
        product.setOutOfStock(stock <= 0);
        product.setVendorId(vendorId);

        return product;
    }
}
