package fr.efrei.altn72.supervision.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Évaluation de l'intervention d'un prestataire. Un incident a au plus une évaluation :
 * la contrainte {@code unique = true} sur la clé étrangère le garantit en base (RG8).
 */
@Entity
public class Evaluation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Min(1)
	@Max(5)
	@Column(nullable = false)
	private int note;

	@Column(length = 1000)
	private String commentaire;

	@NotNull
	@Column(name = "date_evaluation", nullable = false)
	private LocalDateTime date;

	@NotNull
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "incident_id", nullable = false, unique = true)
	private Incident incident;

	protected Evaluation() {
		// requis par JPA
	}

	public Evaluation(Incident incident, int note, String commentaire, LocalDateTime date) {
		this.incident = incident;
		this.note = note;
		this.commentaire = commentaire;
		this.date = date;
	}

	public Long getId() {
		return id;
	}

	public int getNote() {
		return note;
	}

	public String getCommentaire() {
		return commentaire;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public Incident getIncident() {
		return incident;
	}
}
