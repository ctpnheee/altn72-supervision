package fr.efrei.altn72.supervision.model;

import java.time.LocalDateTime;

import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Un incident est rattaché à un équipement (RG1). Son niveau d'attention (vert, orange, rouge)
 * n'est volontairement pas un champ : il dépend de l'heure courante et est calculé à la lecture.
 */
@Entity
public class Incident {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Column(nullable = false, length = 1000)
	private String description;

	@NotNull
	@Column(nullable = false)
	private LocalDateTime dateOuverture;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatutIncident statut;

	/** Renseignée uniquement quand l'incident est résolu. */
	private LocalDateTime dateResolution;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipement_id", nullable = false)
	private Equipement equipement;

	/** Utilisateur qui a signalé la panne. */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "declarant_id", nullable = false)
	private Utilisateur declarant;

	/** Absent tant que l'incident n'est pas affecté. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "prestataire_id")
	private Prestataire prestataire;

	protected Incident() {
		// requis par JPA
	}

	/** Un incident déclaré est ouvert et sans prestataire (RG1). */
	public Incident(String description, LocalDateTime dateOuverture, Equipement equipement, Utilisateur declarant) {
		this.description = description;
		this.dateOuverture = dateOuverture;
		this.equipement = equipement;
		this.declarant = declarant;
		this.statut = StatutIncident.NON_AFFECTE;
	}

	public Long getId() {
		return id;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDateTime getDateOuverture() {
		return dateOuverture;
	}

	public StatutIncident getStatut() {
		return statut;
	}

	public void setStatut(StatutIncident statut) {
		this.statut = statut;
	}

	public LocalDateTime getDateResolution() {
		return dateResolution;
	}

	public void setDateResolution(LocalDateTime dateResolution) {
		this.dateResolution = dateResolution;
	}

	public Equipement getEquipement() {
		return equipement;
	}

	public Utilisateur getDeclarant() {
		return declarant;
	}

	public Prestataire getPrestataire() {
		return prestataire;
	}

	public void setPrestataire(Prestataire prestataire) {
		this.prestataire = prestataire;
	}
}
