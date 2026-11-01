package vn.edu.uit.flightbooking.report.domain;

import java.time.LocalDate;

public record CashFlowItem(LocalDate period, long charged, long refunded, long net) {
}
