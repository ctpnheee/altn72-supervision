package fr.efrei.altn72.supervision.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

/**
 * Droit de modifier la description d'un incident (F6, RG11).
 * Un responsable corrige les incidents non résolus. Un agent corrige seulement
 * les siens, et seulement tant qu'ils ne sont pas affectés.
 */
@Component("autorisationsIncident")
public class AutorisationsIncident {

	private final IncidentRepository incidentRepository;
	private final UtilisateurRepository utilisateurRepository;

	public AutorisationsIncident(IncidentRepository incidentRepository, UtilisateurRepository utilisateurRepository) {
		this.incidentRepository = incidentRepository;
		this.utilisateurRepository = utilisateurRepository;
	}

	/**
	 * Utilisé par {@code @PreAuthorize}. Un incident inconnu laisse passer : le service
	 * répondra 404, pas 403.
	 */
	@Transactional(readOnly = true)
	public boolean peutModifierDescription(Long incidentId, String login) {
		return incidentRepository.findDetailParId(incidentId)
				.map(incident -> peutModifier(incident, login))
				.orElse(true);
	}

	public boolean peutModifier(Incident incident, String login) {
		if (login == null || incident.getStatut() == StatutIncident.RESOLU) {
			return false;
		}
		Utilisateur utilisateur = utilisateurRepository.findByLogin(login).orElse(null);
		if (utilisateur == null) {
			return false;
		}
		if (utilisateur.getProfil() == Profil.RESPONSABLE) {
			return true;
		}
		return incident.getStatut() == StatutIncident.NON_AFFECTE
				&& login.equals(incident.getDeclarant().getLogin());
	}
}
