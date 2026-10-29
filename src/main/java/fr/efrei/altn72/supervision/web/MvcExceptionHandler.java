package fr.efrei.altn72.supervision.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import fr.efrei.altn72.supervision.exception.EvaluationDejaSaisieException;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.exception.TransitionInterditeException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit les exceptions métier en pages ou en messages flash pour les contrôleurs de pages (package {@code web}).
 * Les contrôleurs REST ont leur propre gestionnaire ({@code ApiExceptionHandler}).
 */
@ControllerAdvice(basePackages = "fr.efrei.altn72.supervision.web")
public class MvcExceptionHandler {

	private static final String PAGE_PAR_DEFAUT = "/";

	@ExceptionHandler(RessourceIntrouvableException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String ressourceIntrouvable() {
		return "error/404";
	}

	/** Action refusée par une règle de gestion : retour à la page d'où elle a été demandée, avec le message. */
	@ExceptionHandler({ TransitionInterditeException.class, EvaluationDejaSaisieException.class })
	String actionImpossible(RuntimeException exception, HttpServletRequest requete, RedirectAttributes redirection) {
		redirection.addFlashAttribute("erreur", exception.getMessage());
		return "redirect:" + pageDOrigine(requete);
	}

	/**
	 * Chemin de la page d'origine (en-tête Referer). Seul le chemin local est conservé : une adresse
	 * externe ou « //hote » ne doit jamais devenir la cible d'une redirection.
	 */
	private static String pageDOrigine(HttpServletRequest requete) {
		String referent = requete.getHeader(HttpHeaders.REFERER);
		if (referent == null) {
			return PAGE_PAR_DEFAUT;
		}
		String chemin = UriComponentsBuilder.fromUriString(referent).build().getPath();
		boolean cheminLocal = chemin != null && chemin.startsWith("/") && !chemin.startsWith("//");
		return cheminLocal ? chemin : PAGE_PAR_DEFAUT;
	}
}
