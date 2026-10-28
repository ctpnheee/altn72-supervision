package fr.efrei.altn72.supervision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.efrei.altn72.supervision.model.Equipement;

public interface EquipementRepository extends JpaRepository<Equipement, Long> {

	List<Equipement> findByStationIdOrderByReferenceAsc(Long stationId);

	boolean existsByReference(String reference);

	/** {@code join fetch} charge la station dans la même requête : la vue n'a plus besoin de la base. */
	@Query("select e from Equipement e join fetch e.station where e.id = :id")
	Optional<Equipement> findAvecStationParId(@Param("id") Long id);
}
