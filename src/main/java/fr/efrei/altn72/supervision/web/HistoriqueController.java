
package fr.efrei.altn72.supervision.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import fr.efrei.altn72.supervision.service.HistoriqueService;

/**
 * Contrôleur MVC de l'historique des incidents résolus.
 */
@Controller
public class HistoriqueController {

    private final HistoriqueService historiqueService;

    public HistoriqueController(HistoriqueService historiqueService) {
        this.historiqueService = historiqueService;
    }

    @GetMapping("/historique")
    public String afficherHistorique(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute(
                "historique",
                historiqueService.consulterHistorique(page));

        return "historique";
    }
}
