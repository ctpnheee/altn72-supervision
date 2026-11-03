
package fr.efrei.altn72.supervision.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EvaluationWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock clock;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private EquipementRepository equipementRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private PrestataireRepository prestataireRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private Incident incident;
    private Utilisateur responsable;
    private Utilisateur agent;

    @BeforeEach
    void preparer() {
        Station station = stationRepository.save(
                new Station("Station Evaluation"));

        Equipement equipement = equipementRepository.save(
                new Equipement(
                        "B7-WEB-001",
                        "Ascenseur test",
                        TypeEquipement.ASCENSEUR,
                        station));

        responsable = utilisateurRepository.save(
                new Utilisateur(
                        "b7-responsable",
                        "hachage",
                        "Alice",
                        Profil.RESPONSABLE));

        agent = utilisateurRepository.save(
                new Utilisateur(
                        "b7-agent-web",
                        "hachage",
                        "Bruno",
                        Profil.AGENT));

        Prestataire prestataire = prestataireRepository.save(
                new Prestataire(
                        "Otis Evaluation",
                        "otis-web@example.org",
                        "0100000001"));

        LocalDateTime maintenant = LocalDateTime.ofInstant(
                clock.instant(), clock.getZone());

        incident = new Incident(
                "Ascenseur réparé",
                maintenant.minusHours(5),
                equipement,
                agent);

        incident.setPrestataire(prestataire);
        incident.setStatut(StatutIncident.RESOLU);
        incident.setDateResolution(maintenant.minusHours(1));

        incident = incidentRepository.save(incident);
    }

    @Test
    void responsableVoitLeFormulaire() throws Exception {
        mockMvc.perform(get("/incidents/" + incident.getId())
                        .with(user(new UtilisateurConnecte(responsable))))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Évaluer l'intervention")));
    }

    @Test
    void agentNeVoitPasLeFormulaire() throws Exception {
        mockMvc.perform(get("/incidents/" + incident.getId())
                        .with(user(new UtilisateurConnecte(agent))))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        not(containsString("Enregistrer l'évaluation"))));
    }

    @Test
    void agentNePeutPasEnvoyerUneEvaluation() throws Exception {
        mockMvc.perform(post("/incidents/" + incident.getId() + "/evaluation")
                        .with(user(new UtilisateurConnecte(agent)))
                        .with(csrf())
                        .param("note", "5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noteZeroEstRefusee() throws Exception {
        verifierNoteInvalide("0");
    }

    @Test
    void noteSixEstRefusee() throws Exception {
        verifierNoteInvalide("6");
    }

    @Test
    void evaluationValideEstEnregistree() throws Exception {
        mockMvc.perform(post("/incidents/" + incident.getId() + "/evaluation")
                        .with(user(new UtilisateurConnecte(responsable)))
                        .with(csrf())
                        .param("note", "5")
                        .param("commentaire", "Très bon travail"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/incidents/" + incident.getId()));

        mockMvc.perform(get("/incidents/" + incident.getId())
                        .with(user(new UtilisateurConnecte(responsable))))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Très bon travail")))
                .andExpect(content().string(
                        containsString("5 / 5")));
    }

    private void verifierNoteInvalide(String note) throws Exception {
        mockMvc.perform(post("/incidents/" + incident.getId() + "/evaluation")
                        .with(user(new UtilisateurConnecte(responsable)))
                        .with(csrf())
                        .param("note", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/incidents/" + incident.getId()));
    }
}
