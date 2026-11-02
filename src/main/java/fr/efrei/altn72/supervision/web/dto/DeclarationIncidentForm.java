package fr.efrei.altn72.supervision.web.dto;

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

	@NotBlank(message = "Décrivez la panne.")
	@Size(max = 1000, message = "La description ne peut pas dépasser 1000 caractères.")
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
