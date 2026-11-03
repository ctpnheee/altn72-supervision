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

	/**
	 * Incidents encore ouverts d'un équipement. Le décompte est fait avant de passer
	 * l'incident courant à {@code RESOLU} : sinon la requête ne le compterait plus.
	 */
	long countByEquipementIdAndStatutNot(Long equipementId, StatutIncident statut);

	/** Fiche d'un équipement : incidents du plus récent au plus ancien (RG7). */
	List<Incident> findByEquipementIdOrderByDateOuvertureDesc(Long equipementId);

	/** Liste de l'API : du plus récent au plus ancien, relations déjà chargées (`open-in-view` est désactivé). */
	@Query("""
			select i from Incident i
			join fetch i.equipement e
			join fetch e.station
			join fetch i.declarant
			left join fetch i.prestataire
			order by i.dateOuverture desc
			""")
	List<Incident> findAllDetails();

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
