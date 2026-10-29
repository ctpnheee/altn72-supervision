package fr.efrei.altn72.supervision.exception;

/** Un changement d'état contraire aux règles de gestion (par exemple réaffecter un incident résolu). Traduite en 409. */
public class TransitionInterditeException extends RuntimeException {

	public TransitionInterditeException(String message) {
		super(message);
	}
}
