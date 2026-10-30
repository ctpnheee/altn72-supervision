
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
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.service.EquipementService;

@WebMvcTest(EquipementController.class)
@Import(SecurityConfig.class)
class EquipementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipementService service;

    private static RequestPostProcessor connecte(Profil profil) {
        return user(new UtilisateurConnecte(
                new Utilisateur(
                        "test",
                        "hache",
                        "Alice",
                        profil
                )
        ));
    }

    @Test
    void responsableVoitLesStations() throws Exception {
        when(service.listerStations()).thenReturn(List.of(
                new EquipementService.StationVue(
                        1L,
                        "Châtelet"
                )
        ));

        mockMvc.perform(get("/stations")
                        .with(connecte(Profil.RESPONSABLE)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Châtelet")
                ));
    }

    @Test
    void agentVoitLesStations() throws Exception {
        when(service.listerStations()).thenReturn(List.of(
                new EquipementService.StationVue(
                        1L,
                        "République"
                )
        ));

        mockMvc.perform(get("/stations")
                        .with(connecte(Profil.AGENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("République")
                ));
    }

    @Test
    void lesDeuxProfilsPeuventConsulterLesEquipements()
            throws Exception {

        var equipement = new EquipementService.EquipementVue(
                10L,
                "ASC-001",
                "Ascenseur quai A",
                TypeEquipement.ASCENSEUR,
                EtatEquipement.EN_SERVICE
        );

        when(service.equipementsParStation(1L))
                .thenReturn(
                        new EquipementService.StationDetailVue(
                                1L,
                                "Châtelet",
                                List.of(equipement)
                        )
                );

        for (Profil profil : List.of(
                Profil.AGENT,
                Profil.RESPONSABLE
        )) {

            mockMvc.perform(get("/stations/1/equipements")
                            .with(connecte(profil)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("ASC-001")
                    ));
        }
    }

    @Test
    void lesDeuxProfilsPeuventVoirLaFicheEquipement()
            throws Exception {

        var incident = new EquipementService.IncidentVue(
                20L,
                "Ascenseur bloqué",
                LocalDateTime.of(2026, 10, 30, 12, 0),
                StatutIncident.NON_AFFECTE
        );

        when(service.ficheEquipement(10L))
                .thenReturn(
                        new EquipementService.FicheEquipementVue(
                                10L,
                                "ASC-001",
                                "Ascenseur quai A",
                                TypeEquipement.ASCENSEUR,
                                EtatEquipement.EN_PANNE,
                                1L,
                                "Châtelet",
                                List.of(incident)
                        )
                );

        for (Profil profil : List.of(
                Profil.AGENT,
                Profil.RESPONSABLE
        )) {

            mockMvc.perform(get("/equipements/10")
                            .with(connecte(profil)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            containsString("Ascenseur bloqué")
                    ))
                    .andExpect(content().string(
                            containsString("/incidents/20")
                    ));
        }
    }

    @Test
    void equipementInexistantRetourne404() throws Exception {
        when(service.ficheEquipement(999L))
                .thenThrow(
                        new RessourceIntrouvableException(
                                "L'équipement demandé n'existe pas."
                        )
                );

        mockMvc.perform(get("/equipements/999")
                        .with(connecte(Profil.AGENT)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(
                        containsString("Page introuvable")
                ));
    }

    @Test
    void visiteurNonConnecteEstRedirigeVersLogin()
            throws Exception {

        mockMvc.perform(get("/stations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
