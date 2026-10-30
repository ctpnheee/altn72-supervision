
package fr.efrei.altn72.supervision.model.enums;

/**
 * Indicateur visuel d'un incident actif.
 *
 * Il est calculé à la lecture et n'est pas stocké en base.
 */
public enum NiveauAttention {
    VERT,
    ORANGE,
    ROUGE,
    PRIS_EN_CHARGE
}
