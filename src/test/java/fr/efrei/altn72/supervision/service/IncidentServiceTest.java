package fr.efrei.altn72.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;

class IncidentServiceTest {

	private static final Instant INSTANT = Instant.parse("2026-10-30T12:00:00Z");

	private IncidentRepository incidentRepository;
	private EquipementRepository equipementRepository;
	private UtilisateurRepository utilisateurRepository;
	private IncidentService service;

	private Equipement equipement;
	private Utilisateur declarant;

	@BeforeEach
	void preparer() {
		incidentRepository = mock(IncidentRepository.class);
		equipementRepository = mock(EquipementRepository.class);
		utilisateurRepository = mock(UtilisateurRepository.class);
		Clock clock = Clock.fixed(INSTANT, ZoneOffset.UTC);
		service = new IncidentService(clock, incidentRepository, equipementRepository, utilisateurRepository);

		equipement = new Equipement("ASC-001", "Ascenseur quai A", TypeEquipement.ASCENSEUR, new Station("Châtelet"));
		declarant = new Utilisateur("alice", "hash", "Alice", Profil.AGENT);
		when(equipementRepository.findById(7L)).thenReturn(Optional.of(equipement));
		when(utilisateurRepository.findByLogin("alice")).thenReturn(Optional.of(declarant));
		when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void unIncidentDeclareDemarreSansPrestataireEtPasseLEquipementEnPanne() {
		Incident incident = service.declarer(7L, "  Porte bloquée  ", "alice");

		assertThat(incident.getStatut()).isEqualTo(StatutIncident.NON_AFFECTE);
		assertThat(incident.getPrestataire()).isNull();
		assertThat(incident.getDateResolution()).isNull();
		assertThat(incident.getDescription()).isEqualTo("Porte bloquée");
		assertThat(incident.getDeclarant()).isSameAs(declarant);
		assertThat(incident.getDateOuverture()).isEqualTo(LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC));
		assertThat(equipement.getEtat()).isEqualTo(EtatEquipement.EN_PANNE);
		verify(equipementRepository).save(equipement);
		verify(incidentRepository).save(incident);
	}

	@Test
	void declarerEstTransactionnel() throws NoSuchMethodException {
		Method declarer = IncidentService.class.getMethod("declarer", Long.class, String.class, String.class);

		assertThat(declarer.isAnnotationPresent(Transactional.class)).isTrue();
	}

	@Test
	void unEquipementInconnuNEstPasEnregistre() {
		when(equipementRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.declarer(99L, "Porte bloquée", "alice"))
				.isInstanceOf(RessourceIntrouvableException.class)
				.hasMessage("Équipement 99 introuvable.");
		verify(incidentRepository, never()).save(any());
	}

	@Test
	void unDeclarantInconnuNEstPasEnregistre() {
		when(utilisateurRepository.findByLogin("fantome")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.declarer(7L, "Porte bloquée", "fantome"))
				.isInstanceOf(RessourceIntrouvableException.class)
				.hasMessage("Utilisateur fantome introuvable.");
		verify(incidentRepository, never()).save(any());
		assertThat(equipement.getEtat()).isEqualTo(EtatEquipement.EN_SERVICE);
	}
}
