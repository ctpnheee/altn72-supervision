
package fr.efrei.altn72.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;

class NiveauAttentionCalculatorTest {

    private static final Instant INSTANT_FIXE =
            Instant.parse("2026-10-30T12:00:00Z");

    private NiveauAttentionCalculator calculator;

    @BeforeEach
    void preparerCalculateur() {
        Clock clock = Clock.fixed(
                INSTANT_FIXE,
                ZoneOffset.UTC
        );

        calculator = new NiveauAttentionCalculator(clock);
    }

    private Incident creerIncidentOuvertDepuis(Duration duree) {
        LocalDateTime ouverture = LocalDateTime.ofInstant(
                INSTANT_FIXE.minus(duree),
                ZoneOffset.UTC
        );

        return new Incident(
                "Panne d'ascenseur",
                ouverture,
                null,
                null
        );
    }

    @Test
    void incidentOuvertDepuis47HeuresEstVert() {
        Incident incident = creerIncidentOuvertDepuis(
                Duration.ofHours(47)
        );

        assertThat(calculator.calculer(incident))
                .isEqualTo(NiveauAttention.VERT);
    }

    @Test
    void incidentOuvertDepuis48HeuresEstOrange() {
        Incident incident = creerIncidentOuvertDepuis(
                Duration.ofHours(48)
        );

        assertThat(calculator.calculer(incident))
                .isEqualTo(NiveauAttention.ORANGE);
    }

    @Test
    void incidentOuvertDepuis71Heures59MinutesEstOrange() {
        Incident incident = creerIncidentOuvertDepuis(
                Duration.ofHours(71).plusMinutes(59)
        );

        assertThat(calculator.calculer(incident))
                .isEqualTo(NiveauAttention.ORANGE);
    }

    @Test
    void incidentOuvertDepuis72HeuresEstRouge() {
        Incident incident = creerIncidentOuvertDepuis(
                Duration.ofHours(72)
        );

        assertThat(calculator.calculer(incident))
                .isEqualTo(NiveauAttention.ROUGE);
    }

    @Test
    void incidentAffecteDepuis100HeuresEstPrisEnCharge() {
        Incident incident = creerIncidentOuvertDepuis(
                Duration.ofHours(100)
        );

        incident.setStatut(StatutIncident.AFFECTE);

        assertThat(calculator.calculer(incident))
                .isEqualTo(NiveauAttention.PRIS_EN_CHARGE);
    }

    @Test
    void incidentOuvertDepuisZeroHeureEstVert() {
        Incident incident = creerIncidentOuvertDepuis(
                Duration.ZERO
        );

        assertThat(calculator.calculer(incident))
                .isEqualTo(NiveauAttention.VERT);
    }

    @Test
    void incidentOuvertDepuis48HeuresMoinsUneSecondeEstVert() {
        Incident incident = creerIncidentOuvertDepuis(
                Duration.ofHours(48).minusSeconds(1)
        );

        assertThat(calculator.calculer(incident))
                .isEqualTo(NiveauAttention.VERT);
    }

    @Test
    void incidentResoluNePeutPasAvoirDeNiveauActif() {
        Incident incident = creerIncidentOuvertDepuis(
                Duration.ofHours(100)
        );

        incident.setStatut(StatutIncident.RESOLU);

        assertThatThrownBy(() -> calculator.calculer(incident))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("résolu");
    }
}
