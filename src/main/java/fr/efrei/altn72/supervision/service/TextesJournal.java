package fr.efrei.altn72.supervision.service;

/** Libellés du journal métier, partagés par le service et les données de démonstration. */
public final class TextesJournal {

	public static final String DECLARATION = "Déclaration de l'incident";

	public static final String RESOLUTION = "Résolution de l'incident";

	private TextesJournal() {
	}

	public static String affectation(String prestataire) {
		return "Affectation au prestataire " + prestataire;
	}

	public static String reaffectation(String ancien, String nouveau) {
		return "Réaffectation du prestataire " + ancien + " vers " + nouveau;
	}
}
