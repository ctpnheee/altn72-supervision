package fr.efrei.altn72.supervision.web.dto;

import fr.efrei.altn72.supervision.model.Incident;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nouvelle description d'un incident (F6). Le reste de la fiche n'est pas saisissable ici. */
public class ModificationDescriptionForm {

	@NotBlank(message = Incident.DESCRIPTION_OBLIGATOIRE)
	@Size(max = Incident.DESCRIPTION_MAX, message = Incident.DESCRIPTION_TROP_LONGUE)
	private String description;

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
