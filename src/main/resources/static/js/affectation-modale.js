
"use strict";

document.addEventListener("DOMContentLoaded", () => {

    const dialog = document.getElementById("modale-affectation");

    if (!dialog) {
        return;
    }

    const formulaire = document.getElementById(
        "formulaire-affectation-modale"
    );

    const select = document.getElementById(
        "choix-prestataire"
    );

    const erreur = document.getElementById(
        "erreur-affectation-modale"
    );

    const envoyer = document.getElementById(
        "envoyer-affectation"
    );

    const fermer = document.getElementById(
        "fermer-affectation"
    );

    const csrfToken = dialog.dataset.csrfToken;
    const csrfHeader = dialog.dataset.csrfHeader;
    const urlPrestataires = dialog.dataset.urlPrestataires;

    let incidentId = null;
    let prestataires = [];

    const libelles = {
        VERT: "Vert",
        ORANGE: "Orange",
        ROUGE: "Rouge",
        PRIS_EN_CHARGE: "Pris en charge"
    };

    function afficherErreur(message) {
        erreur.textContent = message;
        erreur.hidden = false;
    }

    function effacerErreur() {
        erreur.textContent = "";
        erreur.hidden = true;
    }

    function texte(element, valeur) {
        if (element) {
            element.textContent = valeur;
        }
    }

    async function lireErreurApi(response) {
        try {
            const probleme = await response.json();

            return probleme.detail
                || probleme.title
                || `Erreur HTTP ${response.status}`;
        } catch {
            return response.status === 403
                ? "Accès refusé : vous ne pouvez pas affecter cet incident."
                : `Erreur HTTP ${response.status}`;
        }
    }

    async function chargerPrestataires() {

        const response = await fetch(urlPrestataires, {
            method: "GET",
            credentials: "same-origin",
            headers: {
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error(await lireErreurApi(response));
        }

        prestataires = await response.json();

        select.replaceChildren();

        const optionVide = document.createElement("option");
        optionVide.value = "";
        optionVide.textContent = "Choisir un prestataire";
        select.appendChild(optionVide);

        for (const prestataire of prestataires) {
            const option = document.createElement("option");

            option.value = String(prestataire.id);
            option.textContent = prestataire.nom;

            select.appendChild(option);
        }

        if (prestataires.length === 0) {
            afficherErreur("Aucun prestataire disponible.");
        }
    }

    function actualiserInterface(incident, prestataire) {

        const conteneurs = document.querySelectorAll(
            "[data-incident-row]"
        );

        for (const conteneur of conteneurs) {

            if (conteneur.dataset.incidentRow
                !== String(incident.id)) {
                continue;
            }

            const badge = conteneur.querySelector(
                "[data-incident-badge]"
            );

            if (badge && incident.niveau) {
                badge.className = "badge badge-"
                    + incident.niveau
                        .toLowerCase()
                        .replaceAll("_", "-");

                badge.textContent =
                    libelles[incident.niveau] || incident.niveau;
            }

            texte(
                conteneur.querySelector("[data-incident-statut]"),
                "Affecté"
            );

            texte(
                conteneur.querySelector("[data-incident-prestataire]"),
                incident.prestataireNom
            );

            const fiche = conteneur.querySelector(
                "[data-prestataire-fiche]"
            );

            const aucun = conteneur.querySelector(
                "[data-prestataire-vide]"
            );

            if (fiche) {
                fiche.hidden = false;
            }

            if (aucun) {
                aucun.hidden = true;
            }

            const lien = conteneur.querySelector(
                "[data-prestataire-lien]"
            );

            if (lien) {
                lien.textContent = prestataire.nom;
                lien.href = `/prestataires/${prestataire.id}`;
            }

            texte(
                conteneur.querySelector("[data-prestataire-email]"),
                prestataire.email
            );

            texte(
                conteneur.querySelector("[data-prestataire-telephone]"),
                prestataire.telephone
            );

            const moyenne = prestataire.noteMoyenne == null
                ? "Pas encore évalué"
                : Number(prestataire.noteMoyenne).toFixed(2) + " / 5";

            texte(
                conteneur.querySelector("[data-prestataire-moyenne]"),
                moyenne
            );

            const message = conteneur.querySelector(
                "[data-affectation-succes]"
            );

            if (message) {
                message.textContent =
                    "Prestataire affecté : " + prestataire.nom;
                message.hidden = false;
            }

            const bouton = conteneur.querySelector(
                "[data-action-affecter]"
            );

            if (bouton) {
                bouton.textContent = "Réaffecter";
            }
        }
    }

    async function ouvrirModale(bouton) {

        incidentId = bouton.dataset.incidentId;

        effacerErreur();

        select.replaceChildren();

        envoyer.disabled = true;

        document.getElementById(
            "titre-modale-affectation"
        ).textContent = bouton.textContent.trim();

        dialog.showModal();

        try {
            await chargerPrestataires();
            envoyer.disabled = prestataires.length === 0;
        } catch (e) {
            afficherErreur(e.message);
        }
    }

    document.querySelectorAll(
        "[data-action-affecter]"
    ).forEach(bouton => {

        bouton.hidden = false;

        bouton.addEventListener("click", () => {
            ouvrirModale(bouton);
        });
    });

    fermer.addEventListener("click", () => {
        dialog.close();
    });

    formulaire.addEventListener("submit", async (event) => {

        event.preventDefault();
        effacerErreur();

        const prestataireId = Number(select.value);

        const prestataire = prestataires.find(p =>
            p.id === prestataireId
        );

        if (!prestataire) {
            afficherErreur("Choisissez un prestataire.");
            return;
        }

        envoyer.disabled = true;

        try {
            const response = await fetch(
                `/api/incidents/${incidentId}/prestataire`,
                {
                    method: "PUT",
                    credentials: "same-origin",
                    headers: {
                        "Content-Type": "application/json",
                        "Accept": "application/json",
                        [csrfHeader]: csrfToken
                    },
                    body: JSON.stringify({
                        prestataireId: prestataireId
                    })
                }
            );

            if (!response.ok) {
                throw new Error(
                    await lireErreurApi(response)
                );
            }

            const incident = await response.json();

            actualiserInterface(incident, prestataire);

            dialog.close();

        } catch (e) {
            afficherErreur(e.message);
        } finally {
            envoyer.disabled = false;
        }
    });

});
