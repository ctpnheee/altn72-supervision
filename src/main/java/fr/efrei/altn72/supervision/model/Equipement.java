package fr.efrei.altn72.supervision.model;

import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
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

@Entity
public class Equipement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Référence unique de l'équipement (RG9) : garantie par la base grâce à {@code unique = true}. */
	@NotBlank
	@Column(nullable = false, unique = true)
	private String reference;

	@NotBlank
	@Column(nullable = false)
	private String libelle;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TypeEquipement type;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EtatEquipement etat;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "station_id", nullable = false)
	private Station station;

	protected Equipement() {
		// requis par JPA
	}

	public Equipement(String reference, String libelle, TypeEquipement type, Station station) {
		this.reference = reference;
		this.libelle = libelle;
		this.type = type;
		this.station = station;
		this.etat = EtatEquipement.EN_SERVICE;
	}

	public Long getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public String getLibelle() {
		return libelle;
	}

	public TypeEquipement getType() {
		return type;
	}

	public EtatEquipement getEtat() {
		return etat;
	}

	public void setEtat(EtatEquipement etat) {
		this.etat = etat;
	}

	public Station getStation() {
		return station;
	}
}
