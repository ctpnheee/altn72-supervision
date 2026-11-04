package fr.efrei.altn72.supervision.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.service.IncidentService;
import fr.efrei.altn72.supervision.service.IncidentService.DetailIncident;
import fr.efrei.altn72.supervision.service.IncidentService.PrestataireChoix;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IncidentService incidentService;

	private static RequestPostProcessor connecte(Profil profil) {
		return user(new UtilisateurConnecte(new Utilisateur("alice", "hache", "Alice", profil)));
	}

	@Test
	void leFormulaireEstAffiche() throws Exception {
		when(incidentService.listerEquipements()).thenReturn(List.of());

		mockMvc.perform(get("/incidents/nouveau").with(connecte(Profil.AGENT)))
				.andExpect(status().isOk())
				.andExpect(view().name("incident/declarer"))
				.andExpect(content().string(containsString("Déclarer un incident")))
				.andExpect(content().string(containsString("Choisir un équipement")))
				.andExpect(content().string(containsString("sans prestataire")));
	}

	@Test
	void unFormulaireSansDescriptionEstReaffiche() throws Exception {
		when(incidentService.listerEquipements()).thenReturn(List.of());

		mockMvc.perform(post("/incidents").with(connecte(Profil.AGENT)).with(csrf())
						.param("equipementId", "7")
						.param("description", "   "))
				.andExpect(status().isOk())
				.andExpect(view().name("incident/declarer"))
				.andExpect(content().string(containsString(Incident.DESCRIPTION_OBLIGATOIRE)));
		verify(incidentService, never()).declarer(any(), any(), any());
	}

	@Test
	void unFormulaireSansEquipementEstReafficheAvecLaDescriptionSaisie() throws Exception {
		when(incidentService.listerEquipements()).thenReturn(List.of());

		mockMvc.perform(post("/incidents").with(connecte(Profil.RESPONSABLE)).with(csrf())
						.param("description", "Porte bloquée"))
				.andExpect(status().isOk())
				.andExpect(view().name("incident/declarer"))
				.andExpect(content().string(containsString("Choisissez un équipement.")))
				.andExpect(content().string(containsString("Porte bloqu&eacute;e")));
		verify(incidentService, never()).declarer(any(), any(), any());
	}

	@Test
	void unFormulaireValideDeclareLIncident() throws Exception {
		when(incidentService.declarer(7L, "Porte bloquée", "alice"))
				.thenReturn(new Incident("Porte bloquée", LocalDateTime.of(2026, 10, 30, 12, 0), null, null));

		mockMvc.perform(post("/incidents").with(connecte(Profil.AGENT)).with(csrf())
						.param("equipementId", "7")
						.param("description", "Porte bloquée"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/incidents/nouveau"))
				.andExpect(flash().attribute("succes", IncidentController.SUCCES_DECLARATION));
		verify(incidentService).declarer(7L, "Porte bloquée", "alice");
	}

	@Test
	void leDetailAfficheLaDureeLePrestataireEtLeFormulaire() throws Exception {
		when(incidentService.consulter(12L, "alice")).thenReturn(detail(true, "Otis", null));

		mockMvc.perform(get("/incidents/12").with(connecte(Profil.AGENT)))
				.andExpect(status().isOk())
				.andExpect(view().name("incident/detail"))
				.andExpect(content().string(containsString("Durée d'ouverture")))
				.andExpect(content().string(containsString("47 h")))
				.andExpect(content().string(containsString("Depuis l&#39;ouverture")))
				.andExpect(content().string(containsString("Otis")))
				.andExpect(content().string(containsString("Aucune évaluation")))
				.andExpect(content().string(containsString("Enregistrer la description")))
				.andExpect(content().string(not(containsString("Déclarer l'incident résolu"))));
	}

	@Test
	void leDetailSansDroitMasqueLeFormulaire() throws Exception {
		when(incidentService.consulter(12L, "alice")).thenReturn(detail(false, null, 4));

		mockMvc.perform(get("/incidents/12").with(connecte(Profil.AGENT)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("4 / 5")))
				.andExpect(content().string(containsString("Aucun prestataire")))
				.andExpect(content().string(not(containsString("Enregistrer la description"))))
				.andExpect(content().string(containsString("Seul le responsable")))
				.andExpect(content().string(not(containsString("Déclarer l'incident résolu"))));
	}

	@Test
	void unIncidentInconnuAfficheLaPage404() throws Exception {
		when(incidentService.consulter(12L, "alice"))
				.thenThrow(new RessourceIntrouvableException("Incident 12 introuvable."));

		mockMvc.perform(get("/incidents/12").with(connecte(Profil.AGENT)))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));
	}

	@Test
	void uneDescriptionVideEstReafficheeSiLaModificationEstAutorisee() throws Exception {
		when(incidentService.consulter(12L, "alice")).thenReturn(detail(true, null, null));

		mockMvc.perform(post("/incidents/12/description").with(connecte(Profil.AGENT)).with(csrf())
						.param("description", "   "))
				.andExpect(status().isOk())
				.andExpect(view().name("incident/detail"))
				.andExpect(content().string(containsString(Incident.DESCRIPTION_OBLIGATOIRE)));
		verify(incidentService).controlerDroitModification(12L, "alice");
		verify(incidentService, never()).modifierDescription(any(), any(), any());
	}

	@Test
	void uneDescriptionVideEstRefuseeQuandLaModificationEstInterdite() throws Exception {
		doThrow(new AuthorizationDeniedException("refus")).when(incidentService).controlerDroitModification(12L, "alice");

		mockMvc.perform(post("/incidents/12/description").with(connecte(Profil.AGENT)).with(csrf())
						.param("description", "   "))
				.andExpect(status().isForbidden())
				.andExpect(content().string(containsString("Accès refusé")));
		verify(incidentService, never()).modifierDescription(any(), any(), any());
	}

	@Test
	void uneDescriptionValideEstEnregistree() throws Exception {
		mockMvc.perform(post("/incidents/12/description").with(connecte(Profil.RESPONSABLE)).with(csrf())
						.param("description", "Cabine arrêtée"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/incidents/12"))
				.andExpect(flash().attribute("succes", IncidentController.SUCCES_DESCRIPTION));
		verify(incidentService).modifierDescription(12L, "Cabine arrêtée", "alice");
	}

	@Test
	void leResponsableVoitLAffectationEtLaResolution() throws Exception {
		when(incidentService.consulter(12L, "alice")).thenReturn(detail(true, null, null, true));

		mockMvc.perform(get("/incidents/12").with(connecte(Profil.RESPONSABLE)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Choisir un prestataire")))
				.andExpect(content().string(containsString("Otis")))
				.andExpect(content().string(containsString("Affecter le prestataire")))
				.andExpect(content().string(containsString("Déclarer l'incident résolu")))
				.andExpect(content().string(containsString("repasse en service")));
	}

	@Test
	void unPrestataireAbsentEstReaffichePourLeResponsable() throws Exception {
		when(incidentService.consulter(12L, "alice")).thenReturn(detail(true, null, null, true));

		mockMvc.perform(post("/incidents/12/prestataire").with(connecte(Profil.RESPONSABLE)).with(csrf())
						.param("prestataireId", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("incident/detail"))
				.andExpect(content().string(containsString("Choisissez un prestataire.")));
		verify(incidentService).controlerDroitPilotage();
		verify(incidentService, never()).affecter(any(), any(), any());
	}

	@Test
	void unPrestataireAbsentEstRefuseAUnAgent() throws Exception {
		doThrow(new AuthorizationDeniedException("refus")).when(incidentService).controlerDroitPilotage();

		mockMvc.perform(post("/incidents/12/prestataire").with(connecte(Profil.AGENT)).with(csrf())
						.param("prestataireId", ""))
				.andExpect(status().isForbidden())
				.andExpect(content().string(containsString("Accès refusé")));
		verify(incidentService, never()).affecter(any(), any(), any());
	}

	@Test
	void uneAffectationValideEstEnregistree() throws Exception {
		mockMvc.perform(post("/incidents/12/prestataire").with(connecte(Profil.RESPONSABLE)).with(csrf())
						.param("prestataireId", "5"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/incidents/12"))
				.andExpect(flash().attribute("succes", IncidentController.SUCCES_AFFECTATION));
		verify(incidentService).affecter(12L, 5L, "alice");
	}

	@Test
	void uneResolutionEstEnregistree() throws Exception {
		mockMvc.perform(post("/incidents/12/resolution").with(connecte(Profil.RESPONSABLE)).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/incidents/12"))
				.andExpect(flash().attribute("succes", IncidentController.SUCCES_RESOLUTION));
		verify(incidentService).resoudre(12L, "alice");
	}

	@Test
	void uneResolutionEstRefuseeAUnAgent() throws Exception {
		doThrow(new AuthorizationDeniedException("refus")).when(incidentService).resoudre(12L, "alice");

		mockMvc.perform(post("/incidents/12/resolution").with(connecte(Profil.AGENT)).with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(content().string(containsString("Accès refusé")));
	}

	private static DetailIncident detail(boolean modifiable, String prestataire, Integer note) {
		return detail(modifiable, prestataire, note, false);
	}

	private static DetailIncident detail(boolean modifiable, String prestataire, Integer note, boolean pilotage) {
		return new DetailIncident(12L, "Porte bloquée", LocalDateTime.of(2026, 10, 28, 13, 0), null,
				StatutIncident.NON_AFFECTE, "47 h", "Depuis l'ouverture", NiveauAttention.VERT, 3L, "ASC-001",
				"Ascenseur quai A", "Châtelet", "Alice", prestataire, prestataire == null ? null : "otis@example.org",
				prestataire == null ? null : "0140000001", note, note == null ? null : "Rapide", null, modifiable,
				modifiable ? "Vous pouvez corriger la description." : null, pilotage,
				pilotage ? null : "Seul le responsable peut affecter un prestataire ou résoudre l'incident.",
				pilotage ? "Affecter le prestataire" : null,
				pilotage ? List.of(new PrestataireChoix(5L, "Otis")) : List.of(), List.of(),
				prestataire == null ? null : 5L,
				prestataire == null ? null : 4.5);
	}
}
