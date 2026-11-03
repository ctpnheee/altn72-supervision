package fr.efrei.altn72.supervision.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.efrei.altn72.supervision.model.Evaluation;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

	boolean existsByIncidentId(Long incidentId);

	Optional<Evaluation> findByIncidentId(Long incidentId);

	/** Évaluations des incidents d'une liste, en une requête plutôt qu'une par incident. */
	@Query("select e from Evaluation e where e.incident.id in :ids")
	List<Evaluation> findByIncidentIdIn(@Param("ids") Collection<Long> ids);

	/** Note moyenne d'un prestataire ; {@code null} s'il n'a encore reçu aucune évaluation. */
	@Query("select avg(e.note) from Evaluation e where e.incident.prestataire.id = :prestataireId")
	Double noteMoyenneDuPrestataire(@Param("prestataireId") Long prestataireId);
}
