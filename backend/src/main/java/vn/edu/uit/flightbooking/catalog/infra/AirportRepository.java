package vn.edu.uit.flightbooking.catalog.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.flightbooking.catalog.domain.Airport;

public interface AirportRepository extends JpaRepository<Airport, String> {

	List<Airport> findAllByOrderByCode();

	List<Airport> findByActiveTrueOrderByCode();

}
