
package fr.efrei.altn72.supervision.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import fr.efrei.altn72.supervision.service.EquipementService;

@Controller
public class EquipementController {

    private final EquipementService equipementService;

    public EquipementController(EquipementService equipementService) {
        this.equipementService = equipementService;
    }

    @GetMapping("/stations")
    public String listerStations(Model model) {
        model.addAttribute(
                "stations",
                equipementService.listerStations());

        return "stations";
    }

    @GetMapping("/stations/{id}/equipements")
    public String equipementsParStation(
            @PathVariable("id") Long id,
            Model model) {

        model.addAttribute(
                "station",
                equipementService.equipementsParStation(id));

        return "equipementsStation";
    }

    @GetMapping("/equipements/{id}")
    public String ficheEquipement(
            @PathVariable("id") Long id,
            Model model) {

        model.addAttribute(
                "equipement",
                equipementService.ficheEquipement(id));

        return "ficheEquipement";
    }
}
