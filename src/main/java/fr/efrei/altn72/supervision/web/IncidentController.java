package fr.efrei.altn72.supervision.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.service.IncidentService;
import fr.efrei.altn72.supervision.service.IncidentService.DetailIncident;
import fr.efrei.altn72.supervision.web.dto.AffectationForm;
import fr.efrei.altn72.supervision.web.dto.DeclarationIncidentForm;
import fr.efrei.altn72.supervision.web.dto.ModificationDescriptionForm;
import jakarta.validation.Valid;

/** Pages du cycle de vie d'un incident : déclaration (F4), détail (F5, F6), affectation (F7) et résolution (F8). */
@Controller
public class IncidentController {

	static final String VUE_DECLARATION = "incident/declarer";
	static final String VUE_DETAIL = "incident/detail";

	private final IncidentService incidentService;

	public IncidentController(IncidentService incidentService) {
		this.incidentService = incidentService;
	}

	@GetMapping("/incidents/nouveau")
	String formulaire(Model model) {
		model.addAttribute("declaration", new DeclarationIncidentForm());
		model.addAttribute("equipements", incidentService.listerEquipements());
		return VUE_DECLARATION;
	}

	@PostMapping("/incidents")
	String declarer(@Valid @ModelAttribute("declaration") DeclarationIncidentForm declaration, BindingResult erreurs,
			@AuthenticationPrincipal UtilisateurConnecte connecte, Model model, RedirectAttributes redirection) {
		if (erreurs.hasErrors()) {
			model.addAttribute("equipements", incidentService.listerEquipements());
			return VUE_DECLARATION;
		}
		incidentService.declarer(declaration.getEquipementId(), declaration.getDescription(), connecte.getUsername());
		redirection.addFlashAttribute("succes", "Incident déclaré.");
		return "redirect:/incidents/nouveau";
	}

	@GetMapping("/incidents/{id}")
	String detail(@PathVariable Long id, @AuthenticationPrincipal UtilisateurConnecte connecte, Model model) {
		preparerDetail(id, connecte.getUsername(), model);
		return VUE_DETAIL;
	}

	@PostMapping("/incidents/{id}/description")
	String modifier(@PathVariable Long id,
			@Valid @ModelAttribute("modification") ModificationDescriptionForm modification, BindingResult erreurs,
			@AuthenticationPrincipal UtilisateurConnecte connecte, Model model, RedirectAttributes redirection) {
		if (erreurs.hasErrors()) {
			incidentService.controlerDroitModification(id, connecte.getUsername());
			preparerDetail(id, connecte.getUsername(), model);
			return VUE_DETAIL;
		}
		incidentService.modifierDescription(id, modification.getDescription(), connecte.getUsername());
		redirection.addFlashAttribute("succes", "Description mise à jour.");
		return "redirect:/incidents/" + id;
	}

	@PostMapping("/incidents/{id}/prestataire")
	String affecter(@PathVariable Long id, @Valid @ModelAttribute("affectation") AffectationForm affectation,
			BindingResult erreurs, @AuthenticationPrincipal UtilisateurConnecte connecte, Model model,
			RedirectAttributes redirection) {
		if (erreurs.hasErrors()) {
			incidentService.controlerDroitPilotage();
			preparerDetail(id, connecte.getUsername(), model);
			return VUE_DETAIL;
		}
		incidentService.affecter(id, affectation.getPrestataireId(), connecte.getUsername());
		redirection.addFlashAttribute("succes", "Prestataire affecté.");
		return "redirect:/incidents/" + id;
	}

	/** La date vient de l'horloge du service : le formulaire n'a pas de champ. */
	@PostMapping("/incidents/{id}/resolution")
	String resoudre(@PathVariable Long id, @AuthenticationPrincipal UtilisateurConnecte connecte,
			RedirectAttributes redirection) {
		incidentService.resoudre(id, connecte.getUsername());
		redirection.addFlashAttribute("succes", "Incident résolu.");
		return "redirect:/incidents/" + id;
	}

	private void preparerDetail(Long id, String login, Model model) {
		DetailIncident detail = incidentService.consulter(id, login);
		model.addAttribute("detail", detail);
		if (!model.containsAttribute("modification")) {
			ModificationDescriptionForm modification = new ModificationDescriptionForm();
			modification.setDescription(detail.description());
			model.addAttribute("modification", modification);
		}
		if (!model.containsAttribute("affectation")) {
			model.addAttribute("affectation", new AffectationForm());
		}
	}
}
