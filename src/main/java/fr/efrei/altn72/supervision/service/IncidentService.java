package fr.efrei.altn72.supervision.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.config.AutorisationsIncident;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.exception.TransitionInterditeException;
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
import fr.efrei.altn72.supervision.repository.EvenementIncidentRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

/** Cycle de vie des incidents : déclaration (F4), détail (F5), description (F6), affectation (F7) et résolution (F8). */
@Service
public class IncidentService {

	static final String MODIFICATION_REFUSEE = "Vous ne pouvez pas modifier la description de cet incident.";

	static final String REAFFECTATION_REFUSEE = "Un incident résolu ne peut plus être réaffecté.";

	static final String DEJA_RESOLU = "Cet incident est déjà résolu.";

	static final String RESOLUTION_AVANT_OUVERTURE = "La date de résolution ne peut pas précéder la date d'ouverture.";

	static final String CONSIGNE_PILOTAGE = "Seul le responsable peut affecter un prestataire ou résoudre l'incident.";

	static final String CONSIGNE_AGENT = "Vous pouvez corriger la description tant que personne n'a pris l'incident en charge.";

	static final String CONSIGNE_RESPONSABLE = "Vous pouvez corriger la description tant que l'incident n'est pas résolu.";

	static final String DEPUIS_OUVERTURE = "Depuis l'ouverture";

	static final String ENTRE_OUVERTURE_ET_RESOLUTION = "Entre l'ouverture et la résolution";

	static final String LIBELLE_AFFECTER = "Affecter le prestataire";

	static final String LIBELLE_REAFFECTER = "Réaffecter le prestataire";

	private final Clock clock;
	private final IncidentRepository incidentRepository;
	private final EquipementRepository equipementRepository;
	private final UtilisateurRepository utilisateurRepository;
	private final EvaluationRepository evaluationRepository;
	private final PrestataireRepository prestataireRepository;
	private final EvenementIncidentRepository evenementRepository;
	private final NiveauAttentionCalculator niveauAttentionCalculator;
	private final AutorisationsIncident autorisationsIncident;
	private final ApplicationEventPublisher evenements;

	public IncidentService(Clock clock, IncidentRepository incidentRepository, EquipementRepository equipementRepository,
			UtilisateurRepository utilisateurRepository, EvaluationRepository evaluationRepository,
			PrestataireRepository prestataireRepository, EvenementIncidentRepository evenementRepository,
			NiveauAttentionCalculator niveauAttentionCalculator, AutorisationsIncident autorisationsIncident,
			ApplicationEventPublisher evenements) {
		this.clock = clock;
		this.incidentRepository = incidentRepository;
		this.equipementRepository = equipementRepository;
		this.utilisateurRepository = utilisateurRepository;
		this.evaluationRepository = evaluationRepository;
		this.prestataireRepository = prestataireRepository;
		this.evenementRepository = evenementRepository;
		this.niveauAttentionCalculator = niveauAttentionCalculator;
		this.autorisationsIncident = autorisationsIncident;
		this.evenements = evenements;
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
			String consigneModification, boolean peutAffecterOuResoudre, String consignePilotage,
			String libelleAffectation, List<PrestataireChoix> prestataires, List<EvenementLigne> evenements, Long prestataireId, Double prestataireNoteMoyenne) {
	}

	/** Une ligne du journal déjà prête pour la page : la vue n'ordonne pas les dates. */
	public record EvenementLigne(LocalDateTime date, String auteur, String action) {
	}

	/** Prestataire proposé dans la liste d'affectation. La vue n'a pas besoin de l'entité. */
	public record PrestataireChoix(Long id, String nom) {
	}

	/**
	 * Incident tel que l'API le renvoie. Les deux profils voient les mêmes champs :
	 * le droit d'écrire est porté par {@code @PreAuthorize}, pas par le JSON.
	 */
	public record IncidentResume(Long id, String description, LocalDateTime dateOuverture, LocalDateTime dateResolution,
			StatutIncident statut, String dureeOuverture, String precisionDuree, NiveauAttention niveau,
			Long equipementId, String equipementReference, String equipementLibelle, String stationNom,
			String declarantPrenom, Long prestataireId, String prestataireNom, Integer note,
			String commentaireEvaluation, LocalDateTime dateEvaluation) {
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
				.orElseThrow(() -> RessourceIntrouvableException.pour("Équipement", equipementId));
		Utilisateur declarant = utilisateurRepository.findByLogin(loginDeclarant)
				.orElseThrow(() -> RessourceIntrouvableException.pour("Utilisateur", loginDeclarant));

		LocalDateTime ouverture = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		Incident incident = new Incident(description.trim(), ouverture, equipement, declarant);
		equipement.setEtat(EtatEquipement.EN_PANNE);
		equipementRepository.save(equipement);
		Incident enregistre = incidentRepository.save(incident);
		publier(enregistre.getId(), loginDeclarant, TextesJournal.DECLARATION);
		return enregistre;
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
		List<EvenementLigne> evenements = evenementRepository.findByIncidentIdOrderByDateAscIdAsc(id).stream()
				.map(evenement -> new EvenementLigne(evenement.getDate(), evenement.getAuteur(), evenement.getAction()))
				.toList();
		return versDetail(incident, evaluation, modifiable, consigne(login, modifiable), pilotage(incident, login),
				evenements);
	}

