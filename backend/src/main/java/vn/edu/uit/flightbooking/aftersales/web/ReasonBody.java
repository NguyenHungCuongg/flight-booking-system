package vn.edu.uit.flightbooking.aftersales.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonBody(@NotBlank @Size(max = 500) String reason) {

}
