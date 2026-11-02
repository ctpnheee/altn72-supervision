package fr.efrei.altn72.supervision.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

class AutorisationsIncidentTest {

	private UtilisateurRepository utilisateurRepository;
	private IncidentRepository incidentRepository;
	private AutorisationsIncident autorisations;

	private Utilisateur agent;
	private Utilisateur responsable;
	private Equipement equipement;

	@BeforeEach
	void preparer() {
		utilisateurRepository = mock(UtilisateurRepository.class);
		incidentRepository = mock(IncidentRepository.class);
		autorisations = new AutorisationsIncident(incidentRepository, utilisateurRepository);
		agent = new Utilisateur("alice", "hache", "Alice", Profil.AGENT);
		responsable = new Utilisateur("robert", "hache", "Robert", Profil.RESPONSABLE);
		equipement = new Equipement("ASC-001", "Ascenseur", TypeEquipement.ASCENSEUR, new Station("Châtelet"));
		when(utilisateurRepository.findByLogin("alice")).thenReturn(Optional.of(agent));
		when(utilisateurRepository.findByLogin("robert")).thenReturn(Optional.of(responsable));
	}

	@Test
	void unAgentModifieSonIncidentNonAffecte() {
		assertThat(autorisations.peutModifier(incidentDe(agent), "alice")).isTrue();
	}

	@Test
	void unAgentNeModifiePasUnIncidentAffecte() {
		Incident incident = incidentDe(agent);
		incident.setStatut(StatutIncident.AFFECTE);

		assertThat(autorisations.peutModifier(incident, "alice")).isFalse();
	}

	@Test
	void unAgentNeModifiePasLIncidentDUnAutre() {
		assertThat(autorisations.peutModifier(incidentDe(responsable), "alice")).isFalse();
	}

	@Test
	void unAgentNeModifiePasUnIncidentResolu() {
		Incident incident = incidentDe(agent);
		incident.setStatut(StatutIncident.RESOLU);

		assertThat(autorisations.peutModifier(incident, "alice")).isFalse();
	}

	@Test
	void unResponsableModifieUnIncidentAffecte() {
		Incident incident = incidentDe(agent);
		incident.setStatut(StatutIncident.AFFECTE);

		assertThat(autorisations.peutModifier(incident, "robert")).isTrue();
	}

	@Test
	void unResponsableNeModifiePasUnIncidentResolu() {
		Incident incident = incidentDe(agent);
		incident.setStatut(StatutIncident.RESOLU);

		assertThat(autorisations.peutModifier(incident, "robert")).isFalse();
	}

	@Test
	void unUtilisateurInconnuNeModifieRien() {
		when(utilisateurRepository.findByLogin("fantome")).thenReturn(Optional.empty());

		assertThat(autorisations.peutModifier(incidentDe(agent), "fantome")).isFalse();
	}

	@Test
	void unIncidentInconnuLaisseLeServiceRepondre404() {
		when(incidentRepository.findDetailParId(9L)).thenReturn(Optional.empty());

		assertThat(autorisations.peutModifierDescription(9L, "alice")).isTrue();
	}

	private Incident incidentDe(Utilisateur declarant) {
		return new Incident("Porte bloquée", LocalDateTime.of(2026, 10, 30, 12, 0), equipement, declarant);
	}
}
