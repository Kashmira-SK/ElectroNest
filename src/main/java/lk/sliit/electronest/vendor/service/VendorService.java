package lk.sliit.electronest.vendor.service;

import lk.sliit.electronest.common.model.Role;
import lk.sliit.electronest.common.model.AccountStatus;
import lk.sliit.electronest.catalog.repository.ProductRepository;
import lk.sliit.electronest.order.repository.OrderRepository;
import lk.sliit.electronest.common.model.User;
import lk.sliit.electronest.common.repository.UserRepository;
import lk.sliit.electronest.vendor.exception.DuplicateVendorApplicationException;
import lk.sliit.electronest.vendor.exception.InvalidVendorReviewReasonException;
import lk.sliit.electronest.vendor.exception.InvalidVendorStatusTransitionException;
import lk.sliit.electronest.vendor.exception.VendorNotFoundException;
import lk.sliit.electronest.vendor.model.Vendor;
import lk.sliit.electronest.vendor.model.VendorStatus;
import lk.sliit.electronest.vendor.model.dto.VendorGuidanceResponse;
import lk.sliit.electronest.vendor.model.dto.VendorProfileUpdateRequest;
import lk.sliit.electronest.vendor.model.dto.VendorRegistrationRequest;
import lk.sliit.electronest.vendor.repository.VendorRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class VendorService {

    private final ApplicationEventPublisher events;

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public VendorService(
            VendorRepository vendorRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            ApplicationEventPublisher events) {
        this.events = events;
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Vendor registerVendor(Long userId, VendorRegistrationRequest request) {
        return registerVendor(userId, request, null);
    }

    @Transactional
    public Vendor registerVendor(
            Long userId,
            VendorRegistrationRequest request,
            String documentPath) {

        if (vendorRepository.existsByUser_Id(userId)) {
            throw new DuplicateVendorApplicationException(
                    "You already have a vendor application"
            );
        }

        String registrationNumber = request.getRegistrationNumber()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (vendorRepository.existsByRegistrationNumberIgnoreCase(registrationNumber)) {
            throw new DuplicateVendorApplicationException(
                    "This business registration number is already in use"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + userId));

        Vendor vendor = new Vendor();
        vendor.setUser(user);
        vendor.setBusinessName(request.getBusinessName().trim());
        vendor.setRegistrationNumber(registrationNumber);
        vendor.setBusinessAddress(request.getBusinessAddress().trim());
        vendor.setContactPhone(request.getContactPhone().trim());
        vendor.setIdDocumentPath(documentPath);
        vendor.setStatus(VendorStatus.PENDING);

        Vendor saved = vendorRepository.save(vendor);

        sendStatusEmail(
                saved,
                "Vendor application received",
                "Your ElectroNest vendor application has been submitted and is awaiting review."
        );

        return saved;
    }

    public List<Vendor> getVerificationQueue() {
        return vendorRepository.findByStatus(VendorStatus.PENDING);
    }

    public List<Vendor> getAllVendors() {
        return vendorRepository.findAll();
    }

    @Transactional
    public Vendor approveVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.PENDING, "approve");
        requireActiveApplicant(vendor);
        if (vendor.getIdDocumentPath() == null || vendor.getIdDocumentPath().isBlank()) {
            throw new InvalidVendorStatusTransitionException("Request a verification document before approving this application.");
        }

        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setRejectionReason(null);

        User user = vendor.getUser();
        user.setRole(Role.VENDOR);
        userRepository.save(user);

        Vendor saved = vendorRepository.save(vendor);

        sendStatusEmail(
                saved,
                "Vendor application approved",
                "Your ElectroNest vendor account has been approved. You can now manage your store and products."
        );

        return saved;
    }

    @Transactional
    public Vendor rejectVendor(Long id, String reason) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.PENDING, "reject");

        String normalizedReason = normalizeReviewReason(reason);

        vendor.setStatus(VendorStatus.REJECTED);
        vendor.setRejectionReason(normalizedReason);

        Vendor saved = vendorRepository.save(vendor);

        sendStatusEmail(
                saved,
                "Vendor application rejected",
                "Your ElectroNest vendor application was rejected. Reason: " + normalizedReason
        );

        return saved;
    }

    @Transactional
    public Vendor requestMoreInfo(Long id, String message) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.PENDING, "request more information for");

        String normalizedMessage = normalizeReviewReason(message);

        vendor.setStatus(VendorStatus.INFO_REQUESTED);
        vendor.setRejectionReason(normalizedMessage);

        Vendor saved = vendorRepository.save(vendor);

        sendStatusEmail(
                saved,
                "More vendor information required",
                "ElectroNest needs additional information before approving your application: "
                        + normalizedMessage
        );

        return saved;
    }

    @Transactional
    public Vendor suspendVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.APPROVED, "suspend");

        vendor.setStatus(VendorStatus.SUSPENDED);

        Vendor saved = vendorRepository.save(vendor);

        sendStatusEmail(
                saved,
                "Vendor account suspended",
                "Your ElectroNest vendor account has been suspended. Please contact an administrator for assistance."
        );

        return saved;
    }

    @Transactional
    public Vendor reactivateVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        requireStatus(vendor, VendorStatus.SUSPENDED, "reactivate");
        if (vendor.getUser().getStatus() != AccountStatus.ACTIVE
                || vendor.getUser().getRole() == Role.ADMIN) {
            throw new InvalidVendorStatusTransitionException("Resolve this user's account status or role in Users before reactivating the seller.");
        }

        vendor.setStatus(VendorStatus.APPROVED);
        vendor.getUser().setRole(Role.VENDOR);
        userRepository.save(vendor.getUser());

        Vendor saved = vendorRepository.save(vendor);

        sendStatusEmail(
                saved,
                "Vendor account reactivated",
                "Your ElectroNest vendor account has been reactivated."
        );

        return saved;
    }

    @Transactional
    public void revokeVendor(Long id) {
        Vendor vendor = getVendorOrThrow(id);
        if (!productRepository.findByVendorId(id).isEmpty() || !orderRepository.findByVendorId(vendor.getUser().getId()).isEmpty()) {
            throw new InvalidVendorStatusTransitionException(
                    "This seller has product or order records. Suspend the seller instead to preserve product and purchase history.");
        }
        if (vendor.getUser().getRole() == Role.VENDOR) {
            vendor.getUser().setRole(Role.CUSTOMER);
            userRepository.save(vendor.getUser());
        }

        sendStatusEmail(
                vendor,
                "Vendor access revoked",
                "Your ElectroNest vendor access has been revoked by an administrator."
        );

        vendorRepository.delete(vendor);
    }

    public Vendor getVendorOrThrow(Long id) {
        return vendorRepository.findById(id)
                .orElseThrow(() ->
                        new VendorNotFoundException("Vendor not found: " + id));
    }

    public Vendor getVendorForUser(Long userId) {
        return vendorRepository.findByUser_Id(userId)
                .orElseThrow(() ->
                        new VendorNotFoundException(
                                "Vendor application not found for the current user"
                        ));
    }

    public VendorGuidanceResponse getGuidanceForUser(Long userId) {
        Vendor vendor = getVendorForUser(userId);

        return switch (vendor.getStatus()) {
            case PENDING -> new VendorGuidanceResponse(
                    vendor.getStatus(),
                    "Application under review",
                    "Your business details and documents are being reviewed.",
                    "No action is required. Wait for the administrator's decision."
            );

            case APPROVED -> new VendorGuidanceResponse(
                    vendor.getStatus(),
                    "Store approved",
                    "Your vendor account is active and approved.",
                    "You can manage your store profile and product catalogue."
            );

            case REJECTED -> new VendorGuidanceResponse(
                    vendor.getStatus(),
                    "Application rejected",
                    vendor.getRejectionReason(),
                    "Review the rejection reason and contact an administrator if you need clarification."
            );

            case INFO_REQUESTED -> new VendorGuidanceResponse(
                    vendor.getStatus(),
                    "More information required",
                    vendor.getRejectionReason(),
                    "Update your vendor profile with the requested information and resubmit it for review."
            );

            case SUSPENDED -> new VendorGuidanceResponse(
                    vendor.getStatus(),
                    "Vendor account suspended",
                    "Your vendor account is temporarily suspended.",
                    "Contact an administrator before attempting to continue selling."
            );
        };
    }

    @Transactional
    public Vendor updateVendorDetails(
            Long userId,
            VendorProfileUpdateRequest request) {

        return updateVendorDetails(userId, request, null);
    }

    @Transactional
    public Vendor updateVendorDetails(
            Long userId,
            VendorProfileUpdateRequest request,
            String replacementDocumentPath) {

        Vendor vendor = getVendorForUser(userId);

        if (vendor.getStatus() != VendorStatus.APPROVED
                && vendor.getStatus() != VendorStatus.INFO_REQUESTED) {
            throw new InvalidVendorStatusTransitionException(
                    "Vendor details can only be edited after approval "
                            + "or when more information is requested"
            );
        }

        vendor.setBusinessName(request.getBusinessName().trim());
        vendor.setBusinessAddress(
                request.getBusinessAddress().trim()
        );
        vendor.setContactPhone(request.getContactPhone().trim());

        if (replacementDocumentPath != null
                && !replacementDocumentPath.isBlank()) {
            vendor.setIdDocumentPath(replacementDocumentPath);
        }

        if (vendor.getStatus() == VendorStatus.INFO_REQUESTED) {
            vendor.setStatus(VendorStatus.PENDING);
            vendor.setRejectionReason(null);

            sendStatusEmail(
                    vendor,
                    "Vendor information resubmitted",
                    "Your updated vendor information has been "
                            + "resubmitted for administrator review."
            );
        }

        return vendorRepository.save(vendor);
    }

    private void requireActiveApplicant(Vendor vendor) {
        if (vendor.getUser().getStatus() != AccountStatus.ACTIVE
                || vendor.getUser().getRole() != Role.CUSTOMER) {
            throw new InvalidVendorStatusTransitionException(
                    "Only an active CUSTOMER account can be approved. Resolve the account in Users first.");
        }
    }

    private void requireStatus(
            Vendor vendor,
            VendorStatus requiredStatus,
            String action) {

        if (vendor.getStatus() != requiredStatus) {
            throw new InvalidVendorStatusTransitionException(
                    "Cannot " + action + " vendor " + vendor.getId()
                            + " while status is " + vendor.getStatus()
                            + "; expected " + requiredStatus
            );
        }
    }

    private String normalizeReviewReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidVendorReviewReasonException(
                    "A review reason is required"
            );
        }

        String normalizedReason = reason.trim();

        if (normalizedReason.length() > 500) {
            throw new InvalidVendorReviewReasonException(
                    "Review reason must not exceed 500 characters"
            );
        }

        return normalizedReason;
    }

    private void sendStatusEmail(
            Vendor vendor,
            String subject,
            String message) {

        events.publishEvent(new VendorStatusEmail(
                vendor.getId(),
                vendor.getUser() == null ? null : vendor.getUser().getEmail(),
                subject, message));
    }
}
