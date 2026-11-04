package fr.efrei.altn72.supervision.exception;

/** Une ressource demandée (incident, équipement, prestataire…) n'existe pas. Traduite en 404. */
public class RessourceIntrouvableException extends RuntimeException {

	public RessourceIntrouvableException(String message) {
		super(message);
	}

	/** Même phrase partout : « Incident 4 introuvable. » Une variante ferait diverger la page et l'API. */
	public static RessourceIntrouvableException pour(String type, Object identifiant) {
		return new RessourceIntrouvableException(type + " " + identifiant + " introuvable.");
	}
}
