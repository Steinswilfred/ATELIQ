package com.clothingplatform.fulfilment;

import java.time.Instant;
import java.util.List;

/**
 * Extension point for a real courier or logistics tracking integration (Shiprocket, Delhivery, ...).
 * NO implementation exists yet: until one is added, tracking is whatever an admin records. A future implementation is a
 * Spring bean that says which courier it handles and returns scan events; DeliveryService merges them into the customer's view.
 */
public interface CourierTrackingProvider {
    record ScanEvent(String status, String note, String location, Instant at) {}
    boolean supports(String courierName);
    List<ScanEvent> fetch(String trackingNumber);
}
