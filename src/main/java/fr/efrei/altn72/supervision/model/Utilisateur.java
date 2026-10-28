package fr.efrei.altn72.supervision.model;

import fr.efrei.altn72.supervision.model.enums.Profil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
public class Utilisateur {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Column(nullable = false, unique = true)
	private String login;

	/** Mot de passe haché avec BCrypt (60 caractères) : jamais le mot de passe en clair. */
	@NotBlank
	@Column(nullable = false)
	private String motDePasse;

	@NotBlank
	@Column(nullable = false)
	private String prenom;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Profil profil;

	protected Utilisateur() {
		// requis par JPA
	}

	public Utilisateur(String login, String motDePasse, String prenom, Profil profil) {
		this.login = login;
		this.motDePasse = motDePasse;
		this.prenom = prenom;
		this.profil = profil;
	}

	public Long getId() {
		return id;
	}

	public String getLogin() {
		return login;
	}

	public String getMotDePasse() {
		return motDePasse;
	}

	public String getPrenom() {
		return prenom;
	}

	public Profil getProfil() {
		return profil;
	}
}
