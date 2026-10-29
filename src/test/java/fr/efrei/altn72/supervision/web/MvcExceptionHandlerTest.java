package fr.efrei.altn72.supervision.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.exception.EvaluationDejaSaisieException;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.exception.TransitionInterditeException;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;

/** Vérifie la traduction de chaque exception avec un contrôleur factice qui les lève. */
@WebMvcTest(MvcExceptionHandlerTest.ControleurFactice.class)
@Import({ MvcExceptionHandler.class, MvcExceptionHandlerTest.ControleurFactice.class })
class MvcExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	private static RequestPostProcessor connecte() {
		return user(new UtilisateurConnecte(new Utilisateur("alice", "hache", "Alice", Profil.AGENT)));
	}

	@Test
	void uneRessourceIntrouvableAfficheLaPage404() throws Exception {
		mockMvc.perform(get("/test/introuvable").with(connecte()))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"))
				.andExpect(content().string(containsString("Page introuvable")));
	}

	@Test
	void uneTransitionInterditeRevientSurLaPageDOrigineAvecLeMessage() throws Exception {
		mockMvc.perform(post("/test/transition").with(connecte()).with(csrf())
						.header(HttpHeaders.REFERER, "http://localhost/incidents/12?tri=date"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/incidents/12"))
				.andExpect(flash().attribute("erreur", "Un incident résolu ne peut plus être réaffecté."));
	}

	@Test
	void uneEvaluationDejaSaisieRevientSurLaPageDOrigineAvecLeMessage() throws Exception {
		mockMvc.perform(post("/test/evaluation").with(connecte()).with(csrf())
						.header(HttpHeaders.REFERER, "http://localhost/incidents/12"))
				.andExpect(redirectedUrl("/incidents/12"))
				.andExpect(flash().attribute("erreur", "Cet incident a déjà été évalué."));
	}

	@Test
	void sansPageDOrigineLaRedirectionVaALAccueil() throws Exception {
		mockMvc.perform(post("/test/transition").with(connecte()).with(csrf()))
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void uneAdresseDOrigineExterneNEstJamaisUneCibleDeRedirection() throws Exception {
		for (String referent : new String[] { "http://localhost//site-malveillant.example",
				"https://site-malveillant.example/incidents/12", "//site-malveillant.example/x" }) {
			mockMvc.perform(post("/test/transition").with(connecte()).with(csrf())
							.header(HttpHeaders.REFERER, referent))
					.andExpect(status().is3xxRedirection())
					.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
							.startsWith("/")
							.doesNotStartWith("//")
							.doesNotContain("site-malveillant.example/")
							.doesNotContain("://"));
		}
	}

	@Controller
	static class ControleurFactice {

		@GetMapping("/test/introuvable")
		void introuvable() {
			throw new RessourceIntrouvableException("Incident 42 introuvable.");
		}

		@PostMapping("/test/transition")
		void transition() {
			throw new TransitionInterditeException("Un incident résolu ne peut plus être réaffecté.");
		}

		@PostMapping("/test/evaluation")
		void evaluation() {
			throw new EvaluationDejaSaisieException("Cet incident a déjà été évalué.");
		}
	}
}
