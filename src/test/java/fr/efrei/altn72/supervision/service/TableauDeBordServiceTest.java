
package fr.efrei.altn72.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;

@ExtendWith(MockitoExtension.class)
class TableauDeBordServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private StationRepository stationRepository;

    private TableauDeBordService service;

    private static final Instant MAINTENANT =
            Instant.parse("2026-11-04T12:00:00Z");

    @BeforeEach
    void preparer() {
        Clock clock = Clock.fixed(
                MAINTENANT,
                ZoneOffset.UTC);

        service = new TableauDeBordService(
                incidentRepository,
                stationRepository,
                new NiveauAttentionCalculator(clock),
                clock);
    }

    private Incident creerIncident(
            Long id,
            String description,
            long heures,
            StatutIncident statut,
            Long stationId,
            String stationNom,
            TypeEquipement type) {

        Station station = mock(Station.class);

        when(station.getId()).thenReturn(stationId);
        when(station.getNom()).thenReturn(stationNom);

        Equipement equipement = new Equipement(
                "EQ-" + id,
                description,
                type,
                station);

        LocalDateTime ouverture = LocalDateTime.ofInstant(
                MAINTENANT.minusSeconds(heures * 3600),
                ZoneOffset.UTC);

        Incident incident = new Incident(
                description,
                ouverture,
                equipement,
                null);

        incident.setStatut(statut);

        // L'identifiant est fourni par la base dans les cas réels.
        // Ici, un mock permet de lui attribuer un ID pour le tri.
        Incident resultat = mock(Incident.class);

        when(resultat.getId()).thenReturn(id);
        when(resultat.getDescription()).thenReturn(description);
        when(resultat.getDateOuverture()).thenReturn(ouverture);
        when(resultat.getStatut()).thenReturn(statut);
        when(resultat.getEquipement()).thenReturn(equipement);

        return resultat;
    }

    @Test
    void incidentResoluEstAbsentDuTableauDeBord() {

        Incident actif = creerIncident(
                1L,
                "Incident actif",
                50,
                StatutIncident.NON_AFFECTE,
                10L,
                "Châtelet",
                TypeEquipement.ASCENSEUR);

        when(incidentRepository.findActifsAvecEquipement(
                StatutIncident.RESOLU))
                .thenReturn(List.of(actif));

        when(stationRepository.findAllByOrderByNomAsc())
                .thenReturn(List.of());

        var tableau = service.consulter(null, null, null);

        assertThat(tableau.totalActifs()).isEqualTo(1);
        assertThat(tableau.incidents()).hasSize(1);
        assertThat(tableau.incidents().get(0).niveau())
                .isEqualTo(NiveauAttention.ORANGE);
    }

    @Test
    void filtresStationTypeEtNiveauSontCombinables() {

        Incident rougeChatelet = creerIncident(
                1L, "Ascenseur rouge", 90,
                StatutIncident.NON_AFFECTE,
                10L, "Châtelet", TypeEquipement.ASCENSEUR);

        Incident vertChatelet = creerIncident(
                2L, "Ascenseur vert", 12,
                StatutIncident.NON_AFFECTE,
                10L, "Châtelet", TypeEquipement.ASCENSEUR);

        Incident rougeRepublique = creerIncident(
                3L, "Portique rouge", 100,
                StatutIncident.NON_AFFECTE,
                20L, "République", TypeEquipement.PORTIQUE);

        when(incidentRepository.findActifsAvecEquipement(
                StatutIncident.RESOLU))
                .thenReturn(List.of(
                        rougeChatelet,
                        vertChatelet,
                        rougeRepublique));

        when(stationRepository.findAllByOrderByNomAsc())
                .thenReturn(List.of());

        var tableau = service.consulter(
                10L,
                TypeEquipement.ASCENSEUR,
                NiveauAttention.ROUGE);

        assertThat(tableau.incidents())
                .extracting(TableauDeBordService.IncidentVue::description)
                .containsExactly("Ascenseur rouge");

        // Les indicateurs restent globaux.
        assertThat(tableau.totalActifs()).isEqualTo(3);
        assertThat(tableau.rouges()).isEqualTo(2);
        assertThat(tableau.verts()).isEqualTo(1);
    }

    @Test
    void incidentsRougesSontAffichesAvantLesAutres() {

        Incident vert = creerIncident(
                1L, "Vert", 10,
                StatutIncident.NON_AFFECTE,
                10L, "Châtelet", TypeEquipement.ASCENSEUR);

        Incident orange = creerIncident(
                2L, "Orange", 50,
                StatutIncident.NON_AFFECTE,
                10L, "Châtelet", TypeEquipement.ASCENSEUR);

        Incident rouge = creerIncident(
                3L, "Rouge", 90,
                StatutIncident.NON_AFFECTE,
                10L, "Châtelet", TypeEquipement.ASCENSEUR);

        Incident affecte = creerIncident(
                4L, "Pris en charge", 100,
                StatutIncident.AFFECTE,
                10L, "Châtelet", TypeEquipement.ASCENSEUR);

        when(incidentRepository.findActifsAvecEquipement(
                StatutIncident.RESOLU))
                .thenReturn(List.of(
                        vert, orange, rouge, affecte));

        when(stationRepository.findAllByOrderByNomAsc())
                .thenReturn(List.of());

        var tableau = service.consulter(null, null, null);

        assertThat(tableau.incidents())
                .extracting(TableauDeBordService.IncidentVue::description)
                .containsExactly(
                        "Rouge",
                        "Orange",
                        "Vert",
                        "Pris en charge");

        assertThat(tableau.prisEnCharge()).isEqualTo(1);
        assertThat(tableau.nonAffectes()).isEqualTo(3);
    }
}
