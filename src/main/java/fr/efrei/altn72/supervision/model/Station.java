package fr.efrei.altn72.supervision.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Station {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Column(nullable = false)
	private String nom;

	protected Station() {
		// requis par JPA
	}

	public Station(String nom) {
		this.nom = nom;
	}

	public Long getId() {
		return id;
	}

	public String getNom() {
		return nom;
	}
}
