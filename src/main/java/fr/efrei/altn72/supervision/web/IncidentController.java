package fr.efrei.altn72.supervision.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import fr.efrei.altn72.supervision.config.UtilisateurConnecte;
import fr.efrei.altn72.supervision.service.IncidentService;
import fr.efrei.altn72.supervision.web.dto.DeclarationIncidentForm;
import jakarta.validation.Valid;

/** Pages du cycle de vie d'un incident. La déclaration (F4) vit sous {@code /incidents/nouveau}. */
@Controller
public class IncidentController {

	static final String VUE_DECLARATION = "incident/declarer";

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
}
