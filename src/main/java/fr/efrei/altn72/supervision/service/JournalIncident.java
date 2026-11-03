package fr.efrei.altn72.supervision.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.EvenementIncident;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.repository.EvenementIncidentRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

/**
 * Écrit le journal métier. L'écoute est synchrone et rejoint la transaction en cours :
 * l'incident et sa ligne sont enregistrés ensemble. Après le commit, un échec du journal
 * laisserait un incident sans trace.
 */
@Component
public class JournalIncident {

	private final Clock clock;
	private final IncidentRepository incidentRepository;
	private final UtilisateurRepository utilisateurRepository;
	private final EvenementIncidentRepository evenementRepository;

	public JournalIncident(Clock clock, IncidentRepository incidentRepository, UtilisateurRepository utilisateurRepository,
			EvenementIncidentRepository evenementRepository) {
		this.clock = clock;
		this.incidentRepository = incidentRepository;
		this.utilisateurRepository = utilisateurRepository;
		this.evenementRepository = evenementRepository;
	}

	@EventListener
	@Transactional
	public void enregistrer(EvenementIncidentDemande demande) {
		Incident incident = incidentRepository.findById(demande.incidentId())
				.orElseThrow(() -> new RessourceIntrouvableException("Incident " + demande.incidentId() + " introuvable."));
		String auteur = utilisateurRepository.findByLogin(demande.login())
				.map(utilisateur -> utilisateur.getPrenom())
				.orElse(demande.login());
		LocalDateTime date = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		evenementRepository.save(new EvenementIncident(incident, date, auteur, demande.action()));
	}
}
