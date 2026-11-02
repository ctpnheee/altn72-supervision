package fr.efrei.altn72.supervision.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nouvelle description d'un incident (F6). Le reste de la fiche n'est pas saisissable ici. */
public class ModificationDescriptionForm {

	@NotBlank(message = "Décrivez la panne.")
	@Size(max = 1000, message = "La description ne peut pas dépasser 1000 caractères.")
	private String description;

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