	/** Incidents du plus récent au plus ancien, pour {@code GET /api/incidents}. */
	@Transactional(readOnly = true)
	public List<IncidentResume> lister() {
		List<Incident> incidents = incidentRepository.findAllDetails();
		Map<Long, Evaluation> evaluations = evaluationsDes(incidents);
		return incidents.stream()
				.map(incident -> versResume(incident, evaluations.get(incident.getId())))
				.toList();
	}

	/** Un incident pour {@code GET /api/incidents/{id}}. Même durée que la page, sans les drapeaux d'affichage. */
	@Transactional(readOnly = true)
	public IncidentResume lire(Long id) {
		Incident incident = charger(id);
		return versResume(incident, evaluationRepository.findByIncidentId(id).orElse(null));
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

	/**
	 * Affecte ou réaffecte un prestataire (F7). Un incident résolu ne change plus de prestataire (RG5).
	 * La transaction relie la lecture du statut et l'écriture : {@code open-in-view} est désactivé,
	 * donc sans elle l'incident serait détaché et {@code save} réécrirait toute la ligne, y compris
	 * une résolution enregistrée entre-temps.
	 */
	@Transactional
	@PreAuthorize("hasRole('RESPONSABLE')")
	public Incident affecter(Long incidentId, Long prestataireId, String login) {
		Incident incident = charger(incidentId);
		if (incident.getStatut() == StatutIncident.RESOLU) {
			throw new TransitionInterditeException(REAFFECTATION_REFUSEE);
		}
		String ancienNom = incident.getPrestataire() == null ? null : incident.getPrestataire().getNom();
		Prestataire prestataire = prestataireRepository.findById(prestataireId)
				.orElseThrow(() -> RessourceIntrouvableException.pour("Prestataire", prestataireId));
		incident.setPrestataire(prestataire);
		incident.setStatut(StatutIncident.AFFECTE);
		Incident enregistre = incidentRepository.save(incident);
		String action = ancienNom == null ? TextesJournal.affectation(prestataire.getNom())
				: TextesJournal.reaffectation(ancienNom, prestataire.getNom());
		publier(enregistre.getId(), login, action);
		return enregistre;
	}

	/**
	 * Refuse avant de réafficher le formulaire (F7, F8). Sans cet appel, un agent qui envoie
	 * un prestataire vide obtiendrait le formulaire au lieu d'un refus.
	 */
	@PreAuthorize("hasRole('RESPONSABLE')")
	public void controlerDroitPilotage() {
		// Le refus est porté par l'annotation : le profil est vérifié avant d'entrer ici.
	}

	/**
	 * Résout l'incident à l'instant de l'horloge (F8, RG5). Si c'était le dernier incident ouvert
	 * de l'équipement, celui-ci repasse en service. Les deux écritures partagent la transaction (RG10) :
	 * sans elle, l'équipement peut rester en panne après une résolution réussie.
	 */
	@Transactional
	@PreAuthorize("hasRole('RESPONSABLE')")
	public Incident resoudre(Long incidentId, String login) {
		Incident incident = charger(incidentId);
		if (incident.getStatut() == StatutIncident.RESOLU) {
			throw new TransitionInterditeException(DEJA_RESOLU);
		}
		LocalDateTime resolution = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
		if (resolution.isBefore(incident.getDateOuverture())) {
			throw new TransitionInterditeException(RESOLUTION_AVANT_OUVERTURE);
		}
		Equipement equipement = incident.getEquipement();
		long encoreOuverts = incidentRepository.countByEquipementIdAndStatutNot(equipement.getId(),
				StatutIncident.RESOLU);
		if (encoreOuverts <= 1) {
			equipement.setEtat(EtatEquipement.EN_SERVICE);
			equipementRepository.save(equipement);
		}
		incident.setStatut(StatutIncident.RESOLU);
		incident.setDateResolution(resolution);
		Incident enregistre = incidentRepository.save(incident);
		publier(enregistre.getId(), login, TextesJournal.RESOLUTION);
		return enregistre;
	}

	private void publier(Long incidentId, String login, String action) {
		evenements.publishEvent(new EvenementIncidentDemande(incidentId, login, action));
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
				.orElseThrow(() -> RessourceIntrouvableException.pour("Incident", id));
	}

	private String consigne(String login, boolean modifiable) {
		if (!modifiable) {
			return null;
		}
		Utilisateur utilisateur = utilisateurRepository.findByLogin(login).orElse(null);
		if (utilisateur != null && utilisateur.getProfil() == Profil.AGENT) {
			return CONSIGNE_AGENT;
		}
		return CONSIGNE_RESPONSABLE;
	}

	/**
	 * Décide l'affichage des formulaires d'affectation et de résolution. La vue ne compare pas le statut :
	 * elle reçoit un drapeau, le libellé du bouton et la liste des prestataires.
	 */
	private Pilotage pilotage(Incident incident, String login) {
		if (incident.getStatut() == StatutIncident.RESOLU) {
			return new Pilotage(false, null, null, List.of());
		}
		boolean responsable = utilisateurRepository.findByLogin(login)
				.map(utilisateur -> utilisateur.getProfil() == Profil.RESPONSABLE)
				.orElse(false);
		if (!responsable) {
			return new Pilotage(false, CONSIGNE_PILOTAGE, null, List.of());
		}
		List<PrestataireChoix> prestataires = prestataireRepository.findAllByOrderByNomAsc().stream()
				.map(prestataire -> new PrestataireChoix(prestataire.getId(), prestataire.getNom()))
				.toList();
		String libelle = incident.getPrestataire() == null ? LIBELLE_AFFECTER : LIBELLE_REAFFECTER;
		return new Pilotage(true, null, libelle, prestataires);
	}

	private DetailIncident versDetail(Incident incident, Evaluation evaluation, boolean modifiable, String consigne,
			Pilotage pilotage, List<EvenementLigne> evenements) {
		boolean resolu = incident.getStatut() == StatutIncident.RESOLU;
		LocalDateTime fin = finDeDuree(incident);
		Prestataire prestataire = incident.getPrestataire();
		Equipement equipement = incident.getEquipement();
		return new DetailIncident(incident.getId(), incident.getDescription(), incident.getDateOuverture(),
				incident.getDateResolution(), incident.getStatut(), formaterDuree(incident.getDateOuverture(), fin),
				precisionDuree(resolu),
				resolu ? null : niveauAttentionCalculator.calculer(incident), equipement.getId(),
				equipement.getReference(), equipement.getLibelle(), equipement.getStation().getNom(),
				incident.getDeclarant().getPrenom(), prestataire == null ? null : prestataire.getNom(),
				prestataire == null ? null : prestataire.getEmail(),
				prestataire == null ? null : prestataire.getTelephone(),
				evaluation == null ? null : evaluation.getNote(),
				evaluation == null ? null : evaluation.getCommentaire(),
				evaluation == null ? null : evaluation.getDate(), modifiable, consigne, pilotage.autorise(),
				pilotage.consigne(), pilotage.libelleAffectation(), pilotage.prestataires(), evenements, prestataire == null ? null : prestataire.getId(),
				prestataire == null
						? null
						: evaluationRepository.noteMoyenneDuPrestataire(
						prestataire.getId()));
	}

	private record Pilotage(boolean autorise, String consigne, String libelleAffectation,
			List<PrestataireChoix> prestataires) {
	}

	private IncidentResume versResume(Incident incident, Evaluation evaluation) {
		boolean resolu = incident.getStatut() == StatutIncident.RESOLU;
		Prestataire prestataire = incident.getPrestataire();
		Equipement equipement = incident.getEquipement();
		return new IncidentResume(incident.getId(), incident.getDescription(), incident.getDateOuverture(),
				incident.getDateResolution(), incident.getStatut(),
				formaterDuree(incident.getDateOuverture(), finDeDuree(incident)),
				precisionDuree(resolu),
				resolu ? null : niveauAttentionCalculator.calculer(incident), equipement.getId(),
				equipement.getReference(), equipement.getLibelle(), equipement.getStation().getNom(),
				incident.getDeclarant().getPrenom(), prestataire == null ? null : prestataire.getId(),
				prestataire == null ? null : prestataire.getNom(), evaluation == null ? null : evaluation.getNote(),
				evaluation == null ? null : evaluation.getCommentaire(),
				evaluation == null ? null : evaluation.getDate());
	}

	private static String precisionDuree(boolean resolu) {
		return resolu ? ENTRE_OUVERTURE_ET_RESOLUTION : DEPUIS_OUVERTURE;
	}

	/** Incident ouvert : jusqu'à maintenant. Incident résolu : jusqu'à sa date de résolution. */
	private LocalDateTime finDeDuree(Incident incident) {
		if (incident.getStatut() == StatutIncident.RESOLU && incident.getDateResolution() != null) {
			return incident.getDateResolution();
		}
		return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
	}

	private Map<Long, Evaluation> evaluationsDes(List<Incident> incidents) {
		List<Long> ids = incidents.stream().map(Incident::getId).filter(id -> id != null).toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return evaluationRepository.findByIncidentIdIn(ids).stream()
				.collect(Collectors.toMap(evaluation -> evaluation.getIncident().getId(), Function.identity()));
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
