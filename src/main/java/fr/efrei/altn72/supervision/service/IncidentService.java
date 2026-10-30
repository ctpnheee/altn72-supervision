package fr.efrei.altn72.supervision.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

/** Cycle de vie des incidents. La déclaration (F4) est la première transition. */
@Service
public class IncidentService {

	private final Clock clock;
	private final IncidentRepository incidentRepository;
	private final EquipementRepository equipementRepository;
	private final UtilisateurRepository utilisateurRepository;

	public IncidentService(Clock clock, IncidentRepository incidentRepository, EquipementRepository equipementRepository,
			UtilisateurRepository utilisateurRepository) {
		this.clock = clock;
		this.incidentRepository = incidentRepository;
		this.equipementRepository = equipementRepository;
		this.utilisateurRepository = utilisateurRepository;
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
}
