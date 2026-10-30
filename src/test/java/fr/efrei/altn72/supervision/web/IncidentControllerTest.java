package fr.efrei.altn72.supervision.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.service.IncidentService;

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
				.andExpect(content().string(containsString("Décrivez la panne.")));
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
				.andExpect(flash().attribute("succes", "Incident déclaré."));
		verify(incidentService).declarer(7L, "Porte bloquée", "alice");
	}
}
