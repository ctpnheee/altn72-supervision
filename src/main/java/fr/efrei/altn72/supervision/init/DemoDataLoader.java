package fr.efrei.altn72.supervision.init;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.StationRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

/**
 * Charge un jeu de données daté par rapport à l'horloge, pour que chaque couleur
 * du tableau de bord soit visible dès le démarrage. N'insère rien si la base
 * contient déjà des stations, sauf si {@code APP_DEMO_RESET=true}.
 */
@Component
public class DemoDataLoader implements ApplicationRunner {

	static final String LOGIN_RESPONSABLE = "responsable";
	static final String LOGIN_AGENT = "agent";
	static final String MOT_DE_PASSE_DEMO = "demo1234";

	private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

	private final Clock clock;
	private final PasswordEncoder passwordEncoder;
	private final StationRepository stationRepository;
	private final EquipementRepository equipementRepository;
	private final IncidentRepository incidentRepository;
	private final PrestataireRepository prestataireRepository;
	private final EvaluationRepository evaluationRepository;
	private final UtilisateurRepository utilisateurRepository;

	@Value("${app.demo.enabled:true}")
	private boolean enabled;

	@Value("${app.demo.reset:false}")
	private boolean reset;

	public DemoDataLoader(Clock clock, PasswordEncoder passwordEncoder, StationRepository stationRepository,
			EquipementRepository equipementRepository, IncidentRepository incidentRepository,
			PrestataireRepository prestataireRepository, EvaluationRepository evaluationRepository,
			UtilisateurRepository utilisateurRepository) {
		this.clock = clock;
		this.passwordEncoder = passwordEncoder;
		this.stationRepository = stationRepository;
		this.equipementRepository = equipementRepository;
		this.incidentRepository = incidentRepository;
		this.prestataireRepository = prestataireRepository;
		this.evaluationRepository = evaluationRepository;
		this.utilisateurRepository = utilisateurRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!enabled) {
			return;
		}
		executer(reset);
	}

	@Transactional
	void executer(boolean recreer) {
		if (recreer) {
			log.info("Recréation des données de démonstration (APP_DEMO_RESET).");
			vider();
		}
		Utilisateur responsable = assurerCompte(LOGIN_RESPONSABLE, "Alice", Profil.RESPONSABLE);
		Utilisateur agent = assurerCompte(LOGIN_AGENT, "Bruno", Profil.AGENT);
		if (!recreer && stationRepository.count() > 0) {
			log.info("Données de démonstration déjà présentes, chargement ignoré.");
			return;
		}
		charger(responsable, agent);
		log.info("Données de démonstration chargées : {} stations, {} incidents.",
				stationRepository.count(), incidentRepository.count());
	}

	private void vider() {
		evaluationRepository.deleteAll();
		incidentRepository.deleteAll();
		equipementRepository.deleteAll();
		stationRepository.deleteAll();
		prestataireRepository.deleteAll();
		utilisateurRepository.deleteAll();
		utilisateurRepository.flush();
	}

	private void charger(Utilisateur responsable, Utilisateur agent) {
		Station chatelet = stationRepository.save(new Station("Châtelet"));
		Station gareDeLyon = stationRepository.save(new Station("Gare de Lyon"));
		Station republique = stationRepository.save(new Station("République"));

		Prestataire otis = prestataireRepository.save(new Prestataire("Otis Maintenance", "otis@example.org", "0140000001"));
		Prestataire schindler = prestataireRepository
				.save(new Prestataire("Schindler Service", "schindler@example.org", "0140000002"));
		Prestataire kone = prestataireRepository.save(new Prestataire("Kone Interventions", "kone@example.org", "0140000003"));

		Equipement ascCht = equipement(chatelet, "ASC-CHT-01", "Ascenseur correspondance", TypeEquipement.ASCENSEUR);
		Equipement escCht = equipement(chatelet, "ESC-CHT-01", "Escalier mécanique ligne 1", TypeEquipement.ESCALIER_MECANIQUE);
		Equipement porCht = equipement(chatelet, "POR-CHT-01", "Portique d'entrée", TypeEquipement.PORTIQUE);

		Equipement disGdl = equipement(gareDeLyon, "DIS-GDL-01", "Distributeur de titres", TypeEquipement.DISTRIBUTEUR);
		equipement(gareDeLyon, "ECR-GDL-01", "Écran d'information hall", TypeEquipement.ECRAN_INFORMATION);
		Equipement ascGdl = equipement(gareDeLyon, "ASC-GDL-01", "Ascenseur quai A", TypeEquipement.ASCENSEUR);
		Equipement escGdl = equipement(gareDeLyon, "ESC-GDL-01", "Escalier mécanique hall", TypeEquipement.ESCALIER_MECANIQUE);

		Equipement escRep = equipement(republique, "ESC-REP-01", "Escalier mécanique ligne 5", TypeEquipement.ESCALIER_MECANIQUE);
		Equipement porRep = equipement(republique, "POR-REP-01", "Portique de sortie", TypeEquipement.PORTIQUE);
		Equipement disRep = equipement(republique, "DIS-REP-01", "Distributeur hall", TypeEquipement.DISTRIBUTEUR);
		Equipement ascRep = equipement(republique, "ASC-REP-01", "Ascenseur accès", TypeEquipement.ASCENSEUR);
		Equipement ecrRep = equipement(republique, "ECR-REP-01", "Écran quai", TypeEquipement.ECRAN_INFORMATION);

		// Non affectés : VERT (2 h, 47 h), ORANGE (48 h, 71 h), ROUGE (72 h, 100 h).
		declarer("Portique bloqué à l'entrée, voyageurs coincés.", 2, porCht, agent);
		declarer("Ascenseur à l'arrêt entre deux niveaux.", 47, ascCht, agent);
		declarer("Distributeur hors service, n'accepte plus les cartes.", 48, disGdl, agent);
		declarer("Escalier mécanique à l'arrêt, file sur les marches fixes.", 71, escRep, responsable);
		declarer("Ascenseur en panne depuis trois jours.", 72, ascGdl, agent);
		declarer("Portique hors service, file d'attente sur le quai.", 100, porRep, agent);

		// Affectés : PRIS_EN_CHARGE, quelle que soit l'ancienneté.
		affecter(declarer("Distributeur avale les billets.", 30, disRep, agent), otis);
		affecter(declarer("Escalier mécanique bruyant, à inspecter.", 12, escGdl, responsable), kone);

		// Résolus : dates de résolution distinctes ; un seul est évalué.
		Incident resoluEvalue = resoudre("Cabine bloquée, intervention terminée.", 240, 96, ascRep, agent, otis);
		resoudre("Main courante réparée.", 200, 24, escCht, agent, schindler);
		resoudre("Écran réinitialisé en interne.", 150, 72, ecrRep, responsable, null);
		evaluationRepository.save(new Evaluation(resoluEvalue, 5, "Intervention rapide et soignée.", ilYa(90)));
	}

	private Utilisateur assurerCompte(String login, String prenom, Profil profil) {
		return utilisateurRepository.findByLogin(login)
				.orElseGet(() -> utilisateurRepository.save(
						new Utilisateur(login, passwordEncoder.encode(MOT_DE_PASSE_DEMO), prenom, profil)));
	}

	private Equipement equipement(Station station, String reference, String libelle, TypeEquipement type) {
		return equipementRepository.save(new Equipement(reference, libelle, type, station));
	}

	private Incident declarer(String description, int heures, Equipement equipement, Utilisateur declarant) {
		Incident incident = incidentRepository
				.save(new Incident(description, ilYa(heures), equipement, declarant));
		equipement.setEtat(EtatEquipement.EN_PANNE);
		return incident;
	}

	private Incident affecter(Incident incident, Prestataire prestataire) {
		incident.setPrestataire(prestataire);
		incident.setStatut(StatutIncident.AFFECTE);
		return incidentRepository.save(incident);
	}

	private Incident resoudre(String description, int heuresOuverture, int heuresResolution, Equipement equipement,
			Utilisateur declarant, Prestataire prestataire) {
		Incident incident = new Incident(description, ilYa(heuresOuverture), equipement, declarant);
		incident.setStatut(StatutIncident.RESOLU);
		incident.setDateResolution(ilYa(heuresResolution));
		incident.setPrestataire(prestataire);
		return incidentRepository.save(incident);
	}

	private LocalDateTime ilYa(int heures) {
		return LocalDateTime.now(clock).minusHours(heures);
	}
}
