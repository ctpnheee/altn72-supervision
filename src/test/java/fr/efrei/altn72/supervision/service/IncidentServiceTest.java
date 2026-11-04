package fr.efrei.altn72.supervision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.altn72.supervision.config.AutorisationsIncident;
import fr.efrei.altn72.supervision.exception.RessourceIntrouvableException;
import fr.efrei.altn72.supervision.exception.TransitionInterditeException;
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
import fr.efrei.altn72.supervision.repository.EvenementIncidentRepository;
import fr.efrei.altn72.supervision.repository.IncidentRepository;
import fr.efrei.altn72.supervision.repository.PrestataireRepository;
import fr.efrei.altn72.supervision.repository.UtilisateurRepository;
import fr.efrei.altn72.supervision.service.IncidentService.DetailIncident;
import fr.efrei.altn72.supervision.service.IncidentService.IncidentResume;
import fr.efrei.altn72.supervision.service.IncidentService.PrestataireChoix;

class IncidentServiceTest {

	private static final Instant INSTANT = Instant.parse("2026-10-30T12:00:00Z");

	private IncidentRepository incidentRepository;
	private EquipementRepository equipementRepository;
	private UtilisateurRepository utilisateurRepository;
	private EvaluationRepository evaluationRepository;
	private PrestataireRepository prestataireRepository;
	private EvenementIncidentRepository evenementRepository;
	private ApplicationEventPublisher evenements;
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
		prestataireRepository = mock(PrestataireRepository.class);
		evenementRepository = mock(EvenementIncidentRepository.class);
		evenements = mock(ApplicationEventPublisher.class);
		autorisationsIncident = mock(AutorisationsIncident.class);
		Clock clock = Clock.fixed(INSTANT, ZoneOffset.UTC);
		service = new IncidentService(clock, incidentRepository, equipementRepository, utilisateurRepository,
				evaluationRepository, prestataireRepository, evenementRepository, new NiveauAttentionCalculator(clock),
				autorisationsIncident, evenements);
		when(evenementRepository.findByIncidentIdOrderByDateAscIdAsc(any())).thenReturn(List.of());

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
		assertThat(detail.precisionDuree()).isEqualTo(IncidentService.DEPUIS_OUVERTURE);
		assertThat(detail.niveau()).isEqualTo(NiveauAttention.VERT);
		assertThat(detail.prestataireNom()).isNull();
		assertThat(detail.note()).isNull();
		assertThat(detail.descriptionModifiable()).isTrue();
		assertThat(detail.consigneModification()).contains("pris l'incident en charge");
		assertThat(detail.stationNom()).isEqualTo("Châtelet");
		assertThat(detail.peutAffecterOuResoudre()).isFalse();
		assertThat(detail.consignePilotage()).contains("Seul le responsable");
		assertThat(detail.prestataires()).isEmpty();
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
		assertThat(detail.peutAffecterOuResoudre()).isFalse();
		assertThat(detail.libelleAffectation()).isNull();
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
		assertThat(detail.precisionDuree()).isEqualTo(IncidentService.ENTRE_OUVERTURE_ET_RESOLUTION);
		assertThat(detail.niveau()).isNull();
		assertThat(detail.note()).isEqualTo(5);
		assertThat(detail.commentaireEvaluation()).isEqualTo("Intervention rapide");
		assertThat(detail.descriptionModifiable()).isFalse();
		assertThat(detail.peutAffecterOuResoudre()).isFalse();
		assertThat(detail.consignePilotage()).isNull();
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

	@Test
	void leResponsableVoitLAffectationTantQueLIncidentNEstPasResolu() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(evaluationRepository.findByIncidentId(4L)).thenReturn(Optional.empty());
		when(autorisationsIncident.peutModifier(incident, "alice")).thenReturn(true);
		when(utilisateurRepository.findByLogin("alice"))
				.thenReturn(Optional.of(new Utilisateur("alice", "hash", "Alice", Profil.RESPONSABLE)));
		when(prestataireRepository.findAllByOrderByNomAsc())
				.thenReturn(List.of(new Prestataire("Otis", "otis@example.org", "0140000001")));

