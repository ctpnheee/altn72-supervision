package fr.efrei.altn72.supervision.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

/** Fournit à Spring Security les comptes stockés dans la table {@code utilisateur}. */
@Service
public class UtilisateurDetailsService implements UserDetailsService {

	private final UtilisateurRepository utilisateurRepository;

	public UtilisateurDetailsService(UtilisateurRepository utilisateurRepository) {
		this.utilisateurRepository = utilisateurRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String login) {
		return utilisateurRepository.findByLogin(login)
				.map(UtilisateurConnecte::new)
				.orElseThrow(() -> new UsernameNotFoundException("Utilisateur inconnu : " + login));
	}
}
