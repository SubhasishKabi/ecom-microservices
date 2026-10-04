package com.ecommerce.user.dto;

import com.ecommerce.user.models.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private UserRole role;
    private Boolean active;
    private List<AddressDTO> addresses;
    private AddressDTO defaultAddress;
    private LocalDateTime createdAt;
}
