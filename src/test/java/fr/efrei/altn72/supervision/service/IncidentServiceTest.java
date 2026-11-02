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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.config.AutorisationsIncident;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Evaluation;
import fr.efrei.altn72.supervision.model.Incident;
import fr.efrei.altn72.supervision.model.Prestataire;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.NiveauAttention;
import fr.efrei.altn72.supervision.model.enums.Profil;
import fr.efrei.altn72.supervision.model.enums.StatutIncident;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;
import fr.efrei.altn72.supervision.repository.EquipementRepository;
import fr.efrei.altn72.supervision.repository.EvaluationRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;
import fr.efrei.altn72.supervision.service.IncidentService.DetailIncident;

class IncidentServiceTest {

	private static final Instant INSTANT = Instant.parse("2026-10-30T12:00:00Z");

	private IncidentRepository incidentRepository;
	private EquipementRepository equipementRepository;
	private UtilisateurRepository utilisateurRepository;
	private EvaluationRepository evaluationRepository;
	private AutorisationsIncident autorisationsIncident;
	private IncidentService service;

	private Equipement equipement;
	private Utilisateur declarant;

	@BeforeEach
	void preparer() {
		incidentRepository = mock(IncidentRepository.class);
		equipementRepository = mock(EquipementRepository.class);
		utilisateurRepository = mock(UtilisateurRepository.class);
		evaluationRepository = mock(EvaluationRepository.class);
		autorisationsIncident = mock(AutorisationsIncident.class);
		Clock clock = Clock.fixed(INSTANT, ZoneOffset.UTC);
		service = new IncidentService(clock, incidentRepository, equipementRepository, utilisateurRepository,
				evaluationRepository, new NiveauAttentionCalculator(clock), autorisationsIncident);

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
	void modifierDescriptionEstTransactionnelEtProtegeParLeProfil() throws NoSuchMethodException {
		Method modifier = IncidentService.class.getMethod("modifierDescription", Long.class, String.class, String.class);

		assertThat(modifier.isAnnotationPresent(Transactional.class)).isTrue();
		assertThat(modifier.getAnnotation(PreAuthorize.class).value()).contains("autorisationsIncident.peutModifierDescription");
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

	@Test
	void leDetailDUnIncidentOuvertCalculeLaDureeAvecLHorloge() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(47));
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(evaluationRepository.findByIncidentId(4L)).thenReturn(Optional.empty());
		when(autorisationsIncident.peutModifier(incident, "alice")).thenReturn(true);

		DetailIncident detail = service.consulter(4L, "alice");

		assertThat(detail.dureeOuverture()).isEqualTo("47 h");
		assertThat(detail.precisionDuree()).isEqualTo("Depuis l'ouverture");
		assertThat(detail.niveau()).isEqualTo(NiveauAttention.VERT);
		assertThat(detail.prestataireNom()).isNull();
		assertThat(detail.note()).isNull();
		assertThat(detail.descriptionModifiable()).isTrue();
		assertThat(detail.consigneModification()).contains("pris l'incident en charge");
		assertThat(detail.stationNom()).isEqualTo("Châtelet");
	}

	@Test
	void leDetailDUnIncidentAffecteMontreLePrestataireEtLeNiveauPrisEnCharge() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(72));
		incident.setStatut(StatutIncident.AFFECTE);
		incident.setPrestataire(new Prestataire("Otis", "otis@example.org", "0140000001"));
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(evaluationRepository.findByIncidentId(4L)).thenReturn(Optional.empty());
		when(autorisationsIncident.peutModifier(incident, "alice")).thenReturn(false);

		DetailIncident detail = service.consulter(4L, "alice");

		assertThat(detail.dureeOuverture()).isEqualTo("72 h");
		assertThat(detail.niveau()).isEqualTo(NiveauAttention.PRIS_EN_CHARGE);
		assertThat(detail.prestataireNom()).isEqualTo("Otis");
		assertThat(detail.prestataireEmail()).isEqualTo("otis@example.org");
		assertThat(detail.descriptionModifiable()).isFalse();
		assertThat(detail.consigneModification()).isNull();
	}

	@Test
	void leDetailDUnIncidentResoluMesureLeTempsJusquaLaResolution() {
		LocalDateTime ouverture = LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC).minusHours(10).minusMinutes(30);
		LocalDateTime resolution = LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC).minusHours(1);
		Incident incident = new Incident("Porte bloquée", ouverture, equipement, declarant);
		incident.setStatut(StatutIncident.RESOLU);
		incident.setDateResolution(resolution);
		incident.setPrestataire(new Prestataire("Otis", "otis@example.org", "0140000001"));
		Evaluation evaluation = new Evaluation(incident, 5, "Intervention rapide", resolution);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(evaluationRepository.findByIncidentId(4L)).thenReturn(Optional.of(evaluation));
		when(autorisationsIncident.peutModifier(incident, "alice")).thenReturn(false);

		DetailIncident detail = service.consulter(4L, "alice");

		assertThat(detail.dureeOuverture()).isEqualTo("9 h 30 min");
		assertThat(detail.precisionDuree()).isEqualTo("Entre l'ouverture et la résolution");
		assertThat(detail.niveau()).isNull();
		assertThat(detail.note()).isEqualTo(5);
		assertThat(detail.commentaireEvaluation()).isEqualTo("Intervention rapide");
		assertThat(detail.descriptionModifiable()).isFalse();
	}

	@Test
	void unIncidentInconnuNEstPasConsulte() {
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.consulter(4L, "alice"))
				.isInstanceOf(RessourceIntrouvableException.class)
				.hasMessage("Incident 4 introuvable.");
	}

	@Test
	void modifierLaDescriptionEnregistreLeTexteEpure() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(autorisationsIncident.peutModifier(incident, "alice")).thenReturn(true);

		service.modifierDescription(4L, "  Cabine arrêtée  ", "alice");

		assertThat(incident.getDescription()).isEqualTo("Cabine arrêtée");
		verify(incidentRepository).save(incident);
	}

	@Test
	void unAgentNePeutPasModifierUnIncidentQuiLuiEstRefuse() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		incident.setStatut(StatutIncident.AFFECTE);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(autorisationsIncident.peutModifier(incident, "alice")).thenReturn(false);

		assertThatThrownBy(() -> service.modifierDescription(4L, "Cabine arrêtée", "alice"))
				.isInstanceOf(AuthorizationDeniedException.class)
				.hasMessage(IncidentService.MODIFICATION_REFUSEE);
		verify(incidentRepository, never()).save(any());
		assertThat(incident.getDescription()).isEqualTo("Porte bloquée");
	}

	private Incident incidentOuvertDepuis(Duration duree) {
		LocalDateTime ouverture = LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC).minus(duree);
		return new Incident("Porte bloquée", ouverture, equipement, declarant);
	}
}
