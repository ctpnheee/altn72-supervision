
package fr.efrei.altn72.supervision.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.exception.EvaluationDejaSaisieException;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.exception.TransitionInterditeException;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;

/**
 * Evaluation d'une intervention (F10, RG8).
 */
@Service
public class EvaluationService {

    private final Clock clock;
    private final IncidentRepository incidentRepository;
    private final EvaluationRepository evaluationRepository;

    public EvaluationService(
            Clock clock,
            IncidentRepository incidentRepository,
            EvaluationRepository evaluationRepository) {
        this.clock = clock;
        this.incidentRepository = incidentRepository;
        this.evaluationRepository = evaluationRepository;
    }

    @Transactional
    @PreAuthorize("hasRole('RESPONSABLE')")
    public Evaluation evaluer(
            Long idIncident,
            int note,
            String commentaire) {

        Incident incident = incidentRepository.findById(idIncident)
                .orElseThrow(() -> RessourceIntrouvableException.pour("Incident", idIncident));

        if (incident.getStatut() != StatutIncident.RESOLU) {
            throw new TransitionInterditeException(
                    "Seul un incident résolu peut être évalué.");
        }

        if (incident.getPrestataire() == null) {
            throw new TransitionInterditeException(
                    "Cet incident n'a pas de prestataire à évaluer.");
        }

        if (evaluationRepository.existsByIncidentId(idIncident)) {
            throw new EvaluationDejaSaisieException(
                    "Cet incident possède déjà une évaluation.");
        }

        if (note < 1 || note > 5) {
            throw new IllegalArgumentException(
                    "La note doit être comprise entre 1 et 5.");
        }

        String commentaireNettoye = commentaire == null
                ? null
                : commentaire.trim();

        if (commentaireNettoye != null
                && commentaireNettoye.length() > 1000) {
            throw new IllegalArgumentException(
                    "Le commentaire ne doit pas dépasser 1000 caractères.");
        }

        LocalDateTime maintenant = LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone());

        Evaluation evaluation = new Evaluation(
                incident,
                note,
                commentaireNettoye,
                maintenant);

        return evaluationRepository.saveAndFlush(evaluation);
    }
}
