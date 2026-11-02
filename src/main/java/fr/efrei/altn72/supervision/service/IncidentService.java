package fr.efrei.altn72.supervision.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.config.AutorisationsIncident;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

/** Cycle de vie des incidents : déclaration (F4), détail (F5) et description (F6). */
@Service
public class IncidentService {

	static final String MODIFICATION_REFUSEE = "Vous ne pouvez pas modifier la description de cet incident.";

	private final Clock clock;
	private final IncidentRepository incidentRepository;
	private final EquipementRepository equipementRepository;
	private final UtilisateurRepository utilisateurRepository;
	private final EvaluationRepository evaluationRepository;
	private final NiveauAttentionCalculator niveauAttentionCalculator;
	private final AutorisationsIncident autorisationsIncident;

	public IncidentService(Clock clock, IncidentRepository incidentRepository, EquipementRepository equipementRepository,
			UtilisateurRepository utilisateurRepository, EvaluationRepository evaluationRepository,
			NiveauAttentionCalculator niveauAttentionCalculator, AutorisationsIncident autorisationsIncident) {
		this.clock = clock;
		this.incidentRepository = incidentRepository;
		this.equipementRepository = equipementRepository;
		this.utilisateurRepository = utilisateurRepository;
		this.evaluationRepository = evaluationRepository;
		this.niveauAttentionCalculator = niveauAttentionCalculator;
		this.autorisationsIncident = autorisationsIncident;
	}

	/**
	 * Fiche affichée. La durée et le droit de modifier sont déjà décidés : la vue ne compare
	 * ni un statut ni des dates.
	 */
	public record DetailIncident(Long id, String description, LocalDateTime dateOuverture, LocalDateTime dateResolution,
			StatutIncident statut, String dureeOuverture, String precisionDuree, NiveauAttention niveau,
			Long equipementId, String equipementReference, String equipementLibelle, String stationNom,
			String declarantPrenom, String prestataireNom, String prestataireEmail, String prestataireTelephone,
			Integer note, String commentaireEvaluation, LocalDateTime dateEvaluation, boolean descriptionModifiable,
			String consigneModification) {
	}

	/** Équipements proposés à la déclaration, station déjà chargée pour la vue. */
	public List<Equipement> listerEquipements() {
		return equipementRepository.findAllAvecStation();
	}

	/**
	 * Déclare un incident ouvert et sans prestataire (RG1), et passe l'équipement en panne.
	 * Les deux écritures partagent une transaction (RG10) : si l'enregistrement échoue, l'équipement
	 * reste dans son état précédent.
	 */
	@Transactional
	public Incident declarer(Long equipementId, String description, String loginDeclarant) {
		Equipement equipement = equipementRepository.findById(equipementId)
				.orElseThrow(() -> new RessourceIntrouvableException("Équipement " + equipementId + " introuvable."));
		Utilisateur declarant = utilisateurRepository.findByLogin(loginDeclarant)
				.orElseThrow(() -> new RessourceIntrouvableException("Utilisateur " + loginDeclarant + " introuvable."));

		LocalDateTime ouverture = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		Incident incident = new Incident(description.trim(), ouverture, equipement, declarant);
		equipement.setEtat(EtatEquipement.EN_PANNE);
		equipementRepository.save(equipement);
		return incidentRepository.save(incident);
	}

	/**
	 * Détail d'un incident (F5). La durée vient de l'horloge injectée : un incident ouvert
	 * se mesure jusqu'à maintenant, un incident résolu jusqu'à sa résolution.
	 * {@code open-in-view} est désactivé : la transaction garde l'incident, le prestataire
	 * et l'évaluation lisibles ensemble.
	 */
	@Transactional(readOnly = true)
	public DetailIncident consulter(Long id, String login) {
		Incident incident = charger(id);
		Evaluation evaluation = evaluationRepository.findByIncidentId(id).orElse(null);
		boolean modifiable = autorisationsIncident.peutModifier(incident, login);
		return versDetail(incident, evaluation, modifiable, consigne(login, modifiable));
	}

