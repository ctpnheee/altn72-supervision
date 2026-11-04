package fr.efrei.altn72.supervision.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import fr.efrei.altn72.supervision.model.EvenementIncident;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.EvenementIncidentRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;
import fr.efrei.altn72.supervision.service.IncidentService;
import fr.efrei.altn72.supervision.service.TextesJournal;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JournalIncidentWebTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Clock clock;

	@Autowired
	private IncidentService incidentService;

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
	private EvenementIncidentRepository evenementRepository;

	private Utilisateur responsable;
	private Prestataire otis;
	private Prestataire schindler;
	private Equipement equipement;
	private Incident ouvert;

	@BeforeEach
	void preparer() {
		Station station = stationRepository.save(new Station("Station A9"));
		equipement = new Equipement("A9-EQ", "Escalier A9", TypeEquipement.ESCALIER_MECANIQUE, station);
		equipement.setEtat(EtatEquipement.EN_PANNE);
		equipement = equipementRepository.save(equipement);
		otis = prestataireRepository.save(new Prestataire("Otis A9", "otis-a9@example.org", "0140000091"));
		schindler = prestataireRepository.save(new Prestataire("Schindler A9", "schindler-a9@example.org", "0140000092"));
		responsable = utilisateurRepository.save(new Utilisateur("a9-responsable", "hache", "Alice", Profil.RESPONSABLE));
		LocalDateTime ouverture = LocalDateTime.ofInstant(clock.instant(), clock.getZone()).minusHours(2);
		ouvert = incidentRepository.save(new Incident("Panne A9", ouverture, equipement, responsable));
	}

	@Test
	void affecterCreeUneLigneAvecAuteurDateEtAction() throws Exception {
		LocalDateTime avant = LocalDateTime.ofInstant(clock.instant(), clock.getZone()).minusSeconds(1);

		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.param("prestataireId", otis.getId().toString()))
				.andExpect(status().is3xxRedirection());

		LocalDateTime apres = LocalDateTime.ofInstant(clock.instant(), clock.getZone()).plusSeconds(1);
		var lignes = evenementRepository.findByIncidentIdOrderByDateAscIdAsc(ouvert.getId());
		assertThat(lignes).hasSize(1);
		EvenementIncident ligne = lignes.get(0);
		assertThat(ligne.getAuteur()).isEqualTo("Alice");
		assertThat(ligne.getAction()).isEqualTo(TextesJournal.affectation("Otis A9"));
		assertThat(ligne.getDate()).isBetween(avant, apres);

		mockMvc.perform(get("/incidents/" + ouvert.getId()).with(connecte(responsable)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Alice")))
				.andExpect(content().string(containsString(TextesJournal.affectation("Otis A9"))));
	}

	@Test
	void reaffecterPuisResoudreAjoutentLesLignesCorrespondantes() throws Exception {
		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.param("prestataireId", otis.getId().toString()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.param("prestataireId", schindler.getId().toString()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/incidents/" + ouvert.getId() + "/resolution").with(connecte(responsable)).with(csrf()))
				.andExpect(status().is3xxRedirection());

		var lignes = evenementRepository.findByIncidentIdOrderByDateAscIdAsc(ouvert.getId());
		assertThat(lignes).extracting(EvenementIncident::getAction).containsExactly(
				TextesJournal.affectation("Otis A9"),
				TextesJournal.reaffectation("Otis A9", "Schindler A9"),
				TextesJournal.RESOLUTION);
		assertThat(lignes).allSatisfy(ligne -> assertThat(ligne.getAuteur()).isEqualTo("Alice"));

		mockMvc.perform(get("/incidents/" + ouvert.getId()).with(connecte(responsable)))
				.andExpect(status().isOk())
				// Thymeleaf écrit l'apostrophe &#39; : on vérifie le début de la phrase.
				.andExpect(content().string(containsString("Résolution de l")));
	}

	@Test
	void uneTransitionRefuseeNeCreePasDeLigne() throws Exception {
		Incident resolu = new Incident("Deja resolu", ouvert.getDateOuverture(), equipement, responsable);
		resolu.setStatut(StatutIncident.RESOLU);
		resolu.setDateResolution(ouvert.getDateOuverture());
		resolu.setPrestataire(otis);
		resolu = incidentRepository.save(resolu);

		mockMvc.perform(post("/incidents/" + resolu.getId() + "/prestataire").with(connecte(responsable)).with(csrf())
						.param("prestataireId", otis.getId().toString()))
				.andExpect(status().is3xxRedirection());

		assertThat(evenementRepository.findByIncidentIdOrderByDateAscIdAsc(resolu.getId())).isEmpty();
	}

	@Test
	void declarerCreeLaLigneDeDeclaration() {
		Incident declare = incidentService.declarer(equipement.getId(), "Nouvelle panne", responsable.getLogin());

		var lignes = evenementRepository.findByIncidentIdOrderByDateAscIdAsc(declare.getId());
		assertThat(lignes).hasSize(1);
		assertThat(lignes.get(0).getAuteur()).isEqualTo("Alice");
		assertThat(lignes.get(0).getAction()).isEqualTo(TextesJournal.DECLARATION);
	}

	@Test
	void unIncidentSansJournalAfficheLAbsenceDEvenement() throws Exception {
		mockMvc.perform(get("/incidents/" + ouvert.getId()).with(connecte(responsable)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Aucun événement.")));
	}

	private static RequestPostProcessor connecte(Utilisateur utilisateur) {
		return user(new UtilisateurConnecte(utilisateur));
	}
}
