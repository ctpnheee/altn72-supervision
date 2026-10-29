package fr.efrei.altn72.supervision.config;

import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 * Choisit la page affichée quand Spring signale une erreur HTTP (URL inconnue, accès refusé, erreur interne).
 * Sans cela, Spring Boot afficherait sa page « Whitelabel » pour tous les codes sans modèle dédié.
 */
@Configuration
public class PagesErreurConfig {

	@Bean
	ErrorViewResolver pagesErreur() {
		return (requete, statut, modele) -> new ModelAndView(vuePour(statut), modele, statut);
	}

	private static String vuePour(HttpStatus statut) {
		return switch (statut) {
			case NOT_FOUND -> "error/404";
			case FORBIDDEN -> "error/403";
			default -> "error/erreur";
		};
	}
}
