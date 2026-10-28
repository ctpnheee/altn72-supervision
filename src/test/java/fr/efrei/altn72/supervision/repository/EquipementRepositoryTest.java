package fr.efrei.altn72.supervision.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import fr.efrei.altn72.supervision.model.Equipement;
import fr.efrei.altn72.supervision.model.Station;
import fr.efrei.altn72.supervision.model.enums.EtatEquipement;
import fr.efrei.altn72.supervision.model.enums.TypeEquipement;

@DataJpaTest
class EquipementRepositoryTest {

	@Autowired
	private EquipementRepository equipementRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void deuxEquipementsDeMemeReferenceSontRefuses() {
		Station station = entityManager.persist(new Station("Châtelet"));
		equipementRepository.saveAndFlush(new Equipement("ASC-001", "Ascenseur quai A", TypeEquipement.ASCENSEUR, station));

		Equipement doublon = new Equipement("ASC-001", "Autre ascenseur", TypeEquipement.ASCENSEUR, station);

		assertThatThrownBy(() -> equipementRepository.saveAndFlush(doublon))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unNouvelEquipementEstEnService() {
		Station station = entityManager.persist(new Station("Châtelet"));

		Equipement equipement = equipementRepository
				.save(new Equipement("ASC-001", "Ascenseur quai A", TypeEquipement.ASCENSEUR, station));

		assertThat(equipement.getEtat()).isEqualTo(EtatEquipement.EN_SERVICE);
	}

	@Test
	void requeteDeriveeListeLesEquipementsDUneStationTriesParReference() {
		Station chatelet = entityManager.persist(new Station("Châtelet"));
		Station nation = entityManager.persist(new Station("Nation"));
		entityManager.persist(new Equipement("PRT-020", "Portique 20", TypeEquipement.PORTIQUE, chatelet));
		entityManager.persist(new Equipement("ASC-001", "Ascenseur quai A", TypeEquipement.ASCENSEUR, chatelet));
		entityManager.persist(new Equipement("ASC-900", "Ascenseur Nation", TypeEquipement.ASCENSEUR, nation));
		entityManager.flush();

		List<Equipement> equipements = equipementRepository.findByStationIdOrderByReferenceAsc(chatelet.getId());

		assertThat(equipements).extracting(Equipement::getReference).containsExactly("ASC-001", "PRT-020");
	}

	@Test
	void existsByReferenceDistingueUneReferenceConnueDUneInconnue() {
		Station station = entityManager.persist(new Station("Châtelet"));
		entityManager.persistAndFlush(new Equipement("ASC-001", "Ascenseur quai A", TypeEquipement.ASCENSEUR, station));

		assertThat(equipementRepository.existsByReference("ASC-001")).isTrue();
		assertThat(equipementRepository.existsByReference("ASC-404")).isFalse();
	}

	@Test
	void requeteJpqlChargeLaStationAvecLEquipement() {
		Station station = entityManager.persist(new Station("Châtelet"));
		Equipement equipement = entityManager
				.persist(new Equipement("ASC-001", "Ascenseur quai A", TypeEquipement.ASCENSEUR, station));
		entityManager.flush();
		entityManager.clear(); // vide le cache : tout doit être relu en base

		Equipement relu = equipementRepository.findAvecStationParId(equipement.getId()).orElseThrow();

		assertThat(Hibernate.isInitialized(relu.getStation())).isTrue();
		assertThat(relu.getStation().getNom()).isEqualTo("Châtelet");
	}
}
