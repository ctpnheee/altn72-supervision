package fr.efrei.altn72.supervision.web.dto;

import jakarta.validation.constraints.NotNull;

/** Choix du prestataire à affecter ou réaffecter (F7). */
public class AffectationForm {

	/** Même phrase que {@code AffectationRequete} : la page et l'API ne se référencent pas. */
	public static final String PRESTATAIRE_OBLIGATOIRE = "Choisissez un prestataire.";

	@NotNull(message = PRESTATAIRE_OBLIGATOIRE)
	private Long prestataireId;

	public Long getPrestataireId() {
		return prestataireId;
	}

	public void setPrestataireId(Long prestataireId) {
		this.prestataireId = prestataireId;
	}
}