		DetailIncident detail = service.consulter(4L, "alice");

		assertThat(detail.peutAffecterOuResoudre()).isTrue();
		assertThat(detail.libelleAffectation()).isEqualTo(IncidentService.LIBELLE_AFFECTER);
		assertThat(detail.consignePilotage()).isNull();
		assertThat(detail.prestataires()).extracting(PrestataireChoix::nom).containsExactly("Otis");
	}

	@Test
	void unIncidentDejaAffecteProposeLaReaffectation() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		incident.setStatut(StatutIncident.AFFECTE);
		incident.setPrestataire(new Prestataire("Otis", "otis@example.org", "0140000001"));
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(evaluationRepository.findByIncidentId(4L)).thenReturn(Optional.empty());
		when(autorisationsIncident.peutModifier(incident, "alice")).thenReturn(true);
		when(utilisateurRepository.findByLogin("alice"))
				.thenReturn(Optional.of(new Utilisateur("alice", "hash", "Alice", Profil.RESPONSABLE)));
		when(prestataireRepository.findAllByOrderByNomAsc()).thenReturn(List.of());

		DetailIncident detail = service.consulter(4L, "alice");

		assertThat(detail.peutAffecterOuResoudre()).isTrue();
		assertThat(detail.libelleAffectation()).isEqualTo(IncidentService.LIBELLE_REAFFECTER);
	}

	@Test
	void affecterEtResoudreSontTransactionnelsEtReservesAuResponsable() throws NoSuchMethodException {
		Method affecter = IncidentService.class.getMethod("affecter", Long.class, Long.class, String.class);
		Method resoudre = IncidentService.class.getMethod("resoudre", Long.class, String.class);
		Method controle = IncidentService.class.getMethod("controlerDroitPilotage");

		assertThat(affecter.isAnnotationPresent(Transactional.class)).isTrue();
		assertThat(affecter.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('RESPONSABLE')");
		assertThat(resoudre.isAnnotationPresent(Transactional.class)).isTrue();
		assertThat(resoudre.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('RESPONSABLE')");
		assertThat(controle.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('RESPONSABLE')");
	}

	@Test
	void affecterRattacheLePrestataireEtPasseLIncidentAAffecte() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		Prestataire otis = new Prestataire("Otis", "otis@example.org", "0140000001");
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(prestataireRepository.findById(5L)).thenReturn(Optional.of(otis));

		Incident enregistre = service.affecter(4L, 5L, "alice");

		assertThat(enregistre.getStatut()).isEqualTo(StatutIncident.AFFECTE);
		assertThat(enregistre.getPrestataire()).isSameAs(otis);
		verify(incidentRepository).save(incident);
		ArgumentCaptor<EvenementIncidentDemande> demande = ArgumentCaptor.forClass(EvenementIncidentDemande.class);
		verify(evenements).publishEvent(demande.capture());
		assertThat(demande.getValue().login()).isEqualTo("alice");
		assertThat(demande.getValue().action()).isEqualTo(TextesJournal.affectation("Otis"));
	}

	@Test
	void reaffecterChangeLePrestataireSansSortirDeLEtatAffecte() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		incident.setStatut(StatutIncident.AFFECTE);
		incident.setPrestataire(new Prestataire("Otis", "otis@example.org", "0140000001"));
		Prestataire schindler = new Prestataire("Schindler", "schindler@example.org", "0140000002");
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(prestataireRepository.findById(6L)).thenReturn(Optional.of(schindler));

		service.affecter(4L, 6L, "alice");

		assertThat(incident.getStatut()).isEqualTo(StatutIncident.AFFECTE);
		assertThat(incident.getPrestataire()).isSameAs(schindler);
		ArgumentCaptor<EvenementIncidentDemande> demande = ArgumentCaptor.forClass(EvenementIncidentDemande.class);
		verify(evenements).publishEvent(demande.capture());
		assertThat(demande.getValue().action()).isEqualTo(TextesJournal.reaffectation("Otis", "Schindler"));
	}

	@Test
	void unIncidentResoluNePeutPlusEtreReaffecte() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(2));
		incident.setStatut(StatutIncident.RESOLU);
		incident.setDateResolution(LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC).minusHours(1));
		Prestataire otis = new Prestataire("Otis", "otis@example.org", "0140000001");
		incident.setPrestataire(otis);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));

		assertThatThrownBy(() -> service.affecter(4L, 6L, "alice"))
				.isInstanceOf(TransitionInterditeException.class)
				.hasMessage(IncidentService.REAFFECTATION_REFUSEE);
		verify(incidentRepository, never()).save(any());
		verify(prestataireRepository, never()).findById(any());
		verify(evenements, never()).publishEvent(any());
		assertThat(incident.getPrestataire()).isSameAs(otis);
	}

	@Test
	void unPrestataireInconnuNEstPasAffecte() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(prestataireRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.affecter(4L, 99L, "alice"))
				.isInstanceOf(RessourceIntrouvableException.class)
				.hasMessage("Prestataire 99 introuvable.");
		verify(incidentRepository, never()).save(any());
		assertThat(incident.getStatut()).isEqualTo(StatutIncident.NON_AFFECTE);
	}

	@Test
	void unIncidentInconnuNEstPasAffecte() {
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.affecter(4L, 5L, "alice"))
				.isInstanceOf(RessourceIntrouvableException.class)
				.hasMessage("Incident 4 introuvable.");
		verify(prestataireRepository, never()).findById(any());
	}

	@Test
	void resoudreDateLIncidentAvecLHorloge() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(2));
		equipement.setEtat(EtatEquipement.EN_PANNE);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(incidentRepository.countByEquipementIdAndStatutNot(nullable(Long.class), eq(StatutIncident.RESOLU)))
				.thenAnswer(invocation -> {
					assertThat(incident.getStatut()).isEqualTo(StatutIncident.NON_AFFECTE);
					return 1L;
				});

		Incident enregistre = service.resoudre(4L, "alice");

		assertThat(enregistre.getStatut()).isEqualTo(StatutIncident.RESOLU);
		assertThat(enregistre.getDateResolution()).isEqualTo(LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC));
		assertThat(enregistre.getPrestataire()).isNull();
		assertThat(equipement.getEtat()).isEqualTo(EtatEquipement.EN_SERVICE);
		verify(equipementRepository).save(equipement);
		ArgumentCaptor<EvenementIncidentDemande> demande = ArgumentCaptor.forClass(EvenementIncidentDemande.class);
		verify(evenements).publishEvent(demande.capture());
		assertThat(demande.getValue().login()).isEqualTo("alice");
		assertThat(demande.getValue().action()).isEqualTo(TextesJournal.RESOLUTION);
	}

	@Test
	void uneDateDeResolutionEgaleALOuvertureEstAcceptee() {
		LocalDateTime ouverture = LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC);
		Incident incident = new Incident("Porte bloquée", ouverture, equipement, declarant);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(incidentRepository.countByEquipementIdAndStatutNot(nullable(Long.class), eq(StatutIncident.RESOLU)))
				.thenReturn(1L);

		service.resoudre(4L, "alice");

		assertThat(incident.getDateResolution()).isEqualTo(ouverture);
		assertThat(incident.getStatut()).isEqualTo(StatutIncident.RESOLU);
	}

	@Test
	void uneResolutionAvantLOuvertureEstRefuseeEtNeChangeRien() {
		LocalDateTime ouverture = LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC).plusMinutes(1);
		Incident incident = new Incident("Porte bloquée", ouverture, equipement, declarant);
		equipement.setEtat(EtatEquipement.EN_PANNE);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));

		assertThatThrownBy(() -> service.resoudre(4L, "alice"))
				.isInstanceOf(TransitionInterditeException.class)
				.hasMessage(IncidentService.RESOLUTION_AVANT_OUVERTURE);
		assertThat(incident.getStatut()).isEqualTo(StatutIncident.NON_AFFECTE);
		assertThat(incident.getDateResolution()).isNull();
		assertThat(equipement.getEtat()).isEqualTo(EtatEquipement.EN_PANNE);
		verify(incidentRepository, never()).save(any());
		verify(equipementRepository, never()).save(any());
		verify(evenements, never()).publishEvent(any());
	}

	@Test
	void leDernierIncidentOuvertRemetLEquipementEnService() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		incident.setStatut(StatutIncident.AFFECTE);
		equipement.setEtat(EtatEquipement.EN_PANNE);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(incidentRepository.countByEquipementIdAndStatutNot(nullable(Long.class), eq(StatutIncident.RESOLU)))
				.thenReturn(1L);

		service.resoudre(4L, "alice");

		assertThat(equipement.getEtat()).isEqualTo(EtatEquipement.EN_SERVICE);
		verify(equipementRepository).save(equipement);
	}

	@Test
	void unAutreIncidentOuvertLaisseLEquipementEnPanne() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(1));
		equipement.setEtat(EtatEquipement.EN_PANNE);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(incidentRepository.countByEquipementIdAndStatutNot(nullable(Long.class), eq(StatutIncident.RESOLU)))
				.thenReturn(2L);

		service.resoudre(4L, "alice");

		assertThat(incident.getStatut()).isEqualTo(StatutIncident.RESOLU);
		assertThat(equipement.getEtat()).isEqualTo(EtatEquipement.EN_PANNE);
		verify(equipementRepository, never()).save(any());
	}

	@Test
	void unIncidentDejaResoluNePeutPasLEtreANouveau() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(2));
		LocalDateTime resolution = LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC).minusHours(1);
		incident.setStatut(StatutIncident.RESOLU);
		incident.setDateResolution(resolution);
		equipement.setEtat(EtatEquipement.EN_SERVICE);
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));

		assertThatThrownBy(() -> service.resoudre(4L, "alice"))
				.isInstanceOf(TransitionInterditeException.class)
				.hasMessage(IncidentService.DEJA_RESOLU);
		assertThat(incident.getDateResolution()).isEqualTo(resolution);
		assertThat(equipement.getEtat()).isEqualTo(EtatEquipement.EN_SERVICE);
		verify(incidentRepository, never()).save(any());
		verify(incidentRepository, never()).countByEquipementIdAndStatutNot(any(), any());
		verify(evenements, never()).publishEvent(any());
	}

	@Test
	void lireExposeLaMemeDureeQueLaPage() {
		Incident incident = incidentOuvertDepuis(Duration.ofHours(47));
		when(incidentRepository.findDetailParId(4L)).thenReturn(Optional.of(incident));
		when(evaluationRepository.findByIncidentId(4L)).thenReturn(Optional.empty());

		IncidentResume resume = service.lire(4L);

		assertThat(resume.dureeOuverture()).isEqualTo("47 h");
		assertThat(resume.precisionDuree()).isEqualTo(IncidentService.DEPUIS_OUVERTURE);
		assertThat(resume.niveau()).isEqualTo(NiveauAttention.VERT);
		assertThat(resume.prestataireId()).isNull();
		assertThat(resume.note()).isNull();
	}

	private Incident incidentOuvertDepuis(Duration duree) {
		LocalDateTime ouverture = LocalDateTime.ofInstant(INSTANT, ZoneOffset.UTC).minus(duree);
		return new Incident("Porte bloquée", ouverture, equipement, declarant);
	}
}
