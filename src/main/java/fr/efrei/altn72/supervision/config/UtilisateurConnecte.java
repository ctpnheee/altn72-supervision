package fr.efrei.altn72.supervision.config;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;

/**
 * Utilisateur authentifié tel que Spring Security le conserve en session.
 * Il ajoute le prénom et le profil, nécessaires à l'en-tête « Bonjour Prénom (profil) ».
 */
public class UtilisateurConnecte extends User {

	private static final String PREFIXE_ROLE = "ROLE_";

	private final String prenom;
	private final Profil profil;

	public UtilisateurConnecte(Utilisateur utilisateur) {
		super(utilisateur.getLogin(), utilisateur.getMotDePasse(),
				List.of(new SimpleGrantedAuthority(PREFIXE_ROLE + utilisateur.getProfil().name())));
		this.prenom = utilisateur.getPrenom();
		this.profil = utilisateur.getProfil();
	}

	public String getPrenom() {
		return prenom;
	}

	public Profil getProfil() {
		return profil;
	}
}
