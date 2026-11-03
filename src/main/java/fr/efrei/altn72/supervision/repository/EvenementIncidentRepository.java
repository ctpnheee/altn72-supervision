package fr.efrei.altn72.supervision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.efrei.altn72.supervision.model.EvenementIncident;

public interface EvenementIncidentRepository extends JpaRepository<EvenementIncident, Long> {

	/** Journal d'un incident, du plus ancien au plus récent. L'identifiant départage deux lignes du même instant. */
	List<EvenementIncident> findByIncidentIdOrderByDateAscIdAsc(Long incidentId);
}
