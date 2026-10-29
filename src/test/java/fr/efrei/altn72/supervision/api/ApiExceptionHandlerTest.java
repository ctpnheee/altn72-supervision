package fr.efrei.altn72.supervision.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import fr.efrei.altn72.supervision.exception.EvaluationDejaSaisieException;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.exception.TransitionInterditeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Vérifie la traduction de chaque exception avec un contrôleur factice qui les lève. */
@WebMvcTest(ApiExceptionHandlerTest.ControleurFactice.class)
@Import({ ApiExceptionHandler.class, ApiExceptionHandlerTest.ControleurFactice.class })
@WithMockUser
class ApiExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void uneRessourceIntrouvableDonne404() throws Exception {
		mockMvc.perform(get("/test/introuvable"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Incident 42 introuvable."));
	}

	@Test
	void uneTransitionInterditeDonne409() throws Exception {
		mockMvc.perform(get("/test/transition"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Un incident résolu ne peut plus être réaffecté."));
	}

	@Test
	void uneEvaluationDejaSaisieDonne409() throws Exception {
		mockMvc.perform(get("/test/evaluation"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Cet incident a déjà été évalué."));
	}

	@Test
	void uneDonneeInvalideDonne400AvecLeChampEnCause() throws Exception {
		mockMvc.perform(post("/test/valider").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"description\": \"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erreurs.description").exists());
	}

	@Test
	void aucuneTraceTechniqueNEstRenvoyee() throws Exception {
		mockMvc.perform(get("/test/introuvable"))
				.andExpect(content().string(not(containsString("Exception"))))
				.andExpect(content().string(not(containsString("fr.efrei"))))
				.andExpect(content().string(not(containsString("stackTrace"))));
	}

	@RestController
	static class ControleurFactice {

		record Saisie(@NotBlank String description) {
		}

		@GetMapping("/test/introuvable")
		void introuvable() {
			throw new RessourceIntrouvableException("Incident 42 introuvable.");
		}

		@GetMapping("/test/transition")
		void transition() {
			throw new TransitionInterditeException("Un incident résolu ne peut plus être réaffecté.");
		}

		@GetMapping("/test/evaluation")
		void evaluation() {
			throw new EvaluationDejaSaisieException("Cet incident a déjà été évalué.");
		}

		@PostMapping("/test/valider")
		void valider(@Valid @RequestBody Saisie saisie) {
		}
	}
}
