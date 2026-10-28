package fr.efrei.altn72.supervision.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.efrei.altn72.supervision.model.Utilisateur;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

	Optional<Utilisateur> findByLogin(String login);
}
