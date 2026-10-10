package vn.edu.uit.flightbooking.catalog.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.flightbooking.catalog.domain.Airline;

public interface AirlineRepository extends JpaRepository<Airline, String> {

	List<Airline> findAllByOrderByCode();

	List<Airline> findByActiveTrueOrderByCode();

	Optional<Airline> findByTicketPrefix(String ticketPrefix);

}
