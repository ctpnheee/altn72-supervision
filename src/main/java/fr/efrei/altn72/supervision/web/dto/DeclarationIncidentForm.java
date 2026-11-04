package fr.efrei.altn72.supervision.web.dto;

import fr.efrei.altn72.supervision.model.Incident;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Saisie de la déclaration d'un incident (F4).
 * Le prestataire n'y figure pas : un incident déclaré démarre sans prestataire (RG1).
 */
public class DeclarationIncidentForm {

	@NotNull(message = "Choisissez un équipement.")
	private Long equipementId;

	@NotBlank(message = Incident.DESCRIPTION_OBLIGATOIRE)
	@Size(max = Incident.DESCRIPTION_MAX, message = Incident.DESCRIPTION_TROP_LONGUE)
	private String description;

	public Long getEquipementId() {
		return equipementId;
	}

	public void setEquipementId(Long equipementId) {
		this.equipementId = equipementId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
