
package fr.efrei.altn72.supervision.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import fr.efrei.altn72.supervision.service.TableauDeBordService;

@WebMvcTest(TableauDeBordController.class)
@Import(SecurityConfig.class)
class TableauDeBordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TableauDeBordService service;

    private static RequestPostProcessor connecte(Profil profil) {
        return user(new UtilisateurConnecte(
                new Utilisateur(
                        "test",
                        "hachage",
                        "Alice",
                        profil)));
    }

    private TableauDeBordService.TableauDeBord tableauVide() {
        return new TableauDeBordService.TableauDeBord(
                List.of(),
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                List.of());
    }

    @Test
    void responsablePeutConsulterLeTableau() throws Exception {

        when(service.consulter(null, null, null))
                .thenReturn(tableauVide());

        mockMvc.perform(get("/tableau-de-bord")
                        .with(connecte(Profil.RESPONSABLE)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(
                                "Tableau de bord de supervision")))
                .andExpect(content().string(
                        containsString("Incidents actifs")));
    }

    @Test
    void agentRecoitErreur403() throws Exception {

        mockMvc.perform(get("/tableau-de-bord")
                        .with(connecte(Profil.AGENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void visiteurEstRedirigeVersConnexion() throws Exception {

        mockMvc.perform(get("/tableau-de-bord"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
