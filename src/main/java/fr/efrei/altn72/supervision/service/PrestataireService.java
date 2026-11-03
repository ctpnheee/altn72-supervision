
package fr.efrei.altn72.supervision.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;

@Service
public class PrestataireService {

    private final PrestataireRepository prestataireRepository;
    private final IncidentRepository incidentRepository;
    private final EvaluationRepository evaluationRepository;

    public PrestataireService(
            PrestataireRepository prestataireRepository,
            IncidentRepository incidentRepository,
            EvaluationRepository evaluationRepository) {

        this.prestataireRepository = prestataireRepository;
        this.incidentRepository = incidentRepository;
        this.evaluationRepository = evaluationRepository;
    }

    public record PrestataireResume(
            Long id,
            String nom,
            String email,
            String telephone,
            Double noteMoyenne,
            int nombreInterventions) {
    }

    public record InterventionVue(
            Long id,
            String description,
            String referenceEquipement,
            String libelleEquipement,
            String nomStation,
            StatutIncident statut,
            LocalDateTime dateOuverture,
            LocalDateTime dateResolution) {
    }

    public record FichePrestataire(
            Long id,
            String nom,
            String email,
            String telephone,
            Double noteMoyenne,
            int nombreInterventions,
            List<InterventionVue> interventions) {
    }

    @Transactional(readOnly = true)
    public List<PrestataireResume> listerPrestataires() {

        return prestataireRepository.findAllByOrderByNomAsc()
                .stream()
                .map(this::versResume)
                .toList();
    }

    @Transactional(readOnly = true)
    public FichePrestataire consulter(Long id) {

        Prestataire prestataire = prestataireRepository.findById(id)
                .orElseThrow(() ->
                        new RessourceIntrouvableException(
                                "Le prestataire " + id + " n'existe pas."));

        List<InterventionVue> interventions = incidentRepository
                .findInterventionsParPrestataire(id)
                .stream()
                .map(this::versIntervention)
                .toList();

        Double moyenne = evaluationRepository
                .noteMoyenneDuPrestataire(id);

        return new FichePrestataire(
                prestataire.getId(),
                prestataire.getNom(),
                prestataire.getEmail(),
                prestataire.getTelephone(),
                moyenne,
                interventions.size(),
                interventions);
    }

    private PrestataireResume versResume(Prestataire prestataire) {

        Long id = prestataire.getId();

        int total = incidentRepository
                .findInterventionsParPrestataire(id)
                .size();

        Double moyenne = evaluationRepository
                .noteMoyenneDuPrestataire(id);

        return new PrestataireResume(
                id,
                prestataire.getNom(),
                prestataire.getEmail(),
                prestataire.getTelephone(),
                moyenne,
                total);
    }

    private InterventionVue versIntervention(Incident incident) {

        return new InterventionVue(
                incident.getId(),
                incident.getDescription(),
                incident.getEquipement().getReference(),
                incident.getEquipement().getLibelle(),
                incident.getEquipement().getStation().getNom(),
                incident.getStatut(),
                incident.getDateOuverture(),
                incident.getDateResolution());
    }
}
