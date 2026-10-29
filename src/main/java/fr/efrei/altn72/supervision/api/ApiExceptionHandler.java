package fr.efrei.altn72.supervision.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import fr.efrei.altn72.supervision.exception.EvaluationDejaSaisieException;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.exception.TransitionInterditeException;

/**
 * Traduit les exceptions métier en réponses JSON {@link ProblemDetail} pour les contrôleurs REST.
 * Les services ignorent HTTP : ils lèvent une exception, ce gestionnaire choisit le code.
 * Seul le message en français est renvoyé, jamais de trace technique.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

	@ExceptionHandler(RessourceIntrouvableException.class)
	ProblemDetail ressourceIntrouvable(RessourceIntrouvableException exception) {
		return problemeDetail(HttpStatus.NOT_FOUND, "Ressource introuvable", exception.getMessage());
	}

	@ExceptionHandler({ TransitionInterditeException.class, EvaluationDejaSaisieException.class })
	ProblemDetail conflit(RuntimeException exception) {
		return problemeDetail(HttpStatus.CONFLICT, "Action impossible", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail donneesInvalides(MethodArgumentNotValidException exception) {
		ProblemDetail probleme = problemeDetail(HttpStatus.BAD_REQUEST, "Données invalides",
				"Les données envoyées sont invalides.");
		Map<String, String> erreurs = new LinkedHashMap<>();
		for (FieldError erreur : exception.getBindingResult().getFieldErrors()) {
			erreurs.putIfAbsent(erreur.getField(), erreur.getDefaultMessage());
		}
		probleme.setProperty("erreurs", erreurs);
		return probleme;
	}

	private static ProblemDetail problemeDetail(HttpStatus statut, String titre, String detail) {
		ProblemDetail probleme = ProblemDetail.forStatusAndDetail(statut, detail);
		probleme.setTitle(titre);
		return probleme;
	}
}
