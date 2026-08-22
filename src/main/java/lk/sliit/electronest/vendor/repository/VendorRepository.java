package lk.sliit.electronest.vendor.repository;

import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    List<Vendor> findByStatus(VendorStatus status);

    Optional<Vendor> findByUserId(Long userId);

    Optional<Vendor> findByRegistrationNumber(String registrationNumber);
}
