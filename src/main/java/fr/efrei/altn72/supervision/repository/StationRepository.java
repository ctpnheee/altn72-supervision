package fr.efrei.altn72.supervision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.efrei.altn72.supervision.model.Station;

public interface StationRepository extends JpaRepository<Station, Long> {

	List<Station> findAllByOrderByNomAsc();
}
