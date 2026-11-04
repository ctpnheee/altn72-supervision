package fr.efrei.altn72.supervision.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.api.dto.AffectationRequete;
import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.EvenementIncidentRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;
import fr.efrei.altn72.supervision.service.TextesJournal;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IncidentApiWebTest {

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

	@Autowired
	private EvenementIncidentRepository evenementRepository;

	private Utilisateur agent;
	private Utilisateur responsable;
	private Prestataire otis;
	private Equipement equipement;
	private Incident recent;
	private Incident ancien;
	private Incident resolu;

	@BeforeEach
	void preparer() {
		Station station = stationRepository.save(new Station("Station A8"));
		equipement = new Equipement("A8-EQ", "Ecran A8", TypeEquipement.ECRAN_INFORMATION, station);
		equipement.setEtat(EtatEquipement.EN_PANNE);
		equipement = equipementRepository.save(equipement);
		otis = prestataireRepository.save(new Prestataire("Otis A8", "otis-a8@example.org", "0140000081"));
		agent = utilisateurRepository.save(new Utilisateur("a8-agent", "hache", "Bruno", Profil.AGENT));
		responsable = utilisateurRepository.save(new Utilisateur("a8-responsable", "hache", "Alice", Profil.RESPONSABLE));

		LocalDateTime maintenant = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		ancien = incidentRepository.save(new Incident("Panne ancienne", maintenant.minusHours(5), equipement, agent));
		recent = incidentRepository.save(new Incident("Panne recente", maintenant.minusHours(2), equipement, agent));

		Incident aResoudre = new Incident("Panne deja resolue", maintenant.minusHours(10), equipement, agent);
		aResoudre.setStatut(StatutIncident.RESOLU);
		aResoudre.setDateResolution(maintenant.minusHours(8));
		aResoudre.setPrestataire(otis);
		resolu = incidentRepository.save(aResoudre);
		evaluationRepository.save(new Evaluation(resolu, 5, "Soignee", maintenant.minusHours(8)));
	}

	@Test
	void sansSessionLApiRepond401EtLaPageRedirigeVersLaConnexion() throws Exception {
		mockMvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/incidents/" + recent.getId() + "/prestataire").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prestataireId\":1}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void unAgentLitLaListeEtLeDetailMaisNePeutPasEcrire() throws Exception {
		mockMvc.perform(get("/api/incidents").with(connecte(agent)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(recent.getId().intValue()))
				.andExpect(jsonPath("$[0].dureeOuverture").value("2 h"))
				.andExpect(jsonPath("$[1].id").value(ancien.getId().intValue()));

		mockMvc.perform(get("/api/incidents/" + resolu.getId()).with(connecte(agent)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("RESOLU"))
				.andExpect(jsonPath("$.note").value(5))
				.andExpect(jsonPath("$.precisionDuree").value("Entre l'ouverture et la résolution"))
				.andExpect(jsonPath("$.niveau").value(nullValue()));

		mockMvc.perform(put("/api/incidents/" + recent.getId() + "/prestataire").with(connecte(agent)).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prestataireId\":" + otis.getId() + "}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.detail").value("Vous n'avez pas le droit d'effectuer cette action."));
		mockMvc.perform(patch("/api/incidents/" + recent.getId() + "/resolution").with(connecte(agent)).with(csrf()))
				.andExpect(status().isForbidden());

		assertThat(incidentRepository.findById(recent.getId()).orElseThrow().getStatut())
				.isEqualTo(StatutIncident.NON_AFFECTE);
	}

	@Test
	void unResponsableAffectePuisResout() throws Exception {
		mockMvc.perform(put("/api/incidents/" + recent.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prestataireId\":" + otis.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("AFFECTE"))
				.andExpect(jsonPath("$.prestataireId").value(otis.getId().intValue()))
				.andExpect(jsonPath("$.prestataireNom").value("Otis A8"))
				.andExpect(jsonPath("$.niveau").value("PRIS_EN_CHARGE"));

		mockMvc.perform(patch("/api/incidents/" + ancien.getId() + "/resolution").with(connecte(responsable)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("RESOLU"));
		assertThat(equipementRepository.findById(equipement.getId()).orElseThrow().getEtat())
				.isEqualTo(EtatEquipement.EN_PANNE);

		mockMvc.perform(patch("/api/incidents/" + recent.getId() + "/resolution").with(connecte(responsable)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("RESOLU"))
				.andExpect(jsonPath("$.dateResolution").exists());
		assertThat(equipementRepository.findById(equipement.getId()).orElseThrow().getEtat())
				.isEqualTo(EtatEquipement.EN_SERVICE);
		assertThat(evenementRepository.findByIncidentIdOrderByDateAscIdAsc(recent.getId()))
				.extracting(evenement -> evenement.getAction())
				.containsExactly(TextesJournal.affectation("Otis A8"), TextesJournal.RESOLUTION);
		assertThat(evenementRepository.findByIncidentIdOrderByDateAscIdAsc(ancien.getId()))
				.extracting(evenement -> evenement.getAuteur())
				.containsExactly("Alice");
	}

	@Test
	void unIncidentInconnuDonne404EtUneTransitionInterditeDonne409() throws Exception {
		mockMvc.perform(get("/api/incidents/999999").with(connecte(responsable)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Incident 999999 introuvable."));

		mockMvc.perform(put("/api/incidents/" + resolu.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prestataireId\":" + otis.getId() + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("Un incident résolu ne peut plus être réaffecté."));
		assertThat(incidentRepository.findById(resolu.getId()).orElseThrow().getPrestataire().getId())
				.isEqualTo(otis.getId());
	}

	@Test
	void unCorpsSansPrestataireDonne400() throws Exception {
		mockMvc.perform(put("/api/incidents/" + recent.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.erreurs.prestataireId").value(AffectationRequete.PRESTATAIRE_OBLIGATOIRE));
	}

	@Test
	void swaggerDecritLApiPourUnUtilisateurConnecte() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
		mockMvc.perform(get("/v3/api-docs").with(connecte(agent)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Supervision ALTN72"))
				.andExpect(jsonPath("$.paths['/api/incidents']").exists())
				.andExpect(jsonPath("$.paths['/api/incidents/{id}/prestataire'].put").exists())
				.andExpect(jsonPath("$.paths['/api/incidents/{id}/resolution'].patch").exists());
	}

	private static RequestPostProcessor connecte(Utilisateur utilisateur) {
		return user(new UtilisateurConnecte(utilisateur));
	}
}
