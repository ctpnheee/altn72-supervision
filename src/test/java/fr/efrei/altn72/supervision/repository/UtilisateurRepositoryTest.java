package fr.efrei.altn72.supervision.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import fr.efrei.altn72.supervision.model.Utilisateur;
import fr.efrei.altn72.supervision.model.enums.Profil;

@DataJpaTest
class UtilisateurRepositoryTest {

	@Autowired
	private UtilisateurRepository utilisateurRepository;

	@Test
	void findByLoginRetrouveUnUtilisateurExistant() {
		utilisateurRepository.saveAndFlush(new Utilisateur("agent", "hash", "Alice", Profil.AGENT));

		assertThat(utilisateurRepository.findByLogin("agent"))
				.get().extracting(Utilisateur::getPrenom, Utilisateur::getProfil)
				.containsExactly("Alice", Profil.AGENT);
		assertThat(utilisateurRepository.findByLogin("inconnu")).isEmpty();
	}

	@Test
	void deuxUtilisateursDeMemeLoginSontRefuses() {
		utilisateurRepository.saveAndFlush(new Utilisateur("agent", "hash", "Alice", Profil.AGENT));

		assertThatThrownBy(() -> utilisateurRepository
				.saveAndFlush(new Utilisateur("agent", "hash2", "Autre", Profil.RESPONSABLE)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
