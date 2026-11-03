package fr.efrei.altn72.supervision.api.dto;

import jakarta.validation.constraints.NotNull;

/** Corps de {@code PUT /api/incidents/{id}/prestataire}. */
public record AffectationRequete(@NotNull(message = "Choisissez un prestataire.") Long prestataireId) {
}
