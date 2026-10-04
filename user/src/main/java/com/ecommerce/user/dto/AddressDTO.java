package com.ecommerce.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDTO {
    private Long id;
    private String type; // HOME, WORK, OTHER
    private String street;
    private String city;
    private String state;
    private String country;
    private String zipcode;
    private Boolean isDefault;
}
