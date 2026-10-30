
package fr.efrei.altn72.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;

class EquipementServiceTest {

    private StationRepository stationRepository;
    private EquipementRepository equipementRepository;
    private IncidentRepository incidentRepository;
    private EquipementService service;

    @BeforeEach
    void preparer() {
        stationRepository = mock(StationRepository.class);
        equipementRepository = mock(EquipementRepository.class);
        incidentRepository = mock(IncidentRepository.class);

        service = new EquipementService(
                stationRepository,
                equipementRepository,
                incidentRepository);
    }

    @Test
    void listerStationsRetourneLesStations() {
        when(stationRepository.findAllByOrderByNomAsc())
                .thenReturn(List.of(
                        new Station("Châtelet"),
                        new Station("République")));

        var resultat = service.listerStations();

        assertThat(resultat).hasSize(2);
        assertThat(resultat.get(0).nom()).isEqualTo("Châtelet");
        assertThat(resultat.get(1).nom()).isEqualTo("République");
    }

    @Test
    void stationInexistanteDeclencheUneException() {
        when(stationRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.equipementsParStation(999L))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("station");
    }

    @Test
    void equipementsDUneStationSontRetournes() {
        Station station = new Station("Châtelet");

        Equipement equipement = new Equipement(
                "ASC-001",
                "Ascenseur quai A",
                TypeEquipement.ASCENSEUR,
                station);

        when(stationRepository.findById(1L))
                .thenReturn(Optional.of(station));

        when(equipementRepository
                .findByStationIdOrderByReferenceAsc(1L))
                .thenReturn(List.of(equipement));

        var resultat = service.equipementsParStation(1L);

        assertThat(resultat.nom()).isEqualTo("Châtelet");
        assertThat(resultat.equipements()).hasSize(1);
        assertThat(resultat.equipements().get(0).reference())
                .isEqualTo("ASC-001");
    }

    @Test
    void equipementInexistantDeclencheUneException() {
        when(equipementRepository.findAvecStationParId(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.ficheEquipement(999L))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("équipement");
    }

    @Test
    void ficheEquipementContientSesIncidentsTries() {
        Station station = new Station("Châtelet");

        Equipement equipement = new Equipement(
                "ASC-001",
                "Ascenseur quai A",
                TypeEquipement.ASCENSEUR,
                station);

        Incident ancien = new Incident(
                "Incident ancien",
                LocalDateTime.of(2026, 10, 1, 12, 0),
                equipement,
                null);

        Incident recent = new Incident(
                "Incident récent",
                LocalDateTime.of(2026, 10, 3, 12, 0),
                equipement,
                null);

        when(equipementRepository.findAvecStationParId(1L))
                .thenReturn(Optional.of(equipement));

        when(incidentRepository
                .findByEquipementIdOrderByDateOuvertureDesc(1L))
                .thenReturn(List.of(recent, ancien));

        var resultat = service.ficheEquipement(1L);

        assertThat(resultat.reference()).isEqualTo("ASC-001");
        assertThat(resultat.incidents())
                .extracting(EquipementService.IncidentVue::description)
                .containsExactly("Incident récent", "Incident ancien");
    }
}
