
package fr.efrei.altn72.supervision.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.service.TableauDeBordService;

@Controller
public class TableauDeBordController {

    private final TableauDeBordService tableauDeBordService;

    public TableauDeBordController(
            TableauDeBordService tableauDeBordService) {
        this.tableauDeBordService = tableauDeBordService;
    }

    @GetMapping("/tableau-de-bord")
    @PreAuthorize("hasRole('RESPONSABLE')")
    public String afficher(
            @RequestParam(required = false) Long station,
            @RequestParam(required = false) TypeEquipement type,
            @RequestParam(required = false) NiveauAttention niveau,
            Model model) {

        model.addAttribute(
                "tableau",
                tableauDeBordService.consulter(station, type, niveau));

        model.addAttribute("stationSelectionnee", station);
        model.addAttribute("typeSelectionne", type);
        model.addAttribute("niveauSelectionne", niveau);

        model.addAttribute(
                "typesEquipement",
                TypeEquipement.values());

        model.addAttribute(
                "niveauxAttention",
                NiveauAttention.values());

        return "tableauDeBord";
    }
}
