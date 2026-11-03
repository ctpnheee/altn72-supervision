
package fr.efrei.altn72.supervision.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Formulaire d'évaluation d'une intervention.
 */
public class EvaluationForm {

    @NotNull(message = "Choisissez une note.")
    @Min(value = 1, message = "La note minimale est 1.")
    @Max(value = 5, message = "La note maximale est 5.")
    private Integer note;

    @Size(
            max = 1000,
            message = "Le commentaire ne doit pas dépasser 1000 caractères."
    )
    private String commentaire;

    public Integer getNote() {
        return note;
    }

    public void setNote(Integer note) {
        this.note = note;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }
}
