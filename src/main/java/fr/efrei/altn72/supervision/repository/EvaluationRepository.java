package fr.efrei.altn72.supervision.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.efrei.altn72.supervision.model.Evaluation;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

	boolean existsByIncidentId(Long incidentId);

	Optional<Evaluation> findByIncidentId(Long incidentId);

	/** Note moyenne d'un prestataire ; {@code null} s'il n'a encore reçu aucune évaluation. */
	@Query("select avg(e.note) from Evaluation e where e.incident.prestataire.id = :prestataireId")
	Double noteMoyenneDuPrestataire(@Param("prestataireId") Long prestataireId);
}
