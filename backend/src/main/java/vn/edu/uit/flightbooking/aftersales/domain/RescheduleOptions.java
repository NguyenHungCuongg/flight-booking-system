package vn.edu.uit.flightbooking.aftersales.domain;

import java.util.List;

/** Các hành trình thay thế xếp theo số tiền phải trả, rồi giờ hạ cánh. */
public record RescheduleOptions(List<RescheduleOption> options) {

}
