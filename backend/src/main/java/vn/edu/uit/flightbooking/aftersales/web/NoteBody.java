package vn.edu.uit.flightbooking.aftersales.web;

import jakarta.validation.constraints.Size;

public record NoteBody(@Size(max = 500) String note) {

}
