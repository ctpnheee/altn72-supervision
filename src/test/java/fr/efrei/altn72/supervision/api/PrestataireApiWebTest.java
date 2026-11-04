
package fr.efrei.altn72.supervision.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import fr.efrei.altn72.supervision.config.SecurityConfig;
import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.service.PrestataireService;
import fr.efrei.altn72.supervision.service.PrestataireService.PrestataireResume;

@WebMvcTest(PrestataireApiController.class)
@Import(SecurityConfig.class)
class PrestataireApiWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrestataireService prestataireService;

    private RequestPostProcessor connecte(Profil profil) {
        return user(new UtilisateurConnecte(
                new Utilisateur(
                        "test",
                        "hachage",
                        "Alice",
                        profil)));
    }

    @Test
    void responsableObtientLaListeJson() throws Exception {

        when(prestataireService.listerPrestataires())
                .thenReturn(List.of(
                        new PrestataireResume(
                                1L,
                                "Otis",
                                "otis@example.org",
                                "0102030405",
                                4.5,
                                3)));

        mockMvc.perform(get("/api/prestataires")
                        .with(connecte(Profil.RESPONSABLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nom").value("Otis"))
                .andExpect(jsonPath("$[0].noteMoyenne").value(4.5));
    }

    @Test
    void agentNePeutPasChargerLaListe() throws Exception {

        mockMvc.perform(get("/api/prestataires")
                        .with(connecte(Profil.AGENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void visiteurNonConnecteObtient401() throws Exception {

        mockMvc.perform(get("/api/prestataires"))
                .andExpect(status().isUnauthorized());
    }
}
