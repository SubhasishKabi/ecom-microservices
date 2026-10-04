package com.ecommerce.user.services;

import com.ecommerce.user.dto.*;
import com.ecommerce.user.models.Address;
import com.ecommerce.user.models.User;
import com.ecommerce.user.models.UserRole;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.JwtUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @PostConstruct
    public void initDefaultUsers() {
        if (userRepository.count() == 0) {
            log.info("Bootstrapping default ADMIN and USER accounts...");

            User admin = User.builder()
                    .firstName("Admin")
                    .lastName("User")
                    .email("admin@ecom.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .phone("+1-555-0100")
                    .role(UserRole.ADMIN)
                    .active(true)
                    .build();
            userRepository.save(admin);

            User user = User.builder()
                    .firstName("Demo")
                    .lastName("Customer")
                    .email("user@ecom.com")
                    .password(passwordEncoder.encode("User@123"))
                    .phone("+1-555-0101")
                    .role(UserRole.USER)
                    .active(true)
                    .build();

            Address defaultAddress = Address.builder()
                    .user(user)
                    .type("HOME")
                    .street("123 Market Street")
                    .city("San Francisco")
                    .state("CA")
                    .zipcode("94105")
                    .country("USA")
                    .isDefault(true)
                    .build();
            user.getAddresses().add(defaultAddress);

            userRepository.save(user);
            log.info("Default users initialized successfully: admin@ecom.com and user@ecom.com");
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered: " + request.getEmail());
        }

        UserRole role = UserRole.USER;
        if (request.getRole() != null && request.getRole().equalsIgnoreCase("ADMIN")) {
            role = UserRole.ADMIN;
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(role)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);

        String accessToken = jwtUtils.generateAccessToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(savedUser.getId(), savedUser.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getExpirationMs() / 1000)
                .user(mapToUserResponse(savedUser))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!user.getActive()) {
            throw new IllegalStateException("Account has been deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getExpirationMs() / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtUtils.validateToken(request.getRefreshToken())) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        Long userId = Long.valueOf(jwtUtils.extractUserId(request.getRefreshToken()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getActive()) {
            throw new IllegalStateException("Account is deactivated");
        }

        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getExpirationMs() / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password does not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public UserResponse mapToUserResponse(User user) {
        List<AddressDTO> addressDTOs = user.getAddresses() != null ? user.getAddresses().stream()
                .map(this::mapToAddressDTO)
                .collect(Collectors.toList()) : List.of();

        AddressDTO defaultAddressDTO = addressDTOs.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                .findFirst()
                .orElse(addressDTOs.isEmpty() ? null : addressDTOs.get(0));

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .active(user.getActive())
                .addresses(addressDTOs)
                .defaultAddress(defaultAddressDTO)
                .createdAt(user.getCreatedAt())
                .build();
    }

    public AddressDTO mapToAddressDTO(Address address) {
        return AddressDTO.builder()
                .id(address.getId())
                .type(address.getType() != null ? address.getType() : "HOME")
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zipcode(address.getZipcode())
                .country(address.getCountry())
                .isDefault(address.getIsDefault())
                .build();
    }
}
