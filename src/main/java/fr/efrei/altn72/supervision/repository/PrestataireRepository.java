package fr.efrei.altn72.supervision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.efrei.altn72.supervision.model.Prestataire;

public interface PrestataireRepository extends JpaRepository<Prestataire, Long> {

	List<Prestataire> findAllByOrderByNomAsc();
}
