package vn.edu.uit.flightbooking.flight;

/**
 * Admin huỷ chuyến (BR-84). Listener đồng bộ: booking ({@code @Order(1)}), aftersales ({@code @Order(2)}); notification
 * gửi email sau commit.
 */
public record FlightCancelled(long flightId) {
}

