
package fr.efrei.altn72.supervision.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import fr.efrei.altn72.supervision.service.PrestataireService;

@Controller
public class PrestataireController {

    private final PrestataireService prestataireService;

    public PrestataireController(PrestataireService prestataireService) {
        this.prestataireService = prestataireService;
    }

    @GetMapping("/prestataires")
    public String lister(Model model) {

        model.addAttribute(
                "prestataires",
                prestataireService.listerPrestataires());

        return "prestataires";
    }

    @GetMapping("/prestataires/{id}")
    public String detail(
            @PathVariable Long id,
            Model model) {

        model.addAttribute(
                "prestataire",
                prestataireService.consulter(id));

        return "prestataire/detail";
    }
}
