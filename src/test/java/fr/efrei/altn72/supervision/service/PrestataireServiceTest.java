
package fr.efrei.altn72.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

@SpringBootTest
@Transactional
class PrestataireServiceTest {

    private static final LocalDateTime DATE =
            LocalDateTime.of(2026, 11, 1, 12, 0);

    @Autowired
    private PrestataireService service;

    @Autowired
    private PrestataireRepository prestataireRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private EquipementRepository equipementRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private Prestataire prestataire;
    private Equipement equipement;
    private Utilisateur utilisateur;

    @BeforeEach
    void preparer() {
        Station station = stationRepository.save(
                new Station("Station B8"));

        equipement = equipementRepository.save(
                new Equipement(
                        "B8-ASC-001",
                        "Ascenseur B8",
                        TypeEquipement.ASCENSEUR,
                        station));

        utilisateur = utilisateurRepository.save(
                new Utilisateur(
                        "agent-b8",
                        "hachage",
                        "Alice",
                        Profil.AGENT));

        prestataire = prestataireRepository.save(
                new Prestataire(
                        "Otis B8",
                        "otis-b8@example.org",
                        "0102030405"));
    }

    private Incident creerIncident(String description) {
        Incident incident = new Incident(
                description,
                DATE.minusDays(2),
                equipement,
                utilisateur);

        incident.setPrestataire(prestataire);
        incident.setStatut(StatutIncident.RESOLU);
        incident.setDateResolution(DATE);

        return incidentRepository.save(incident);
    }

    @Test
    void prestataireSansEvaluationAfficheUneMoyenneNulle() {
        var fiche = service.consulter(prestataire.getId());

        assertThat(fiche.noteMoyenne()).isNull();
        assertThat(fiche.nombreInterventions()).isZero();
        assertThat(fiche.interventions()).isEmpty();
    }

    @Test
    void moyenneEtNombreInterventionsSontCorrects() {
        Incident premier = creerIncident("Première intervention");
        Incident second = creerIncident("Deuxième intervention");

        evaluationRepository.save(
                new Evaluation(premier, 4, "Bien", DATE));

        evaluationRepository.save(
                new Evaluation(second, 5, "Très bien", DATE));

        evaluationRepository.flush();

        var fiche = service.consulter(prestataire.getId());

        assertThat(fiche.noteMoyenne()).isEqualTo(4.5);
        assertThat(fiche.nombreInterventions()).isEqualTo(2);
        assertThat(fiche.interventions()).hasSize(2);
    }

    @Test
    void laListeContientLePrestataireEtSaMoyenne() {
        Incident incident = creerIncident("Intervention évaluée");

        evaluationRepository.saveAndFlush(
                new Evaluation(incident, 5, null, DATE));

        var liste = service.listerPrestataires();

        assertThat(liste).hasSize(1);
        assertThat(liste.get(0).nom()).isEqualTo("Otis B8");
        assertThat(liste.get(0).noteMoyenne()).isEqualTo(5.0);
        assertThat(liste.get(0).nombreInterventions()).isEqualTo(1);
    }
}
