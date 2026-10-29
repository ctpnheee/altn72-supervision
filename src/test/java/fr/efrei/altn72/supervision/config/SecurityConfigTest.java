package fr.efrei.altn72.supervision.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

	private static final String MOT_DE_PASSE = "MotDePasseDeTest1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UtilisateurRepository utilisateurRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void creerLesComptes() {
		utilisateurRepository.deleteAll();
		utilisateurRepository.save(new Utilisateur("alice", passwordEncoder.encode(MOT_DE_PASSE), "Alice", Profil.RESPONSABLE));
		utilisateurRepository.save(new Utilisateur("bruno", passwordEncoder.encode(MOT_DE_PASSE), "Bruno", Profil.AGENT));
	}

	@Test
	void unVisiteurNonConnecteEstRenvoyeVersLaConnexion() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void laPageDeConnexionEstPubliqueEtSonStyleAussi() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Se connecter")));
		mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
	}

	@Test
	void unMauvaisMotDePasseRenvoieVersLaConnexionAvecUneErreur() throws Exception {
		mockMvc.perform(formLogin().user("alice").password("incorrect"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?error"));

		mockMvc.perform(get("/login").param("error", ""))
				.andExpect(content().string(containsString("Identifiant ou mot de passe incorrect.")));
	}

	@Test
	void unLoginInconnuEstRefuse() throws Exception {
		mockMvc.perform(formLogin().user("inconnu").password(MOT_DE_PASSE))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void uneConnexionReussieAfficheLEnteteAvecLePrenomEtLeProfil() throws Exception {
		MvcResult connexion = mockMvc.perform(formLogin().user("alice").password(MOT_DE_PASSE))
				.andExpect(authenticated().withUsername("alice"))
				.andExpect(redirectedUrl("/"))
				.andReturn();

		mockMvc.perform(get("/").session((MockHttpSession) connexion.getRequest().getSession()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Alice")))
				.andExpect(content().string(containsString("Responsable")))
				.andExpect(content().string(containsString("Déconnectez-vous")));
	}

	@Test
	void laDeconnexionInvalideLaSession() throws Exception {
		MvcResult connexion = mockMvc.perform(formLogin().user("bruno").password(MOT_DE_PASSE)).andReturn();
		MockHttpSession session = (MockHttpSession) connexion.getRequest().getSession();

		mockMvc.perform(post("/logout").with(csrf()).session(session))
				.andExpect(redirectedUrl("/login?logout"));

		mockMvc.perform(get("/").session(session))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void unAgentNeVoitPasLeTableauDeBord() throws Exception {
		mockMvc.perform(get("/tableau-de-bord").with(user(new UtilisateurConnecte(
				new Utilisateur("bruno", "hache", "Bruno", Profil.AGENT)))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unResponsableAccedeAuTableauDeBordSansEtreBloque() throws Exception {
		// La page n'existe pas encore (ticket B9) : 404 prouve que la sécurité a laissé passer.
		mockMvc.perform(get("/tableau-de-bord").with(user(new UtilisateurConnecte(
				new Utilisateur("alice", "hache", "Alice", Profil.RESPONSABLE)))))
				.andExpect(status().isNotFound());
	}

	@Test
	void laDeconnexionExigeUnJetonCsrf() throws Exception {
		mockMvc.perform(post("/logout"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/logout").with(csrf()))
				.andExpect(redirectedUrl("/login?logout"));
	}
}
