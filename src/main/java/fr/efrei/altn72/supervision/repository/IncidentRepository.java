package fr.efrei.altn72.supervision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

	List<Incident> findByStatut(StatutIncident statut);

	long countByStatut(StatutIncident statut);

	/** Fiche d'un équipement : incidents du plus récent au plus ancien (RG7). */
	List<Incident> findByEquipementIdOrderByDateOuvertureDesc(Long equipementId);

	/** Détail d'un incident avec tout ce que la vue affiche ; le prestataire est facultatif (left join). */
	@Query("""
			select i from Incident i
			join fetch i.equipement e
			join fetch e.station
			join fetch i.declarant
			left join fetch i.prestataire
			where i.id = :id
			""")
	Optional<Incident> findDetailParId(@Param("id") Long id);
}
