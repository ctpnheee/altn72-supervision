package fr.efrei.altn72.supervision.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;

@WebMvcTest(AccueilController.class)
class AccueilControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private static RequestPostProcessor connecte(Profil profil) {
		return user(new UtilisateurConnecte(new Utilisateur("alice", "hache", "Alice", profil)));
	}

	@Test
	void laPageDAccueilAfficheLeLayout() throws Exception {
		mockMvc.perform(get("/").with(connecte(Profil.RESPONSABLE)))
				.andExpect(status().isOk())
				.andExpect(view().name("accueil"))
				.andExpect(content().string(containsString("<title>Accueil · Supervision</title>")))
				.andExpect(content().string(containsString("/css/app.css")))
				.andExpect(content().string(containsString("class=\"entete\"")))
				.andExpect(content().string(containsString("class=\"menu\"")))
				.andExpect(content().string(containsString("class=\"pied\"")))
				.andExpect(content().string(containsString("Alice")))
				.andExpect(content().string(containsString("Responsable")))
				.andExpect(content().string(containsString("Déconnectez-vous")));
	}

	@Test
	void leResponsableVoitLeTableauDeBordDansLeMenu() throws Exception {
		mockMvc.perform(get("/").with(connecte(Profil.RESPONSABLE)))
				.andExpect(content().string(containsString("Tableau de bord")))
				.andExpect(content().string(containsString("Historique")));
	}

	@Test
	void unAgentNeVoitPasLeTableauDeBordDansLeMenu() throws Exception {
		mockMvc.perform(get("/").with(connecte(Profil.AGENT)))
				.andExpect(content().string(not(containsString("Tableau de bord"))))
				.andExpect(content().string(containsString("Historique")));
	}

	@Test
	void lesMessagesFlashSontAffiches() throws Exception {
		mockMvc.perform(get("/").with(connecte(Profil.AGENT)).flashAttr("succes", "Incident déclaré").flashAttr("erreur", "Action impossible"))
				.andExpect(content().string(containsString("Incident déclaré")))
				.andExpect(content().string(containsString("Action impossible")));
	}
}
