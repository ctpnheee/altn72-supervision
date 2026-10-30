package fr.efrei.altn72.supervision.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;
import fr.efrei.altn72.supervision.service.NiveauAttentionCalculator;

@SpringBootTest(properties = "app.demo.enabled=true")
class DemoDataLoaderTest {

	@Autowired
	private DemoDataLoader demoDataLoader;

	@Autowired
	private IncidentRepository incidentRepository;

	@Autowired
	private EquipementRepository equipementRepository;

	@Autowired
	private StationRepository stationRepository;

	@Autowired
	private PrestataireRepository prestataireRepository;

	@Autowired
	private EvaluationRepository evaluationRepository;

	@Autowired
	private UtilisateurRepository utilisateurRepository;

	@Autowired
	private NiveauAttentionCalculator niveauAttentionCalculator;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private Clock clock;

	@BeforeEach
	void assurerLesDonneesDeDemo() {
		demoDataLoader.executer(false);
	}

	@Test
	void auMoinsUnIncidentExistePourChaqueStatut() {
		assertThat(incidentRepository.countByStatut(StatutIncident.NON_AFFECTE)).isPositive();
		assertThat(incidentRepository.countByStatut(StatutIncident.AFFECTE)).isPositive();
		assertThat(incidentRepository.countByStatut(StatutIncident.RESOLU)).isPositive();
	}

	@Test
	void chaqueNiveauDAttentionEstRepresente() {
		Set<NiveauAttention> niveaux = incidentRepository.findAll().stream()
				.filter(incident -> incident.getStatut() != StatutIncident.RESOLU)
				.map(niveauAttentionCalculator::calculer)
				.collect(Collectors.toSet());

		assertThat(niveaux).containsExactlyInAnyOrder(NiveauAttention.values());
	}

	@Test
	void lesIncidentsNonAffectesCouvrentLesSeuilsDAttention() {
		LocalDateTime maintenant = LocalDateTime.now(clock);
		Set<Long> agesEnHeures = incidentRepository.findByStatut(StatutIncident.NON_AFFECTE).stream()
				.map(incident -> Duration.between(incident.getDateOuverture(), maintenant).toHours())
				.collect(Collectors.toSet());

		assertThat(agesEnHeures).contains(2L, 47L, 48L, 71L, 72L, 100L);
	}

	@Test
	void tousLesTypesDEquipementEtPlusieursStationsSontPresents() {
		assertThat(stationRepository.findAll()).extracting(Station::getNom)
				.contains("Châtelet", "Gare de Lyon", "République");
		assertThat(equipementRepository.findAll()).extracting(Equipement::getType)
				.contains(TypeEquipement.values());
		assertThat(prestataireRepository.count()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void lesDeuxComptesOntUnMotDePasseHache() {
		Utilisateur responsable = utilisateurRepository.findByLogin(DemoDataLoader.LOGIN_RESPONSABLE).orElseThrow();
		Utilisateur agent = utilisateurRepository.findByLogin(DemoDataLoader.LOGIN_AGENT).orElseThrow();

		assertThat(responsable.getProfil().name()).isEqualTo("RESPONSABLE");
		assertThat(agent.getProfil().name()).isEqualTo("AGENT");
		assertThat(passwordEncoder.matches(DemoDataLoader.MOT_DE_PASSE_DEMO, responsable.getMotDePasse())).isTrue();
		assertThat(passwordEncoder.matches(DemoDataLoader.MOT_DE_PASSE_DEMO, agent.getMotDePasse())).isTrue();
		assertThat(responsable.getMotDePasse()).isNotEqualTo(DemoDataLoader.MOT_DE_PASSE_DEMO);
	}

	@Test
	void unIncidentResoluEstEvalue() {
		assertThat(evaluationRepository.count()).isPositive();
		assertThat(incidentRepository.findByStatut(StatutIncident.RESOLU))
				.extracting(Incident::getDateResolution)
				.doesNotContainNull()
				.doesNotHaveDuplicates();
	}

	@Test
	void unSecondChargementSansResetNeDupliquePasLesIncidents() {
		long nombreIncidents = incidentRepository.count();
		long nombreStations = stationRepository.count();

		demoDataLoader.executer(false);

		assertThat(incidentRepository.count()).isEqualTo(nombreIncidents);
		assertThat(stationRepository.count()).isEqualTo(nombreStations);
	}

	@Test
	void leResetRecreeLesDonnees() {
		stationRepository.save(new Station("Station à supprimer"));
		assertThat(stationRepository.findAll()).extracting(Station::getNom).contains("Station à supprimer");

		demoDataLoader.executer(true);

		assertThat(stationRepository.findAll()).extracting(Station::getNom)
				.contains("Châtelet", "Gare de Lyon", "République")
				.doesNotContain("Station à supprimer");
		assertThat(incidentRepository.countByStatut(StatutIncident.NON_AFFECTE)).isPositive();
		assertThat(utilisateurRepository.findByLogin(DemoDataLoader.LOGIN_RESPONSABLE)).isPresent();
	}
}
