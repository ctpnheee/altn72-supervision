package fr.efrei.altn72.supervision.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IncidentPilotageWebTest {

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

	private Utilisateur agent;
	private Utilisateur responsable;
	private Prestataire otis;
	private Prestataire schindler;
	private Equipement equipement;
	private Incident ouvert;
	private Incident resolu;

	@BeforeEach
	void preparer() {
		Station station = stationRepository.save(new Station("Station A7"));
		equipement = new Equipement("A7-EQ", "Escalier A7", TypeEquipement.ESCALIER_MECANIQUE, station);
		equipement.setEtat(EtatEquipement.EN_PANNE);
		equipement = equipementRepository.save(equipement);
		otis = prestataireRepository.save(new Prestataire("Otis A7", "otis-a7@example.org", "0140000071"));
		schindler = prestataireRepository.save(new Prestataire("Schindler A7", "schindler-a7@example.org", "0140000072"));
		agent = utilisateurRepository.save(new Utilisateur("a7-agent", "hache", "Bruno", Profil.AGENT));
		responsable = utilisateurRepository.save(new Utilisateur("a7-responsable", "hache", "Alice", Profil.RESPONSABLE));

		LocalDateTime maintenant = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		ouvert = incidentRepository.save(new Incident("Panne A7", maintenant.minusHours(3), equipement, agent));

		Incident aResoudre = new Incident("Panne deja resolue", maintenant.minusHours(5), equipement, agent);
		aResoudre.setStatut(StatutIncident.RESOLU);
		aResoudre.setDateResolution(maintenant.minusHours(4));
		aResoudre.setPrestataire(otis);
		resolu = incidentRepository.save(aResoudre);
	}

	@Test
	void unAgentNePeutNiAffecterNiResoudre() throws Exception {
		mockMvc.perform(get("/incidents/" + ouvert.getId()).with(connecte(agent)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Seul le responsable")))
				.andExpect(content().string(not(containsString("Déclarer l'incident résolu"))));

		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/prestataire").with(connecte(agent)).with(csrf())
						.param("prestataireId", ""))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/prestataire").with(connecte(agent)).with(csrf())
						.param("prestataireId", otis.getId().toString()))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/resolution").with(connecte(agent)).with(csrf()))
				.andExpect(status().isForbidden());

		Incident inchange = incidentRepository.findById(ouvert.getId()).orElseThrow();
		assertThat(inchange.getStatut()).isEqualTo(StatutIncident.NON_AFFECTE);
		assertThat(inchange.getPrestataire()).isNull();
		assertThat(inchange.getDateResolution()).isNull();
		assertThat(equipementRepository.findById(equipement.getId()).orElseThrow().getEtat())
				.isEqualTo(EtatEquipement.EN_PANNE);
	}

	@Test
	void unResponsableAffecteReaffectePuisResoutLeDernierIncidentOuvert() throws Exception {
		mockMvc.perform(get("/incidents/" + ouvert.getId()).with(connecte(responsable)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Affecter le prestataire")))
				.andExpect(content().string(containsString("Otis A7")))
				.andExpect(content().string(containsString("Déclarer l'incident résolu")));

		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.param("prestataireId", otis.getId().toString()))
				.andExpect(redirectedUrl("/incidents/" + ouvert.getId()))
				.andExpect(flash().attribute("succes", "Prestataire affecté."));

		Incident affecte = incidentRepository.findById(ouvert.getId()).orElseThrow();
		assertThat(affecte.getStatut()).isEqualTo(StatutIncident.AFFECTE);
		assertThat(affecte.getPrestataire().getNom()).isEqualTo("Otis A7");

		mockMvc.perform(get("/incidents/" + ouvert.getId()).with(connecte(responsable)))
				.andExpect(content().string(containsString("Réaffecter le prestataire")));

		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.param("prestataireId", schindler.getId().toString()))
				.andExpect(redirectedUrl("/incidents/" + ouvert.getId()));
		assertThat(incidentRepository.findById(ouvert.getId()).orElseThrow().getPrestataire().getNom())
				.isEqualTo("Schindler A7");

		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/resolution").with(connecte(responsable)).with(csrf()))
				.andExpect(redirectedUrl("/incidents/" + ouvert.getId()))
				.andExpect(flash().attribute("succes", "Incident résolu."));

		Incident resoluMaintenant = incidentRepository.findById(ouvert.getId()).orElseThrow();
		assertThat(resoluMaintenant.getStatut()).isEqualTo(StatutIncident.RESOLU);
		assertThat(resoluMaintenant.getDateResolution()).isAfterOrEqualTo(resoluMaintenant.getDateOuverture());
		assertThat(equipementRepository.findById(equipement.getId()).orElseThrow().getEtat())
				.isEqualTo(EtatEquipement.EN_SERVICE);

		mockMvc.perform(get("/incidents/" + ouvert.getId()).with(connecte(responsable)))
				.andExpect(content().string(not(containsString("Déclarer l'incident résolu"))));
	}

	@Test
	void resoudreSansPrestataireEstPossible() throws Exception {
		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/resolution").with(connecte(responsable)).with(csrf()))
				.andExpect(redirectedUrl("/incidents/" + ouvert.getId()));

		Incident resoluMaintenant = incidentRepository.findById(ouvert.getId()).orElseThrow();
		assertThat(resoluMaintenant.getStatut()).isEqualTo(StatutIncident.RESOLU);
		assertThat(resoluMaintenant.getPrestataire()).isNull();
		assertThat(resoluMaintenant.getDateResolution()).isAfterOrEqualTo(resoluMaintenant.getDateOuverture());
	}

	@Test
	void unAutreIncidentOuvertLaisseLEquipementEnPanne() throws Exception {
		LocalDateTime maintenant = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		Incident second = incidentRepository.save(new Incident("Seconde panne", maintenant.minusHours(1), equipement, agent));

		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/resolution").with(connecte(responsable)).with(csrf()))
				.andExpect(redirectedUrl("/incidents/" + ouvert.getId()));

		assertThat(incidentRepository.findById(ouvert.getId()).orElseThrow().getStatut()).isEqualTo(StatutIncident.RESOLU);
		assertThat(equipementRepository.findById(equipement.getId()).orElseThrow().getEtat())
				.isEqualTo(EtatEquipement.EN_PANNE);

		mockMvc.perform(post("/incidents/" + second.getId() + "/resolution").with(connecte(responsable)).with(csrf()))
				.andExpect(redirectedUrl("/incidents/" + second.getId()));
		assertThat(equipementRepository.findById(equipement.getId()).orElseThrow().getEtat())
				.isEqualTo(EtatEquipement.EN_SERVICE);
	}

	@Test
	void unIncidentResoluNePeutPlusEtreReaffecteNiResolu() throws Exception {
		String page = "http://localhost/incidents/" + resolu.getId();

		mockMvc.perform(post("/incidents/" + resolu.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.header(HttpHeaders.REFERER, page)
						.param("prestataireId", schindler.getId().toString()))
				.andExpect(redirectedUrl("/incidents/" + resolu.getId()))
				.andExpect(flash().attribute("erreur", "Un incident résolu ne peut plus être réaffecté."));
		assertThat(incidentRepository.findById(resolu.getId()).orElseThrow().getPrestataire().getId()).isEqualTo(otis.getId());

		mockMvc.perform(post("/incidents/" + resolu.getId() + "/resolution").with(connecte(responsable)).with(csrf())
						.header(HttpHeaders.REFERER, page))
				.andExpect(redirectedUrl("/incidents/" + resolu.getId()))
				.andExpect(flash().attribute("erreur", "Cet incident est déjà résolu."));
		assertThat(incidentRepository.findById(resolu.getId()).orElseThrow().getDateResolution())
				.isEqualTo(resolu.getDateResolution());
	}

	private static RequestPostProcessor connecte(Utilisateur utilisateur) {
		return user(new UtilisateurConnecte(utilisateur));
	}
}
