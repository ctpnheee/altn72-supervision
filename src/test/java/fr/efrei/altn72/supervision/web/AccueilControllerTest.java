package fr.efrei.altn72.supervision.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccueilController.class)
class AccueilControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser
	void laPageDAccueilAfficheLeLayout() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(view().name("accueil"))
				.andExpect(content().string(containsString("<title>Accueil · Supervision</title>")))
				.andExpect(content().string(containsString("/css/app.css")))
				.andExpect(content().string(containsString("class=\"entete\"")))
				.andExpect(content().string(containsString("class=\"menu\"")))
				.andExpect(content().string(containsString("class=\"pied\"")))
				.andExpect(content().string(containsString("Déconnectez-vous")));
	}

	@Test
	@WithMockUser(roles = "RESPONSABLE")
	void leResponsableVoitLeTableauDeBordDansLeMenu() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(content().string(containsString("Tableau de bord")))
				.andExpect(content().string(containsString("Historique")));
	}

	@Test
	@WithMockUser(roles = "AGENT")
	void unAgentNeVoitPasLeTableauDeBordDansLeMenu() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(content().string(not(containsString("Tableau de bord"))))
				.andExpect(content().string(containsString("Historique")));
	}

	@Test
	@WithMockUser
	void lesMessagesFlashSontAffiches() throws Exception {
		mockMvc.perform(get("/").flashAttr("succes", "Incident déclaré").flashAttr("erreur", "Action impossible"))
				.andExpect(content().string(containsString("Incident déclaré")))
				.andExpect(content().string(containsString("Action impossible")));
	}
}