	/**
	 * Refuse avant de réafficher le formulaire (F6). Sans cet appel, un agent pourrait
	 * obtenir le formulaire en envoyant une description vide.
	 */
	@Transactional(readOnly = true)
	@PreAuthorize("@autorisationsIncident.peutModifierDescription(#id, authentication.name)")
	public void controlerDroitModification(Long id, String login) {
		incidentAutorise(id, login);
	}

	/**
	 * Corrige la description (F6). {@code @PreAuthorize} refuse l'appel avant d'entrer
	 * dans la méthode (RG11) : un filtre d'URL ne voit pas si cet incident est affecté.
	 * La transaction relit l'incident et écrit la description ensemble. Sans elle, une
	 * affectation intercalée laisserait enregistrer la correction d'un agent.
	 */
	@Transactional
	@PreAuthorize("@autorisationsIncident.peutModifierDescription(#id, authentication.name)")
	public void modifierDescription(Long id, String description, String login) {
		Incident incident = incidentAutorise(id, login);
		incident.setDescription(description.trim());
		incidentRepository.save(incident);
	}

	private Incident incidentAutorise(Long id, String login) {
		Incident incident = charger(id);
		if (!autorisationsIncident.peutModifier(incident, login)) {
			throw new AuthorizationDeniedException(MODIFICATION_REFUSEE);
		}
		return incident;
	}

	private Incident charger(Long id) {
		return incidentRepository.findDetailParId(id)
				.orElseThrow(() -> new RessourceIntrouvableException("Incident " + id + " introuvable."));
	}

	private String consigne(String login, boolean modifiable) {
		if (!modifiable) {
			return null;
		}
		Utilisateur utilisateur = utilisateurRepository.findByLogin(login).orElse(null);
		if (utilisateur != null && utilisateur.getProfil() == Profil.AGENT) {
			return "Vous pouvez corriger la description tant que personne n'a pris l'incident en charge.";
		}
		return "Vous pouvez corriger la description tant que l'incident n'est pas résolu.";
	}

	private DetailIncident versDetail(Incident incident, Evaluation evaluation, boolean modifiable, String consigne) {
		LocalDateTime maintenant = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		boolean resolu = incident.getStatut() == StatutIncident.RESOLU;
		LocalDateTime fin = resolu && incident.getDateResolution() != null ? incident.getDateResolution() : maintenant;
		Prestataire prestataire = incident.getPrestataire();
		Equipement equipement = incident.getEquipement();
		return new DetailIncident(incident.getId(), incident.getDescription(), incident.getDateOuverture(),
				incident.getDateResolution(), incident.getStatut(), formaterDuree(incident.getDateOuverture(), fin),
				resolu ? "Entre l'ouverture et la résolution" : "Depuis l'ouverture",
				resolu ? null : niveauAttentionCalculator.calculer(incident), equipement.getId(),
				equipement.getReference(), equipement.getLibelle(), equipement.getStation().getNom(),
				incident.getDeclarant().getPrenom(), prestataire == null ? null : prestataire.getNom(),
				prestataire == null ? null : prestataire.getEmail(),
				prestataire == null ? null : prestataire.getTelephone(),
				evaluation == null ? null : evaluation.getNote(),
				evaluation == null ? null : evaluation.getCommentaire(),
				evaluation == null ? null : evaluation.getDate(), modifiable, consigne);
	}

	private static String formaterDuree(LocalDateTime debut, LocalDateTime fin) {
		Duration duree = Duration.between(debut, fin);
		if (duree.isNegative()) {
			duree = Duration.ZERO;
		}
		long heures = duree.toHours();
		int minutes = duree.toMinutesPart();
		if (minutes == 0) {
			return heures + " h";
		}
		return heures + " h " + minutes + " min";
	}
}
