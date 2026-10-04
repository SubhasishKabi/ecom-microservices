package com.ecommerce.notification.dto;

import com.ecommerce.notification.models.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of the internal (service-to-service) endpoint used by other services,
 * e.g. product-service when a watched product comes back in stock.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalNotificationRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "type is required")
    private NotificationType type;

    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title must be at most 255 characters")
    private String title;

    @Size(max = 1000, message = "message must be at most 1000 characters")
    private String message;

    @Size(max = 255, message = "referenceId must be at most 255 characters")
    private String referenceId;
}
