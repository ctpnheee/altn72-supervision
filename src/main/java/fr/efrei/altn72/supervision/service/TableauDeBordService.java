
package fr.efrei.altn72.supervision.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;

/**
 * F11 : supervision des incidents actifs.
 *
 * RG3 : vert, orange et rouge selon l'ancienneté.
 * RG4 : les incidents affectés sont pris en charge.
 * RG6 : les incidents résolus sont exclus.
 *
 * Le niveau n'est jamais stocké en base.
 */
@Service
public class TableauDeBordService {

    private final IncidentRepository incidentRepository;
    private final StationRepository stationRepository;
    private final NiveauAttentionCalculator calculator;
    private final Clock clock;

    public TableauDeBordService(
            IncidentRepository incidentRepository,
            StationRepository stationRepository,
            NiveauAttentionCalculator calculator,
            Clock clock) {

        this.incidentRepository = incidentRepository;
        this.stationRepository = stationRepository;
        this.calculator = calculator;
        this.clock = clock;
    }

    public record IncidentVue(
            Long id,
            String description,
            String referenceEquipement,
            String libelleEquipement,
            TypeEquipement typeEquipement,
            Long stationId,
            String stationNom,
            LocalDateTime dateOuverture,
            StatutIncident statut,
            NiveauAttention niveau,
            long heuresOuverture,
            String prestataireNom) {
    }

    public record StationVue(
            Long id,
            String nom) {
    }

    public record StatistiqueStation(
            String nom,
            long nombre) {
    }

    public record TableauDeBord(
            List<IncidentVue> incidents,
            List<StationVue> stations,
            long totalActifs,
            long nonAffectes,
            long verts,
            long oranges,
            long rouges,
            long prisEnCharge,
            double dureeMoyenneHeures,
            List<StatistiqueStation> repartitionStations) {
    }

    /**
     * Liste filtrable et indicateurs du tableau de bord.
     *
     * Les statistiques sont calculées sur l'ensemble
     * des incidents actifs, avant application des filtres.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('RESPONSABLE')")
    public TableauDeBord consulter(
            Long stationId,
            TypeEquipement type,
            NiveauAttention niveau) {

        List<Incident> actifs = incidentRepository
                .findActifsAvecEquipement(StatutIncident.RESOLU);

        LocalDateTime maintenant = LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone());

        List<IncidentVue> toutesLesVues = actifs.stream()
                .map(incident -> convertir(incident, maintenant))
                .toList();

        List<IncidentVue> filtres = toutesLesVues.stream()
                .filter(incident ->
                        stationId == null
                                || stationId.equals(incident.stationId()))
                .filter(incident ->
                        type == null
                                || type == incident.typeEquipement())
                .filter(incident ->
                        niveau == null
                                || niveau == incident.niveau())
                .sorted(
                        Comparator.comparingInt(
                                        (IncidentVue incident) ->
                                                priorite(incident.niveau()))
                                .thenComparing(IncidentVue::dateOuverture)
                                .thenComparing(IncidentVue::id))
                .toList();

        long nonAffectes = toutesLesVues.stream()
                .filter(incident ->
                        incident.statut() == StatutIncident.NON_AFFECTE)
                .count();

        long verts = compter(toutesLesVues, NiveauAttention.VERT);
        long oranges = compter(toutesLesVues, NiveauAttention.ORANGE);
        long rouges = compter(toutesLesVues, NiveauAttention.ROUGE);

        long prisEnCharge = compter(
                toutesLesVues,
                NiveauAttention.PRIS_EN_CHARGE);

        double moyenne = toutesLesVues.stream()
                .mapToLong(IncidentVue::heuresOuverture)
                .average()
                .orElse(0.0);

        List<StationVue> stations = stationRepository
                .findAllByOrderByNomAsc()
                .stream()
                .map(station -> new StationVue(
                        station.getId(),
                        station.getNom()))
                .toList();

        return new TableauDeBord(
                filtres,
                stations,
                toutesLesVues.size(),
                nonAffectes,
                verts,
                oranges,
                rouges,
                prisEnCharge,
                moyenne,
                chargerRepartitionStations());
    }

    private IncidentVue convertir(
            Incident incident,
            LocalDateTime maintenant) {

        var equipement = incident.getEquipement();
        var station = equipement.getStation();

        long heures = Math.max(
                0,
                Duration.between(
                        incident.getDateOuverture(),
                        maintenant).toHours());

        return new IncidentVue(
                incident.getId(),
                incident.getDescription(),
                equipement.getReference(),
                equipement.getLibelle(),
                equipement.getType(),
                station.getId(),
                station.getNom(),
                incident.getDateOuverture(),
                incident.getStatut(),
                calculator.calculer(incident),
                heures,
                incident.getPrestataire() == null
                        ? null
                        : incident.getPrestataire().getNom());
    }

    private long compter(
            List<IncidentVue> incidents,
            NiveauAttention niveau) {

        return incidents.stream()
                .filter(incident -> incident.niveau() == niveau)
                .count();
    }

    /**
     * Les incidents critiques sont affichés en premier.
     * Les affectés apparaissent dans une catégorie distincte.
     */
    private int priorite(NiveauAttention niveau) {
        return switch (niveau) {
            case ROUGE -> 0;
            case ORANGE -> 1;
            case VERT -> 2;
            case PRIS_EN_CHARGE -> 3;
        };
    }


    /**
     * B10 : répartition des incidents actifs par station.
     *
     * Les données proviennent de l'unique requête SQL
     * native déclarée dans IncidentRepository.
     *
     * La transformation vers StatistiqueStation reste
     * dans le service, pas dans le contrôleur ni la vue.
     */
    private List<StatistiqueStation> chargerRepartitionStations() {

        return incidentRepository
                .compterIncidentsActifsParStation()
                .stream()
                .map(resultat -> new StatistiqueStation(
                        resultat.getStationNom(),
                        resultat.getNombre()))
                .toList();
    }

}
