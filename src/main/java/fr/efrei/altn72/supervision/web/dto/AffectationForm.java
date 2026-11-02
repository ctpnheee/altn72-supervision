package fr.efrei.altn72.supervision.web.dto;

import jakarta.validation.constraints.NotNull;

/** Choix du prestataire à affecter ou réaffecter (F7). */
public class AffectationForm {

	@NotNull(message = "Choisissez un prestataire.")
	private Long prestataireId;

	public Long getPrestataireId() {
		return prestataireId;
	}

	public void setPrestataireId(Long prestataireId) {
		this.prestataireId = prestataireId;
	}
}
