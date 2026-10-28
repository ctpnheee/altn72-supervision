package fr.efrei.altn72.supervision.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import jakarta.validation.ConstraintViolationException;

@DataJpaTest
class EvaluationRepositoryTest {

	private static final LocalDateTime OUVERTURE = LocalDateTime.of(2026, 10, 1, 8, 0);

	@Autowired
	private EvaluationRepository evaluationRepository;

	@Autowired
	private TestEntityManager entityManager;

	private Equipement equipement;
	private Utilisateur declarant;
	private Prestataire prestataire;

	@BeforeEach
	void preparerDonnees() {
		Station station = entityManager.persist(new Station("Châtelet"));
		equipement = entityManager
				.persist(new Equipement("ASC-001", "Ascenseur quai A", TypeEquipement.ASCENSEUR, station));
		declarant = entityManager.persist(new Utilisateur("responsable", "hash", "Bob", Profil.RESPONSABLE));
		prestataire = entityManager.persist(new Prestataire("Otis", "otis@example.org", "0102030405"));
	}

	private Incident incidentAffecte(String description) {
		Incident incident = new Incident(description, OUVERTURE, equipement, declarant);
		incident.setPrestataire(prestataire);
		return entityManager.persist(incident);
	}

	@Test
	void uneNoteHorsBornesEstRefusee() {
		Incident incident = incidentAffecte("Porte bloquée");

		assertThatThrownBy(() -> evaluationRepository.saveAndFlush(new Evaluation(incident, 6, null, OUVERTURE)))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> evaluationRepository.saveAndFlush(new Evaluation(incident, 0, null, OUVERTURE)))
				.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void unIncidentNePeutEtreEvalueQuUneFois() {
		Incident incident = incidentAffecte("Porte bloquée");
		evaluationRepository.saveAndFlush(new Evaluation(incident, 4, "Rapide", OUVERTURE));

		assertThatThrownBy(() -> evaluationRepository.saveAndFlush(new Evaluation(incident, 5, null, OUVERTURE)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void existsByIncidentIdDitSiUnIncidentEstDejaEvalue() {
		Incident evalue = incidentAffecte("Déjà évalué");
		Incident nonEvalue = incidentAffecte("Pas encore évalué");
		evaluationRepository.saveAndFlush(new Evaluation(evalue, 4, null, OUVERTURE));

		assertThat(evaluationRepository.existsByIncidentId(evalue.getId())).isTrue();
		assertThat(evaluationRepository.existsByIncidentId(nonEvalue.getId())).isFalse();
	}

	@Test
	void requeteJpqlCalculeLaNoteMoyenneDuPrestataire() {
		evaluationRepository.save(new Evaluation(incidentAffecte("Incident 1"), 4, null, OUVERTURE));
		evaluationRepository.save(new Evaluation(incidentAffecte("Incident 2"), 5, null, OUVERTURE));
		evaluationRepository.save(new Evaluation(incidentAffecte("Incident 3"), 3, null, OUVERTURE));
		evaluationRepository.flush();

		assertThat(evaluationRepository.noteMoyenneDuPrestataire(prestataire.getId())).isEqualTo(4.0);
	}

	@Test
	void laNoteMoyenneEstNulleSansEvaluation() {
		assertThat(evaluationRepository.noteMoyenneDuPrestataire(prestataire.getId())).isNull();
	}
}
