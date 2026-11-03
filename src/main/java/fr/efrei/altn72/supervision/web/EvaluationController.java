
package fr.efrei.altn72.supervision.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import fr.efrei.altn72.supervision.service.EvaluationService;
import fr.efrei.altn72.supervision.web.dto.EvaluationForm;
import jakarta.validation.Valid;

@Controller
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/incidents/{id}/evaluation")
    @PreAuthorize("hasRole('RESPONSABLE')")
    public String evaluer(
            @PathVariable Long id,
            @Valid @ModelAttribute("evaluation") EvaluationForm evaluation,
            BindingResult erreurs,
            RedirectAttributes redirection) {

        if (erreurs.hasErrors()) {
            redirection.addFlashAttribute(
                    "evaluation",
                    evaluation);

            redirection.addFlashAttribute(
                    BindingResult.MODEL_KEY_PREFIX + "evaluation",
                    erreurs);

            return "redirect:/incidents/" + id;
        }

        evaluationService.evaluer(
                id,
                evaluation.getNote(),
                evaluation.getCommentaire());

        redirection.addFlashAttribute(
                "succes",
                "L'intervention a été évaluée avec succès.");

        return "redirect:/incidents/" + id;
    }
}
