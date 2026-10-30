
package fr.efrei.altn72.supervision.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;

@Service
public class EquipementService {

    private final StationRepository stationRepository;
    private final EquipementRepository equipementRepository;
    private final IncidentRepository incidentRepository;

    public EquipementService(
            StationRepository stationRepository,
            EquipementRepository equipementRepository,
            IncidentRepository incidentRepository) {
        this.stationRepository = stationRepository;
        this.equipementRepository = equipementRepository;
        this.incidentRepository = incidentRepository;
    }

    public record StationVue(Long id, String nom) {}

    public record EquipementVue(
            Long id,
            String reference,
            String libelle,
            TypeEquipement type,
            EtatEquipement etat) {}

    public record StationDetailVue(
            Long id,
            String nom,
            List<EquipementVue> equipements) {}

    public record IncidentVue(
            Long id,
            String description,
            LocalDateTime dateOuverture,
            StatutIncident statut) {}

    public record FicheEquipementVue(
            Long id,
            String reference,
            String libelle,
            TypeEquipement type,
            EtatEquipement etat,
            Long stationId,
            String stationNom,
            List<IncidentVue> incidents) {}

    @Transactional(readOnly = true)
    public List<StationVue> listerStations() {
        return stationRepository.findAllByOrderByNomAsc()
                .stream()
                .map(station -> new StationVue(
                        station.getId(),
                        station.getNom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StationDetailVue equipementsParStation(Long stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() ->
                        new RessourceIntrouvableException(
                                "La station demandée n'existe pas."));

        List<EquipementVue> equipements = equipementRepository
                .findByStationIdOrderByReferenceAsc(stationId)
                .stream()
                .map(this::versEquipementVue)
                .toList();

        return new StationDetailVue(
                station.getId(),
                station.getNom(),
                equipements);
    }

    @Transactional(readOnly = true)
    public FicheEquipementVue ficheEquipement(Long equipementId) {
        Equipement equipement = equipementRepository
                .findAvecStationParId(equipementId)
                .orElseThrow(() ->
                        new RessourceIntrouvableException(
                                "L'équipement demandé n'existe pas."));

        List<IncidentVue> incidents = incidentRepository
                .findByEquipementIdOrderByDateOuvertureDesc(equipementId)
                .stream()
                .map(this::versIncidentVue)
                .toList();

        Station station = equipement.getStation();

        return new FicheEquipementVue(
                equipement.getId(),
                equipement.getReference(),
                equipement.getLibelle(),
                equipement.getType(),
                equipement.getEtat(),
                station.getId(),
                station.getNom(),
                incidents);
    }

    private EquipementVue versEquipementVue(Equipement equipement) {
        return new EquipementVue(
                equipement.getId(),
                equipement.getReference(),
                equipement.getLibelle(),
                equipement.getType(),
                equipement.getEtat());
    }

    private IncidentVue versIncidentVue(Incident incident) {
        return new IncidentVue(
                incident.getId(),
                incident.getDescription(),
                incident.getDateOuverture(),
                incident.getStatut());
    }
}
