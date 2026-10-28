package fr.efrei.altn72.supervision.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;

@DataJpaTest
class IncidentRepositoryTest {

	private static final LocalDateTime OUVERTURE = LocalDateTime.of(2026, 10, 1, 8, 0);

	@Autowired
	private IncidentRepository incidentRepository;

	@Autowired
	private TestEntityManager entityManager;

	private Equipement equipement;
	private Utilisateur declarant;

	@BeforeEach
	void preparerEquipementEtDeclarant() {
		Station station = entityManager.persist(new Station("Châtelet"));
		equipement = entityManager
				.persist(new Equipement("ASC-001", "Ascenseur quai A", TypeEquipement.ASCENSEUR, station));
		declarant = entityManager.persist(new Utilisateur("agent", "hash", "Alice", Profil.AGENT));
	}

	@Test
	void unIncidentDeclareEstNonAffecteEtSansPrestataire() {
		Incident incident = incidentRepository.save(new Incident("Porte bloquée", OUVERTURE, equipement, declarant));

		assertThat(incident.getStatut()).isEqualTo(StatutIncident.NON_AFFECTE);
		assertThat(incident.getPrestataire()).isNull();
		assertThat(incident.getDateResolution()).isNull();
	}

	@Test
	void requeteDeriveeTriLesIncidentsDuPlusRecentAuPlusAncien() {
		entityManager.persist(new Incident("Le plus ancien", OUVERTURE, equipement, declarant));
		entityManager.persist(new Incident("Le plus récent", OUVERTURE.plusDays(2), equipement, declarant));
		entityManager.persist(new Incident("Au milieu", OUVERTURE.plusDays(1), equipement, declarant));
		entityManager.flush();

		List<Incident> incidents = incidentRepository.findByEquipementIdOrderByDateOuvertureDesc(equipement.getId());

		assertThat(incidents).extracting(Incident::getDescription)
				.containsExactly("Le plus récent", "Au milieu", "Le plus ancien");
	}

	@Test
	void requeteJpqlChargeLesRelationsAffichees() {
		Prestataire prestataire = entityManager.persist(new Prestataire("Otis", "otis@example.org", "0102030405"));
		Incident incident = new Incident("Porte bloquée", OUVERTURE, equipement, declarant);
		incident.setPrestataire(prestataire);
		incident.setStatut(StatutIncident.AFFECTE);
		entityManager.persist(incident);
		entityManager.flush();
		entityManager.clear();

		Incident relu = incidentRepository.findDetailParId(incident.getId()).orElseThrow();

		assertThat(Hibernate.isInitialized(relu.getEquipement())).isTrue();
		assertThat(Hibernate.isInitialized(relu.getEquipement().getStation())).isTrue();
		assertThat(Hibernate.isInitialized(relu.getDeclarant())).isTrue();
		assertThat(relu.getPrestataire().getNom()).isEqualTo("Otis");
	}

	@Test
	void requeteJpqlRetrouveAussiUnIncidentSansPrestataire() {
		Incident incident = entityManager.persistAndFlush(new Incident("Porte bloquée", OUVERTURE, equipement, declarant));
		entityManager.clear();

		Incident relu = incidentRepository.findDetailParId(incident.getId()).orElseThrow();

		assertThat(relu.getPrestataire()).isNull();
	}
}
