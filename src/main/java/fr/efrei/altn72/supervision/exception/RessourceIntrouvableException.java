package fr.efrei.altn72.supervision.exception;

/** Une ressource demandée (incident, équipement, prestataire…) n'existe pas. Traduite en 404. */
public class RessourceIntrouvableException extends RuntimeException {

	public RessourceIntrouvableException(String message) {
		super(message);
	}
}
