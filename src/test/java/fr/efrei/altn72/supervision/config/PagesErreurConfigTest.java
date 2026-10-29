package fr.efrei.altn72.supervision.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import jakarta.servlet.RequestDispatcher;

/**
 * MockMvc ne redirige pas vers {@code /error} : le test simule cette redirection interne en posant
 * le code d'erreur, comme le fait le conteneur de servlets.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PagesErreurConfigTest {

	@Autowired
	private MockMvc mockMvc;

	private ResultActions pageDErreur(int code) throws Exception {
		return mockMvc.perform(get("/error")
				.accept(MediaType.TEXT_HTML)
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, code)
				.with(user(new UtilisateurConnecte(new Utilisateur("bruno", "hache", "Bruno", Profil.AGENT)))));
	}

	@Test
	void uneUrlInconnueAfficheLaPage404EnFrancais() throws Exception {
		pageDErreur(404)
				.andExpect(status().isNotFound())
				.andExpect(content().string(containsString("Page introuvable")))
				.andExpect(content().string(containsString("Bruno")));
	}

	@Test
	void unAccesRefuseAfficheLaPage403EnFrancais() throws Exception {
		pageDErreur(403)
				.andExpect(status().isForbidden())
				.andExpect(content().string(containsString("Accès refusé")));
	}

	@Test
	void uneErreurInterneAfficheLaPageGeneriqueSansDetailTechnique() throws Exception {
		pageDErreur(500)
				.andExpect(status().isInternalServerError())
				.andExpect(content().string(containsString("Une erreur est survenue")))
				.andExpect(content().string(not(containsString("Exception"))))
				.andExpect(content().string(not(containsString("trace"))));
	}

	@Test
	void aucunePageWhitelabelNEstAffichee() throws Exception {
		for (int code : new int[] { 400, 404, 403, 405, 500 }) {
			pageDErreur(code).andExpect(content().string(not(containsString("Whitelabel"))));
		}
	}

	@Test
	void laPageDErreurEstAccessibleSansConnexion() throws Exception {
		mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
						.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
				.andExpect(status().isNotFound())
				.andExpect(content().string(containsString("Page introuvable")));
	}
}
