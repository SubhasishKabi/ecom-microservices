package com.ecommerce.notification.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Local email abstraction: "sends" emails by logging them to the console.
 * Swap the body of {@link #sendEmail} for a real mail transport (e.g. JavaMailSender) later.
 */
@Service
@Slf4j
public class EmailService {

    public void sendEmail(String to, String subject, String body) {
        log.info("[EMAIL] To: {} | Subject: {} | Body: {}", to, subject, body);
    }
}
