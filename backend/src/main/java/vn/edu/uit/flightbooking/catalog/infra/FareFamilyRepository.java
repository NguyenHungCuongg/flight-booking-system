package vn.edu.uit.flightbooking.catalog.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.flightbooking.catalog.domain.FareFamily;

public interface FareFamilyRepository extends JpaRepository<FareFamily, Long> {

	List<FareFamily> findByAirlineCodeOrderById(String airlineCode);

	Optional<FareFamily> findByAirlineCodeAndName(String airlineCode, String name);

}
