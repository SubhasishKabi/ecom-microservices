package com.ecommerce.order.dtos;

import lombok.Data;

@Data
public class AddressDTO {
    private Long id;
    private String type;
    private String street;
    private String city;
    private String state;
    private String zipcode;
    private String country;
    private Boolean isDefault;
}
