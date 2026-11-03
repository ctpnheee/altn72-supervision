
package fr.efrei.altn72.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.exception.EvaluationDejaSaisieException;
import fr.efrei.altn72.supervision.exception.TransitionInterditeException;
import fr.efrei.altn72.supervision.model.Equipement;
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
@WithMockUser(username = "responsable", roles = "RESPONSABLE")
class EvaluationServiceTest {

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private EquipementRepository equipementRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PrestataireRepository prestataireRepository;

    @Autowired
    private Clock clock;

    private Incident incident;
    private Prestataire prestataire;

    @BeforeEach
    void preparer() {
        Station station = stationRepository.save(
                new Station("Station B7"));

        Equipement equipement = equipementRepository.save(
                new Equipement(
                        "B7-ASC-001",
                        "Ascenseur test",
                        TypeEquipement.ASCENSEUR,
                        station));

        Utilisateur declarant = utilisateurRepository.save(
                new Utilisateur(
                        "b7-agent",
                        "hachage",
                        "Bruno",
                        Profil.AGENT));

        prestataire = prestataireRepository.save(
                new Prestataire(
                        "Otis B7",
                        "otis-b7@example.org",
                        "0102030405"));

        LocalDateTime ouverture = LocalDateTime.ofInstant(
                clock.instant(), clock.getZone()).minusHours(5);

        incident = incidentRepository.save(
                new Incident(
                        "Ascenseur bloqué",
                        ouverture,
                        equipement,
                        declarant));
    }

    @Test
    void incidentNonResoluEstRefuse() {
        incident.setPrestataire(prestataire);
        incident.setStatut(StatutIncident.AFFECTE);

        assertThatThrownBy(() ->
                evaluationService.evaluer(
                        incident.getId(), 4, "Test"))
                .isInstanceOf(TransitionInterditeException.class);

        assertThat(evaluationRepository
                .existsByIncidentId(incident.getId()))
                .isFalse();
    }

    @Test
    void incidentSansPrestataireEstRefuse() {
        incident.setStatut(StatutIncident.RESOLU);

        assertThatThrownBy(() ->
                evaluationService.evaluer(
                        incident.getId(), 4, "Test"))
                .isInstanceOf(TransitionInterditeException.class);
    }

    @Test
    void evaluationValideEstEnregistree() {
        rendreEligible();

        var evaluation = evaluationService.evaluer(
                incident.getId(),
                5,
                "Très bonne intervention");

        assertThat(evaluation.getNote()).isEqualTo(5);
        assertThat(evaluation.getCommentaire())
                .isEqualTo("Très bonne intervention");
        assertThat(evaluation.getDate()).isNotNull();

        assertThat(evaluationRepository
                .existsByIncidentId(incident.getId()))
                .isTrue();
    }

    @Test
    void secondeEvaluationEstRefusee() {
        rendreEligible();

        evaluationService.evaluer(
                incident.getId(), 5, "Première note");

        assertThatThrownBy(() ->
                evaluationService.evaluer(
                        incident.getId(), 3, "Deuxième note"))
                .isInstanceOf(EvaluationDejaSaisieException.class);
    }

    @Test
    void noteHorsBornesEstRefusee() {
        rendreEligible();

        assertThatThrownBy(() ->
                evaluationService.evaluer(
                        incident.getId(), 0, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                evaluationService.evaluer(
                        incident.getId(), 6, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void rendreEligible() {
        incident.setPrestataire(prestataire);
        incident.setStatut(StatutIncident.RESOLU);
        incident.setDateResolution(
                LocalDateTime.ofInstant(
                        clock.instant(), clock.getZone()));
        incidentRepository.saveAndFlush(incident);
    }
}
