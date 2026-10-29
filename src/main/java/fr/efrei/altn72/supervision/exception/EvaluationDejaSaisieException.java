package fr.efrei.altn72.supervision.exception;

/** L'intervention d'un incident a déjà été évaluée : une seule évaluation par incident (RG8). Traduite en 409. */
public class EvaluationDejaSaisieException extends RuntimeException {

	public EvaluationDejaSaisieException(String message) {
		super(message);
	}
}
