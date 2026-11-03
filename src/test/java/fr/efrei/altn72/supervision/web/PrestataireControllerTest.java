
package fr.efrei.altn72.supervision.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.service.PrestataireService;

@WebMvcTest(PrestataireController.class)
@Import(SecurityConfig.class)
class PrestataireControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrestataireService service;

    private static RequestPostProcessor connecte(Profil profil) {
        return user(new UtilisateurConnecte(
                new Utilisateur(
                        "test",
                        "hachage",
                        "Alice",
                        profil)));
    }

    @Test
    void lesDeuxProfilsVoientLaListe() throws Exception {
        when(service.listerPrestataires()).thenReturn(List.of(
                new PrestataireService.PrestataireResume(
                        1L,
                        "Otis",
                        "otis@example.org",
                        "0102030405",
                        4.5,
                        2)));

        for (Profil profil : List.of(
                Profil.RESPONSABLE,
                Profil.AGENT)) {

            mockMvc.perform(get("/prestataires")
                            .with(connecte(profil)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("Otis")))
                    .andExpect(content().string(
                            containsString("4.50")));
        }
    }

    @Test
    void laFicheAfficheLesInterventions() throws Exception {

        var intervention = new PrestataireService.InterventionVue(
                10L,
                "Ascenseur réparé",
                "ASC-001",
                "Ascenseur quai A",
                "Châtelet",
                StatutIncident.RESOLU,
                LocalDateTime.of(2026, 11, 1, 8, 0),
                LocalDateTime.of(2026, 11, 2, 8, 0));

        when(service.consulter(1L)).thenReturn(
                new PrestataireService.FichePrestataire(
                        1L,
                        "Otis",
                        "otis@example.org",
                        "0102030405",
                        5.0,
                        1,
                        List.of(intervention)));

        mockMvc.perform(get("/prestataires/1")
                        .with(connecte(Profil.AGENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Ascenseur réparé")))
                .andExpect(content().string(
                        containsString("/incidents/10")));
    }

    @Test
    void aucuneNoteAffichePasEncoreEvalue() throws Exception {
        when(service.consulter(1L)).thenReturn(
                new PrestataireService.FichePrestataire(
                        1L,
                        "Prestataire non évalué",
                        "contact@example.org",
                        "0102030405",
                        null,
                        0,
                        List.of()));

        mockMvc.perform(get("/prestataires/1")
                        .with(connecte(Profil.RESPONSABLE)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Pas encore évalué")));
    }

    @Test
    void prestataireInexistantRetourne404() throws Exception {
        when(service.consulter(999L))
                .thenThrow(new RessourceIntrouvableException(
                        "Prestataire introuvable."));

        mockMvc.perform(get("/prestataires/999")
                        .with(connecte(Profil.AGENT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void visiteurNonConnecteEstRedirigeVersLogin()
            throws Exception {

        mockMvc.perform(get("/prestataires"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
