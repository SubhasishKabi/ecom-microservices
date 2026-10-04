package com.ecommerce.user.services;

import com.ecommerce.user.dto.AddressDTO;
import com.ecommerce.user.dto.UserRequest;
import com.ecommerce.user.dto.UserResponse;
import com.ecommerce.user.models.Address;
import com.ecommerce.user.models.User;
import com.ecommerce.user.models.UserRole;
import com.ecommerce.user.repository.AddressRepository;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public List<UserResponse> fetchAllUsers() {
        return userRepository.findAll().stream()
                .map(authService::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public Page<UserResponse> fetchAllUsersPaged(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(authService::mapToUserResponse);
    }

    public Page<UserResponse> searchUsers(String query, Pageable pageable) {
        return userRepository.searchUsers(query, pageable)
                .map(authService::mapToUserResponse);
    }

    public Optional<UserResponse> fetchUser(String id) {
        try {
            Long userId = Long.valueOf(id);
            return userRepository.findById(userId)
                    .map(authService::mapToUserResponse);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public UserResponse addUser(UserRequest userRequest) {
        if (userRepository.existsByEmail(userRequest.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + userRequest.getEmail());
        }

        User user = User.builder()
                .firstName(userRequest.getFirstName())
                .lastName(userRequest.getLastName())
                .email(userRequest.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(userRequest.getPassword() != null ? userRequest.getPassword() : "Default@123"))
                .phone(userRequest.getPhone())
                .role(userRequest.getRole() != null ? userRequest.getRole() : UserRole.USER)
                .active(true)
                .build();

        if (userRequest.getAddress() != null) {
            Address address = Address.builder()
                    .user(user)
                    .type(userRequest.getAddress().getType() != null ? userRequest.getAddress().getType() : "HOME")
                    .street(userRequest.getAddress().getStreet())
                    .city(userRequest.getAddress().getCity())
                    .state(userRequest.getAddress().getState())
                    .country(userRequest.getAddress().getCountry())
                    .zipcode(userRequest.getAddress().getZipcode())
                    .isDefault(true)
                    .build();
            user.getAddresses().add(address);
        }

        User savedUser = userRepository.save(user);
        return authService.mapToUserResponse(savedUser);
    }

    @Transactional
    public boolean updateUser(String id, UserRequest updatedUserRequest) {
        try {
            Long userId = Long.valueOf(id);
            return userRepository.findById(userId)
                    .map(existingUser -> {
                        existingUser.setFirstName(updatedUserRequest.getFirstName());
                        existingUser.setLastName(updatedUserRequest.getLastName());
                        existingUser.setPhone(updatedUserRequest.getPhone());
                        if (updatedUserRequest.getRole() != null) {
                            existingUser.setRole(updatedUserRequest.getRole());
                        }
                        userRepository.save(existingUser);
                        return true;
                    }).orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Transactional
    public boolean toggleUserStatus(Long userId, boolean active) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setActive(active);
                    userRepository.save(user);
                    return true;
                }).orElse(false);
    }

    // Address Management
    public List<AddressDTO> getUserAddresses(Long userId) {
        return addressRepository.findByUserId(userId).stream()
                .map(authService::mapToAddressDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressDTO addAddress(Long userId, AddressDTO addressDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<Address> existing = addressRepository.findByUserId(userId);
        boolean isFirst = existing.isEmpty();
        boolean makeDefault = isFirst || Boolean.TRUE.equals(addressDTO.getIsDefault());

        if (makeDefault) {
            existing.forEach(a -> a.setIsDefault(false));
            addressRepository.saveAll(existing);
        }

        Address address = Address.builder()
                .user(user)
                .type(addressDTO.getType() != null ? addressDTO.getType() : "HOME")
                .street(addressDTO.getStreet())
                .city(addressDTO.getCity())
                .state(addressDTO.getState())
                .country(addressDTO.getCountry())
                .zipcode(addressDTO.getZipcode())
                .isDefault(makeDefault)
                .build();

        Address saved = addressRepository.save(address);
        return authService.mapToAddressDTO(saved);
    }

    @Transactional
    public Optional<AddressDTO> updateAddress(Long userId, Long addressId, AddressDTO addressDTO) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .map(address -> {
                    address.setType(addressDTO.getType() != null ? addressDTO.getType() : address.getType());
                    address.setStreet(addressDTO.getStreet());
                    address.setCity(addressDTO.getCity());
                    address.setState(addressDTO.getState());
                    address.setCountry(addressDTO.getCountry());
                    address.setZipcode(addressDTO.getZipcode());

                    if (Boolean.TRUE.equals(addressDTO.getIsDefault())) {
                        List<Address> all = addressRepository.findByUserId(userId);
                        all.forEach(a -> a.setIsDefault(false));
                        addressRepository.saveAll(all);
                        address.setIsDefault(true);
                    }

                    Address updated = addressRepository.save(address);
                    return authService.mapToAddressDTO(updated);
                });
    }

    @Transactional
    public boolean deleteAddress(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .map(address -> {
                    addressRepository.delete(address);
                    return true;
                }).orElse(false);
    }

    @Transactional
    public boolean setDefaultAddress(Long userId, Long addressId) {
        List<Address> addresses = addressRepository.findByUserId(userId);
        boolean found = false;
        for (Address addr : addresses) {
            if (addr.getId().equals(addressId)) {
                addr.setIsDefault(true);
                found = true;
            } else {
                addr.setIsDefault(false);
            }
        }
        if (found) {
            addressRepository.saveAll(addresses);
        }
        return found;
    }
}
