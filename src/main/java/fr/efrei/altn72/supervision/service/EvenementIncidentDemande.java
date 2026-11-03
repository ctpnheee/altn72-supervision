package fr.efrei.altn72.supervision.service;

/** Fait publié par le service. Le journal décide seul comment le conserver. */
public record EvenementIncidentDemande(Long incidentId, String login, String action) {
}
