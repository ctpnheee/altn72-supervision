
package fr.efrei.altn72.supervision.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;

/**
 * Consultation de l'historique des incidents résolus (F9).
 *
 * RG6 : les incidents résolus restent consultables.
 * RG7 : tri par date de résolution décroissante.
 */
@Service
public class HistoriqueService {

    private static final int TAILLE_PAGE = 10;

    private final IncidentRepository incidentRepository;
    private final EvaluationRepository evaluationRepository;

    public HistoriqueService(
            IncidentRepository incidentRepository,
            EvaluationRepository evaluationRepository) {
        this.incidentRepository = incidentRepository;
        this.evaluationRepository = evaluationRepository;
    }

    /**
     * Données nécessaires à une ligne du tableau HTML.
     */
    public record LigneHistorique(
            Long id,
            String description,
            String equipementReference,
            String equipementLibelle,
            String stationNom,
            String prestataireNom,
            LocalDateTime dateOuverture,
            LocalDateTime dateResolution,
            String dureeResolution,
            Integer note) {
    }

    /**
     * Retourne une page d'incidents résolus.
     * Les entités sont converties avant de quitter
     * la transaction, pour ne pas exposer JPA à la vue.
     */
    @Transactional(readOnly = true)
    public Page<LigneHistorique> consulterHistorique(int numeroPage) {

        int page = Math.max(0, numeroPage);

        Page<Incident> incidents = incidentRepository
                .findHistoriqueResolu(
                        StatutIncident.RESOLU,
                        PageRequest.of(page, TAILLE_PAGE));

        List<Long> ids = incidents.getContent().stream()
                .map(Incident::getId)
                .toList();

        Map<Long, Evaluation> evaluations = chargerEvaluations(ids);

        return incidents.map(incident ->
                versLigne(
                        incident,
                        evaluations.get(incident.getId())));
    }

    /**
     * Charge toutes les évaluations de la page
     * en une seule requête.
     */
    private Map<Long, Evaluation> chargerEvaluations(List<Long> ids) {

        if (ids.isEmpty()) {
            return Map.of();
        }

        return evaluationRepository.findByIncidentIdIn(ids)
                .stream()
                .collect(Collectors.toMap(
                        evaluation -> evaluation
                                .getIncident()
                                .getId(),
                        Function.identity()));
    }

    private LigneHistorique versLigne(
            Incident incident,
            Evaluation evaluation) {

        Equipement equipement = incident.getEquipement();
        Prestataire prestataire = incident.getPrestataire();

        return new LigneHistorique(
                incident.getId(),
                incident.getDescription(),
                equipement.getReference(),
                equipement.getLibelle(),
                equipement.getStation().getNom(),
                prestataire == null
                        ? null
                        : prestataire.getNom(),
                incident.getDateOuverture(),
                incident.getDateResolution(),
                calculerDuree(
                        incident.getDateOuverture(),
                        incident.getDateResolution()),
                evaluation == null
                        ? null
                        : evaluation.getNote());
    }

    /**
     * Durée entre l'ouverture et la résolution.
     *
     * Aucun Clock n'est nécessaire ici :
     * les deux dates sont déjà enregistrées.
     */
    private String calculerDuree(
            LocalDateTime ouverture,
            LocalDateTime resolution) {

        Duration duree = Duration.between(ouverture, resolution);

        long heures = duree.toHours();
        int minutes = duree.toMinutesPart();

        if (minutes == 0) {
            return heures + " h";
        }

        return heures + " h " + minutes + " min";
    }
}
