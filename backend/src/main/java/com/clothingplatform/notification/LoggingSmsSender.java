package com.clothingplatform.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY. Writes the SMS (including OTPs) to the server log. A real provider
 * (MSG91, Twilio, ...) implements SmsSender and is selected via app.sms.provider in Phase 13.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);
    public LoggingSmsSender() { log.warn("SMS provider is 'log': OTPs are written to the log. Do not use in production."); }
    @Override public void send(String phone, String message) { log.info("[DEV SMS to {}] {}", phone, message); }
}
