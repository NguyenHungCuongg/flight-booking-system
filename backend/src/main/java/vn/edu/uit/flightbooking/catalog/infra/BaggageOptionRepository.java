package vn.edu.uit.flightbooking.catalog.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.flightbooking.catalog.domain.BaggageOption;

public interface BaggageOptionRepository extends JpaRepository<BaggageOption, Long> {

	List<BaggageOption> findByAirlineCodeOrderByWeightKg(String airlineCode);

	List<BaggageOption> findByAirlineCodeAndActiveTrueOrderByWeightKg(String airlineCode);

	Optional<BaggageOption> findByAirlineCodeAndWeightKg(String airlineCode, int weightKg);

}
