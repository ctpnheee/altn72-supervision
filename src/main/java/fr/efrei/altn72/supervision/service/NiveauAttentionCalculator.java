
package fr.efrei.altn72.supervision.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;

import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;

/**
 * Calcule le niveau d'attention d'un incident actif.
 *
 * RG3 : vert avant 48 h, orange avant 72 h,
 *       rouge à partir de 72 h.
 * RG4 : un incident affecté est pris en charge.
 *
 * Le résultat n'est jamais stocké en base de données.
 */
@Service
public class NiveauAttentionCalculator {

    private static final Duration SEUIL_ORANGE =
            Duration.ofHours(48);

    private static final Duration SEUIL_ROUGE =
            Duration.ofHours(72);

    private final Clock clock;

    public NiveauAttentionCalculator(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Retourne le niveau d'attention d'un incident actif.
     *
     * @throws IllegalArgumentException si l'incident est résolu
     *         ou si ses données indispensables sont absentes.
     */
    public NiveauAttention calculer(Incident incident) {
        Objects.requireNonNull(
                incident,
                "L'incident ne peut pas être nul."
        );

        StatutIncident statut = Objects.requireNonNull(
                incident.getStatut(),
                "Le statut de l'incident est obligatoire."
        );

        if (statut == StatutIncident.RESOLU) {
            throw new IllegalArgumentException(
                    "Un incident résolu n'a pas de niveau d'attention actif."
            );
        }

        if (statut == StatutIncident.AFFECTE) {
            return NiveauAttention.PRIS_EN_CHARGE;
        }

        LocalDateTime ouverture = Objects.requireNonNull(
                incident.getDateOuverture(),
                "La date d'ouverture est obligatoire."
        );

        LocalDateTime maintenant = LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone()
        );

        Duration duree = Duration.between(ouverture, maintenant);

        if (duree.compareTo(SEUIL_ORANGE) < 0) {
            return NiveauAttention.VERT;
        }

        if (duree.compareTo(SEUIL_ROUGE) < 0) {
            return NiveauAttention.ORANGE;
        }

        return NiveauAttention.ROUGE;
    }
}
