package fr.efrei.altn72.supervision.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IncidentDetailWebTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Clock clock;

	@Autowired
	private StationRepository stationRepository;

	@Autowired
	private EquipementRepository equipementRepository;

	@Autowired
	private PrestataireRepository prestataireRepository;

	@Autowired
	private UtilisateurRepository utilisateurRepository;

	@Autowired
	private IncidentRepository incidentRepository;

	@Autowired
	private EvaluationRepository evaluationRepository;

	private Utilisateur agent;
	private Utilisateur responsable;
	private Utilisateur autreAgent;
	private Incident ouvert;
	private Incident affecte;
	private Incident resolu;

	@BeforeEach
	void preparer() {
		Station station = stationRepository.save(new Station("Station A6"));
		Equipement equipement = equipementRepository
				.save(new Equipement("A6-EQ", "Ascenseur A6", TypeEquipement.ASCENSEUR, station));
		Prestataire prestataire = prestataireRepository.save(new Prestataire("Otis A6", "otis-a6@example.org", "0140000099"));
		agent = utilisateurRepository.save(new Utilisateur("a6-agent", "hache", "Bruno", Profil.AGENT));
		responsable = utilisateurRepository.save(new Utilisateur("a6-responsable", "hache", "Alice", Profil.RESPONSABLE));
		autreAgent = utilisateurRepository.save(new Utilisateur("a6-autre", "hache", "Chloe", Profil.AGENT));

		LocalDateTime maintenant = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		ouvert = incidentRepository.save(new Incident("Panne ouverte", maintenant.minusHours(47), equipement, agent));

		Incident aAffecter = new Incident("Panne affectee", maintenant.minusHours(50), equipement, agent);
		aAffecter.setStatut(StatutIncident.AFFECTE);
		aAffecter.setPrestataire(prestataire);
		affecte = incidentRepository.save(aAffecter);

		Incident aResoudre = new Incident("Panne resolue", maintenant.minusHours(10).minusMinutes(30), equipement, agent);
		aResoudre.setStatut(StatutIncident.RESOLU);
		aResoudre.setDateResolution(maintenant.minusHours(1));
		aResoudre.setPrestataire(prestataire);
		resolu = incidentRepository.save(aResoudre);
		evaluationRepository.save(new Evaluation(resolu, 4, "Soignee", maintenant.minusHours(1)));
	}

	@Test
	void unAgentNeModifiePasUnIncidentAffecte() throws Exception {
		mockMvc.perform(post("/incidents/" + affecte.getId() + "/description").with(connecte(agent)).with(csrf())
						.param("description", "Description pirate"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(containsString("Accès refusé")));

		assertThat(incidentRepository.findById(affecte.getId()).orElseThrow().getDescription()).isEqualTo("Panne affectee");
	}

	@Test
	void unAgentNeModifiePasLIncidentDUnAutre() throws Exception {
		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/description").with(connecte(autreAgent)).with(csrf())
						.param("description", "Description pirate"))
				.andExpect(status().isForbidden());

		assertThat(incidentRepository.findById(ouvert.getId()).orElseThrow().getDescription()).isEqualTo("Panne ouverte");
	}

	@Test
	void unAgentCorrigeLaDescriptionDeSonIncidentNonAffecte() throws Exception {
		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/description").with(connecte(agent)).with(csrf())
						.param("description", "Cabine arretee"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/incidents/" + ouvert.getId()));

		assertThat(incidentRepository.findById(ouvert.getId()).orElseThrow().getDescription()).isEqualTo("Cabine arretee");
	}

	@Test
	void unResponsableCorrigeUnIncidentAffecteMaisPasUnResolu() throws Exception {
		mockMvc.perform(post("/incidents/" + affecte.getId() + "/description").with(connecte(responsable)).with(csrf())
						.param("description", "Precision du responsable"))
				.andExpect(redirectedUrl("/incidents/" + affecte.getId()));
		assertThat(incidentRepository.findById(affecte.getId()).orElseThrow().getDescription())
				.isEqualTo("Precision du responsable");

		mockMvc.perform(post("/incidents/" + resolu.getId() + "/description").with(connecte(responsable)).with(csrf())
						.param("description", "Trop tard"))
				.andExpect(status().isForbidden());
		assertThat(incidentRepository.findById(resolu.getId()).orElseThrow().getDescription()).isEqualTo("Panne resolue");
	}

	@Test
	void leDetailMontreLaDureeLePrestataireEtLEvaluation() throws Exception {
		mockMvc.perform(get("/incidents/" + affecte.getId()).with(connecte(agent)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Durée d'ouverture")))
				.andExpect(content().string(containsString("50 h")))
				.andExpect(content().string(containsString("Pris en charge")))
				.andExpect(content().string(containsString("Otis A6")))
				.andExpect(content().string(containsString("Aucune évaluation")))
				.andExpect(content().string(containsString("Seul le responsable")))
				.andExpect(content().string(not(containsString("Enregistrer la description"))))
				.andExpect(content().string(not(containsString("Déclarer l'incident résolu"))));

		mockMvc.perform(get("/incidents/" + resolu.getId()).with(connecte(responsable)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("9 h 30 min")))
				.andExpect(content().string(containsString("Entre l&#39;ouverture et la résolution")))
				.andExpect(content().string(containsString("4 / 5")))
				.andExpect(content().string(containsString("Soignee")))
				.andExpect(content().string(not(containsString("Enregistrer la description"))))
				.andExpect(content().string(not(containsString("Déclarer l'incident résolu"))))
				.andExpect(content().string(not(containsString("Seul le responsable"))));

		mockMvc.perform(get("/incidents/" + ouvert.getId()).with(connecte(agent)))
				.andExpect(content().string(containsString("47 h")))
				.andExpect(content().string(containsString("Enregistrer la description")))
				.andExpect(content().string(containsString("pris l&#39;incident en charge")));
	}

	private static RequestPostProcessor connecte(Utilisateur utilisateur) {
		return user(new UtilisateurConnecte(utilisateur));
	}
}
