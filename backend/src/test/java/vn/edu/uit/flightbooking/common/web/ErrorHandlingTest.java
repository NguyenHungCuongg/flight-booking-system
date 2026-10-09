package vn.edu.uit.flightbooking.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.flightbooking.IntegrationTest;

@IntegrationTest
@WithMockUser
class ErrorHandlingTest {

	@Autowired
	MockMvc mvc;

	@Test
	void businessExceptionBecomesProblemDetailWithCode() throws Exception {
		mvc.perform(get("/api/test/business-error"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"))
			.andExpect(jsonPath("$.detail").value("Chuyến VN1825 hạng ECONOMY không còn đủ 3 ghế"))
			.andExpect(jsonPath("$.instance").value("/api/test/business-error"));
	}

	@Test
	void extraPropertiesAreCopiedIntoProblemDetail() throws Exception {
		mvc.perform(get("/api/test/voucher-error"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VOUCHER_INVALID"))
			.andExpect(jsonPath("$.reason").value("EXPIRED"));
	}

	@Test
	void invalidBodyListsFieldErrors() throws Exception {
		mvc.perform(post("/api/test/validate").with(csrf(mvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lastName\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("lastName"));
	}

	@Test
	void malformedJsonIsValidationFailed() throws Exception {
		mvc.perform(post("/api/test/validate").with(csrf(mvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void unknownRouteIsResourceNotFound() throws Exception {
		mvc.perform(get("/api/test/no-such-route"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void wrongParameterTypeIsValidationFailed() throws Exception {
		mvc.perform(get("/api/test/typed").param("page", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void wrongHttpMethodStillHasCode() throws Exception {
		mvc.perform(post("/api/test/business-error").with(csrf(mvc)))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void unexpectedExceptionHidesInternalDetail() throws Exception {
		mvc.perform(get("/api/test/unexpected-error"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.detail", not(containsString("nội bộ"))));
	}

}
