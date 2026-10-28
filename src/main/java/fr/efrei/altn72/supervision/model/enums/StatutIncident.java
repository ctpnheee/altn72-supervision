package fr.efrei.altn72.supervision.model.enums;

/**
 * Les trois statuts possibles d'un incident (RG2).
 * Le niveau d'attention (vert, orange, rouge) n'est pas un statut : il est calculé à la lecture.
 */
public enum StatutIncident {
	NON_AFFECTE,
	AFFECTE,
	RESOLU
}
