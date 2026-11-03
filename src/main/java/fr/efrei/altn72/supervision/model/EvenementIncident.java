package fr.efrei.altn72.supervision.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Une ligne du journal métier d'un incident : qui a fait quoi, et quand. */
@Entity
public class EvenementIncident {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "incident_id", nullable = false)
	private Incident incident;

	@NotNull
	@Column(nullable = false)
	private LocalDateTime date;

	@NotBlank
	@Column(nullable = false)
	private String auteur;

	@NotBlank
	@Column(nullable = false, length = 500)
	private String action;

	protected EvenementIncident() {
		// requis par JPA
	}

	public EvenementIncident(Incident incident, LocalDateTime date, String auteur, String action) {
		this.incident = incident;
		this.date = date;
		this.auteur = auteur;
		this.action = action;
	}

	public Long getId() {
		return id;
	}

	public Incident getIncident() {
		return incident;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public String getAuteur() {
		return auteur;
	}

	public String getAction() {
		return action;
	}
}
