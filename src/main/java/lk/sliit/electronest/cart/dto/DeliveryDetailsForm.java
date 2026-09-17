package lk.sliit.electronest.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeliveryDetailsForm {

    @NotBlank(message = "Delivery name is required")
    @Size(max = 100, message = "Delivery name must be 100 characters or fewer")
    private String deliveryName;

    @NotBlank(message = "Contact number is required")
    @Size(max = 20, message = "Contact number must be 20 characters or fewer")
    private String deliveryPhone;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 200, message = "Address line 1 must be 200 characters or fewer")
    private String deliveryAddressLine1;

    @Size(max = 200, message = "Address line 2 must be 200 characters or fewer")
    private String deliveryAddressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must be 100 characters or fewer")
    private String deliveryCity;

    @Size(max = 20, message = "Postal code must be 20 characters or fewer")
    private String deliveryPostalCode;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country must be 100 characters or fewer")
    private String deliveryCountry;
}
