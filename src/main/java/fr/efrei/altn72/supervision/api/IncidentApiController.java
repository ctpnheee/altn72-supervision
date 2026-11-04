package fr.efrei.altn72.supervision.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.efrei.altn72.supervision.api.dto.AffectationRequete;
import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.service.IncidentService;
import fr.efrei.altn72.supervision.service.IncidentService.IncidentResume;
import jakarta.validation.Valid;

/**
 * API des incidents (F12). Le contrôleur valide, appelle le service et renvoie le JSON.
 * Affecter et résoudre réutilisent les méthodes des pages : les règles RG5 et RG10 n'existent qu'une fois.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentApiController {

	private final IncidentService incidentService;

	public IncidentApiController(IncidentService incidentService) {
		this.incidentService = incidentService;
	}

	@GetMapping
	List<IncidentResume> lister() {
		return incidentService.lister();
	}

	@GetMapping("/{id}")
	IncidentResume lire(@PathVariable Long id) {
		return incidentService.lire(id);
	}

	@PutMapping("/{id}/prestataire")
	IncidentResume affecter(@PathVariable Long id, @Valid @RequestBody AffectationRequete requete,
			@AuthenticationPrincipal UtilisateurConnecte connecte) {
		incidentService.affecter(id, requete.prestataireId(), connecte.getUsername());
		return incidentService.lire(id);
	}

	@PatchMapping("/{id}/resolution")
	IncidentResume resoudre(@PathVariable Long id, @AuthenticationPrincipal UtilisateurConnecte connecte) {
		incidentService.resoudre(id, connecte.getUsername());
		return incidentService.lire(id);
	}
}
