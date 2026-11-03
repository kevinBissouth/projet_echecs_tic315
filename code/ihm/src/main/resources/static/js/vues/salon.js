import { html, afficher } from "../html.js";
import { api, ErreurApi } from "../api.js";
import { Echiquier, lirePosition } from "../echiquier.js";
import { icone, iconeFamille, libelleCadence, libelleCourt } from "../icones.js";
import { CADENCES, POSITION_INITIALE } from "../donnees.js";
import { session } from "../session.js";
import { formaterTemps } from "../duree.js";
import { attendreLaReponse } from "../demandes.js";
import { lignePartie, NOMS_FAMILLES } from "./historique.js";

// Échelle affichée sur le site : le serveur la convertit vers celle de Stockfish (100 devient 1320, 3190 reste 3190).
const ELO_MINIMUM = 100;
const ELO_MAXIMUM = 3190;
const PAS_DE_LA_BARRE = 10;
const CADENCE_PAR_DEFAUT = "5+0";
const NOMBRE_LIGNES_ACCUEIL = 3;
const DELAI_DE_RECHERCHE_MS = 180;
const DELAI_FERMETURE_LISTE_MS = 150;
const INTERVALLE_ACTUALISATION_MS = 5000;
const AIDE_ELO = `De ${ELO_MINIMUM} à ${ELO_MAXIMUM}. Glissez la barre ou saisissez une valeur.`;
const estUnEloValide = (valeur) => Number.isInteger(valeur) && valeur >= ELO_MINIMUM && valeur <= ELO_MAXIMUM;

const COULEURS = [
  { valeur: "HASARD", nom: "Au hasard" },
  { valeur: "BLANC", nom: "Blancs" },
  { valeur: "NOIR", nom: "Noirs" },
];
const COULEUR_DU_CREATEUR = { BLANC: "les blancs", NOIR: "les noirs" };

const familleDe = (cadence) => CADENCES.find(({ cadences }) => cadences.includes(cadence)).famille;
const dureeEnPendule = (cadence) => `${cadence.split("+")[0]}:00`;
const pointsEn = (famille) => session.joueur.classements[famille.toUpperCase()];

const groupeCouleurs = () => html`
  <fieldset class="champ">
    <legend>Votre couleur</legend>
    <div class="choix choix-egaux">
      ${COULEURS.map(({ valeur, nom }, indice) => html`
        <label><input type="radio" name="couleur" value="${valeur}" ${indice === 0 ? html`checked` : ""}><span>${nom}</span></label>`)}
    </div>
  </fieldset>`;

const menuCadence = (courante) => html`
  <details class="menu-cadence">
    <summary>
      <span class="menu-valeur" id="cadence-affichee">${iconeFamille(familleDe(courante))} ${libelleCadence(courante)} <span class="discret">${familleDe(courante)}</span></span>
      ${icone("chevron")}
    </summary>
    <div class="menu-contenu">
      ${CADENCES.map(({ famille, cadences }) => html`
        <fieldset class="famille">
          <legend>${iconeFamille(famille)} ${famille}</legend>
          <div class="choix choix-egaux">
            ${cadences.map((cadence) => html`
              <label><input type="radio" name="cadence" value="${cadence}" ${cadence === courante ? html`checked` : ""}><span aria-label="${libelleCadence(cadence)}">${libelleCourt(cadence)}</span></label>`)}
          </div>
        </fieldset>`)}
    </div>
  </details>`;

const vuePrincipale = (cadence) => html`
  <form class="panneau-formulaire" data-action="creer">
    ${menuCadence(cadence)}
    ${groupeCouleurs()}
    <button class="bouton bouton-principal bouton-grand" type="submit">Créer la partie</button>
  </form>
  <div class="rangees-choix">
    <button class="rangee-choix" type="button" data-vue="invitation">${icone("defier")} <span>Défier un joueur</span> ${icone("suite")}</button>
    <button class="rangee-choix" type="button" data-vue="ordinateur">${icone("ordinateur")} <span>Contre l'ordinateur</span> ${icone("suite")}</button>
  </div>`;

const enTeteVue = (titre) => html`
  <button class="retour-panneau" type="button" data-vue="principale">${icone("retour")} ${titre}</button>`;

const vueInvitation = (cadence) => html`
  ${enTeteVue("Défier un joueur")}
  <form class="panneau-formulaire" data-action="creer" novalidate>
    <div class="champ">
      <label for="invite">Pseudo du joueur</label>
      <input id="invite" name="invite" autocomplete="off" autocapitalize="none" spellcheck="false" required
             role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="suggestions" aria-describedby="aide-invite">
      <ul class="suggestions" id="suggestions" role="listbox" aria-label="Joueurs trouvés" hidden></ul>
      <p class="aide" id="aide-invite">Tapez les premières lettres : les joueurs correspondants s'affichent.</p>
    </div>
    ${menuCadence(cadence)}
    ${groupeCouleurs()}
    <button class="bouton bouton-principal bouton-grand" type="submit">Envoyer l'invitation</button>
  </form>`;

const vueOrdinateur = () => html`
  ${enTeteVue("Contre l'ordinateur")}
  <form class="panneau-formulaire" data-action="ordinateur" novalidate>
    <div class="champ">
      <label for="elo-saisie">Elo de l'ordinateur</label>
      <div class="reglage-elo">
        <input id="elo-barre" type="range" min="${ELO_MINIMUM}" max="${ELO_MAXIMUM}" step="${PAS_DE_LA_BARRE}" value="${ELO_MINIMUM}"
               aria-label="Elo de l'ordinateur, sur une barre">
        <input id="elo-saisie" name="elo" type="number" inputmode="numeric" min="${ELO_MINIMUM}" max="${ELO_MAXIMUM}" step="1" value="${ELO_MINIMUM}"
               aria-describedby="elo-aide">
      </div>
      <p class="aide" id="elo-aide">${AIDE_ELO}</p>
    </div>
    ${groupeCouleurs()}
    <p class="aide">Sans pendule. Cette partie ne compte pas pour votre classement.</p>
    <button class="bouton bouton-principal bouton-grand" type="submit">Commencer la partie</button>
  </form>`;

const expiration = (partie) => html`
  <span class="expiration">Expire dans <span data-reste="${partie.expirationMillisecondes}">${formaterTemps(partie.expirationMillisecondes)}</span></span>`;

/** Une partie qu'on ne peut pas rejoindre reste dans la liste, grisée : le joueur voit pourquoi. */
function raisonIndisponible(partie, jeJoue) {
  if (jeJoue) return "Vous êtes en partie";
  if (!partie.enLigne) return "Hors ligne";
  if (partie.occupe) return "En partie";
  if (partie.sollicitee) return "Déjà sollicitée";
  return null;
}

const ligneOuverte = (jeJoue) => (partie) => {
  const raison = raisonIndisponible(partie, jeJoue);
  return html`
    <li class="ligne-ouverte ${raison ? "indisponible" : ""}">
      <div class="ligne-ouverte-joueur">
        <strong>${partie.pseudo}</strong> <span class="discret">(${partie.classement})</span>
        <p class="discret">${iconeFamille(NOMS_FAMILLES[partie.categorie])} ${libelleCadence(partie.cadence)}, ${COULEUR_DU_CREATEUR[partie.couleur] ? html`il joue ${COULEUR_DU_CREATEUR[partie.couleur]}` : "couleurs au hasard"}</p>
        <p class="discret">${raison ? html`<span class="raison">${raison}</span>` : expiration(partie)}</p>
      </div>
      <button class="bouton bouton-petit" type="button" data-rejoindre="${partie.id}" ${raison ? html`disabled` : ""}>Rejoindre</button>
    </li>`;
};

const listeOuvertes = (parties, jeJoue) => parties.length === 0
  ? html`<div class="vide">
      <p>Aucune partie ouverte pour le moment.</p>
      <p>Créez-en une : elle apparaîtra ici pour les autres joueurs.</p>
    </div>`
  : html`<ul class="liste-ouvertes">${parties.map(ligneOuverte(jeJoue))}</ul>`;

const bandeauInvitation = (jeJoue) => (invitation) => {
  const raison = raisonIndisponible(invitation, jeJoue);
  return html`
    <div class="bandeau ${raison ? "indisponible" : ""}" role="status">
      <p><strong>${invitation.pseudo}</strong> vous invite à une partie en ${libelleCadence(invitation.cadence)}.
        ${raison ? html`<span class="raison">${raison}</span>` : expiration(invitation)}</p>
      <div class="bandeau-actions">
        <button class="bouton bouton-principal bouton-petit" type="button" data-rejoindre="${invitation.id}" ${raison ? html`disabled` : ""}>Accepter</button>
        <button class="bouton bouton-petit" type="button" data-refuser="${invitation.id}">Refuser</button>
      </div>
    </div>`;
};

/** Mes parties qui attendent restent proposées pendant que je joue, grisées : personne ne peut les rejoindre. */
const bandeauMienne = (jeJoue) => (partie) => html`
  <div class="bandeau ${jeJoue ? "indisponible" : ""}" role="status">
    <p>${partie.invite
        ? html`Votre invitation à <strong>${partie.invite}</strong> en ${libelleCadence(partie.cadence)} attend sa réponse.`
        : html`Votre partie en ${libelleCadence(partie.cadence)} attend un adversaire.`}
      ${jeJoue ? html`<span class="raison">En pause pendant votre partie</span>` : expiration(partie)}</p>
    <div class="bandeau-actions">
      <button class="bouton bouton-danger bouton-petit" type="button" data-annuler-partie="${partie.id}">Annuler</button>
    </div>
  </div>`;

const bandeauReprise = () => html`
  <div class="bandeau" role="status">
    <p>Vous avez une partie en cours.</p>
    <div class="bandeau-actions"><a class="bouton bouton-principal bouton-petit" href="#/partie">Reprendre</a></div>
  </div>`;

const bandeSalle = (classe, nom, detail, temps) => html`
  <div class="bande ${classe}">
    <div class="bande-identite">
      <strong>${nom}</strong>
      ${detail && html`<small>${detail}</small>`}
    </div>
    ${temps && html`<div class="pendule" data-temps>${temps}</div>`}
  </div>`;

const totalClasses = (bilans) => bilans.reduce((somme, { victoires, nulles, defaites }) => somme + victoires + nulles + defaites, 0);

const carteProfil = (bilans) => html`
  <section class="carte-profil" aria-label="Votre profil">
    <a class="profil-tete" href="#/classements">
      <span class="avatar avatar-moyen" aria-hidden="true">${session.joueur.pseudo[0].toUpperCase()}</span>
      <span class="profil-nom">
        <strong>${session.joueur.pseudo}</strong>
        <small>${totalClasses(bilans)} ${totalClasses(bilans) > 1 ? "parties classées" : "partie classée"}</small>
      </span>
      ${icone("suite")}
    </a>
    <ul class="mini-classements">
      ${CADENCES.map(({ famille }) => html`
        <li>
          <a href="#/classements">
            <span class="mini-famille">${iconeFamille(famille)} ${famille}</span>
            <strong>${pointsEn(famille)}</strong>
          </a>
        </li>`)}
    </ul>
  </section>`;

const dernieresParties = (parties) => html`
  <section aria-labelledby="titre-dernieres">
    <div class="section-tete">
      <h2 id="titre-dernieres">Mes dernières parties</h2>
      ${parties.length > 0 && html`<a class="lien-discret" href="#/parties">Tout voir</a>`}
    </div>
    <div class="cadre">
      ${parties.length === 0
        ? html`<div class="vide"><p>Vous n'avez pas encore terminé de partie.</p><p>Elles apparaîtront ici, prêtes à être revues.</p></div>`
        : html`<ul class="liste-parties">${parties.slice(0, NOMBRE_LIGNES_ACCUEIL).map(lignePartie)}</ul>`}
    </div>
  </section>`;

const jeJoueDans = (salon) => salon.partieCourante?.etat === "EN_COURS";

const tableauDeBord = ({ salon, bilans, parties }) => {
  const jeJoue = jeJoueDans(salon);
  return html`
    ${carteProfil(bilans)}
    ${jeJoue && bandeauReprise()}
    ${salon.invitations.map(bandeauInvitation(jeJoue))}
    ${salon.mesParties.map(bandeauMienne(jeJoue))}
    <section aria-labelledby="titre-ouvertes">
      <div class="section-tete">
        <h2 id="titre-ouvertes">Parties ouvertes</h2>
        ${salon.ouvertes.length > NOMBRE_LIGNES_ACCUEIL && html`<button class="lien-discret" type="button" data-ouvrir="ouvertes">Tout voir (${salon.ouvertes.length})</button>`}
      </div>
      <div class="cadre">${listeOuvertes(salon.ouvertes.slice(0, NOMBRE_LIGNES_ACCUEIL), jeJoue)}</div>
    </section>
    ${dernieresParties(parties)}
    <div class="barre-jouer">
      <button class="bouton bouton-principal bouton-grand" type="button" data-ouvrir="nouvelle" ${jeJoue ? html`disabled` : ""}>
        ${jeJoue ? "Partie en cours" : "Jouer"}
      </button>
    </div>`;
};

const suggestion = (pseudo, debut, indice) => html`
  <li role="option" id="suggestion-${indice}" data-pseudo="${pseudo}" aria-selected="false">
    <span class="avatar" aria-hidden="true">${pseudo[0].toUpperCase()}</span>
    <span><mark>${pseudo.slice(0, debut.length)}</mark>${pseudo.slice(debut.length)}</span>
  </li>`;

/** Recherche pendant la frappe : la liste suit les lettres tapées, au doigt comme au clavier. */
function brancherRecherche(zone) {
  const saisie = zone.querySelector("#invite");
  const liste = zone.querySelector("#suggestions");
  if (!saisie) return;
  let minuterie;
  let pseudos = [];
  let actif = -1;

  const fermer = () => {
    liste.hidden = true;
    saisie.setAttribute("aria-expanded", "false");
    saisie.removeAttribute("aria-activedescendant");
    actif = -1;
  };
  const choisir = (pseudo) => {
    saisie.value = pseudo;
    fermer();
  };
  const marquer = (indice) => {
    actif = indice;
    liste.querySelectorAll("[role=option]").forEach((option, rang) => option.setAttribute("aria-selected", String(rang === indice)));
    const option = liste.querySelector(`#suggestion-${indice}`);
    saisie.setAttribute("aria-activedescendant", option.id);
    option.scrollIntoView({ block: "nearest" });
  };
  const montrer = (debut, joueurs) => {
    pseudos = joueurs.map(({ pseudo }) => pseudo);
    afficher(liste, pseudos.length === 0
      ? html`<li class="suggestion-vide" role="presentation">Aucun joueur ne commence par « ${debut} ».</li>`
      : html`${pseudos.map((pseudo, indice) => suggestion(pseudo, debut, indice))}`);
    liste.hidden = false;
    saisie.setAttribute("aria-expanded", "true");
    actif = -1;
  };

  saisie.addEventListener("input", () => {
    clearTimeout(minuterie);
    const debut = saisie.value.trim();
    if (!debut) {
      fermer();
      return;
    }
    minuterie = setTimeout(async () => {
      const joueurs = await api.rechercherJoueurs(debut).catch(() => []);
      // Une réponse arrivée après une nouvelle frappe ne correspond plus à ce qui est écrit.
      if (saisie.value.trim() === debut) montrer(debut, joueurs);
    }, DELAI_DE_RECHERCHE_MS);
  });
  saisie.addEventListener("keydown", (evenement) => {
    if (liste.hidden) return;
    if (evenement.key === "Escape") {
      evenement.preventDefault();
      fermer();
      return;
    }
    if (pseudos.length === 0) return;
    if (evenement.key === "ArrowDown" || evenement.key === "ArrowUp") {
      evenement.preventDefault();
      const pas = evenement.key === "ArrowDown" ? 1 : -1;
      marquer((actif + pas + pseudos.length) % pseudos.length);
    } else if (evenement.key === "Enter" && actif >= 0) {
      evenement.preventDefault();
      choisir(pseudos[actif]);
    }
  });
  liste.addEventListener("pointerdown", (evenement) => {
    const option = evenement.target.closest("[data-pseudo]");
    if (!option) return;
    evenement.preventDefault();
    choisir(option.dataset.pseudo);
  });
  saisie.addEventListener("blur", () => setTimeout(fermer, DELAI_FERMETURE_LISTE_MS));
}

export function vueSalon(contenu, { aller }) {
  let cadence = CADENCE_PAR_DEFAUT;
  let vue = "principale";
  let donnees = null;
  let actif = true;

  afficher(contenu, html`
    <section class="salle">
      <div class="salle-plateau">
        <div id="bande-haut"></div>
        <div id="plateau"></div>
        <div id="bande-bas"></div>
      </div>
      <div class="tableau-bord" id="tableau"></div>
    </section>
    <dialog class="feuille" aria-label="Nouvelle partie" tabindex="-1">
      <button class="feuille-fermer" type="button" aria-label="Fermer">${icone("fermer")}</button>
      <div class="panneau-salle">
        <div class="onglets-panneau" role="tablist" aria-label="Jouer">
          <button type="button" role="tab" data-onglet="nouvelle" aria-selected="true">Nouvelle partie</button>
          <button type="button" role="tab" data-onglet="ouvertes" aria-selected="false">Parties ouvertes</button>
        </div>
        <div class="panneau-corps" id="corps" role="tabpanel"></div>
      </div>
    </dialog>`);

  const tableau = contenu.querySelector("#tableau");
  const corps = contenu.querySelector("#corps");
  const feuille = contenu.querySelector(".feuille");
  new Echiquier(contenu.querySelector("#plateau"), { decor: true }).afficherPosition(lirePosition(POSITION_INITIALE));

  const dessinerBandes = () => {
    const contreOrdinateur = vue === "ordinateur";
    const temps = contreOrdinateur ? null : dureeEnPendule(cadence);
    afficher(contenu.querySelector("#bande-haut"), contreOrdinateur
      ? bandeSalle("bande-adversaire", "Ordinateur", "Niveau au choix", null)
      : bandeSalle("bande-adversaire", "Adversaire", null, temps));
    afficher(contenu.querySelector("#bande-bas"),
      bandeSalle("bande-joueur", session.joueur.pseudo, contreOrdinateur ? null : pointsEn(familleDe(cadence)), temps));
  };

  const VUES = { principale: vuePrincipale, invitation: vueInvitation, ordinateur: vueOrdinateur };
  const dessinerVue = () => {
    afficher(corps, VUES[vue](cadence));
    brancherRecherche(corps);
    dessinerBandes();
  };
  const choisirOnglet = (onglet) => {
    contenu.querySelectorAll("[data-onglet]").forEach((bouton) => {
      bouton.setAttribute("aria-selected", String(bouton.dataset.onglet === onglet));
    });
    if (onglet === "ouvertes") afficher(corps, listeOuvertes(donnees?.salon.ouvertes ?? [], donnees && jeJoueDans(donnees.salon)));
    else dessinerVue();
  };
  const ouvrirFeuille = (onglet) => {
    if (onglet === "nouvelle") vue = "principale";
    choisirOnglet(onglet);
    feuille.showModal();
    // Le focus va sur la feuille, pas sur sa croix : ouvrir pour jouer ne doit pas proposer de fermer.
    feuille.focus();
  };

  /** Une erreur du serveur s'affiche là où le joueur regarde : dans la feuille si elle est ouverte. */
  const signaler = (erreur) => {
    if (!(erreur instanceof ErreurApi)) throw erreur;
    const zone = feuille.open ? corps : tableau;
    zone.querySelector(".alerte-formulaire")?.remove();
    const alerte = document.createElement("p");
    alerte.className = "alerte-formulaire";
    alerte.setAttribute("role", "alert");
    alerte.textContent = erreur.message;
    zone.prepend(alerte);
  };

  let attenteOuverte = false;

  /** Une seule fenêtre d'attente à la fois, même si le salon se recharge pendant qu'elle est ouverte. */
  const attendre = (partie) => {
    if (attenteOuverte) return;
    attenteOuverte = true;
    attendreLaReponse(partie, () => {
      attenteOuverte = false;
      actualiserSansDeranger();
    });
  };

  const charger = async () => {
    const [salon, bilans, parties] = await Promise.all([api.salon(), api.bilans(), api.mesParties()]);
    if (!actif) return;
    donnees = { salon, bilans, parties };
    afficher(tableau, tableauDeBord(donnees));
    // Après un rechargement de la page, le joueur retrouve la demande qu'il attendait.
    if (salon.partieCourante?.maDemande) attendre(salon.partieCourante);
  };

  const demander = async (id) => {
    const partie = await api.demander(id);
    feuille.close();
    attendre(partie);
  };

  const actualiserSansDeranger = () => {
    // Une feuille ouverte n'est pas touchée : on ne redessine pas un formulaire en cours de saisie.
    if (actif && !feuille.open) charger().catch(() => {});
  };

  const agir = async (action) => {
    try {
      await action();
    } catch (erreur) {
      signaler(erreur);
    }
  };

  tableau.addEventListener("click", (evenement) => {
    const { target } = evenement;
    const ouvrir = target.closest("[data-ouvrir]");
    const rejoindre = target.closest("[data-rejoindre]");
    const refuser = target.closest("[data-refuser]");
    if (ouvrir) ouvrirFeuille(ouvrir.dataset.ouvrir);
    const annuler = target.closest("[data-annuler-partie]");
    if (rejoindre) agir(() => demander(rejoindre.dataset.rejoindre));
    if (refuser) agir(async () => { await api.refuser(refuser.dataset.refuser); await charger(); });
    if (annuler) agir(async () => { await api.annulerEnAttente(annuler.dataset.annulerPartie); await charger(); });
  });
  contenu.querySelectorAll("[data-onglet]").forEach((bouton) => {
    bouton.addEventListener("click", () => choisirOnglet(bouton.dataset.onglet));
  });
  corps.addEventListener("click", (evenement) => {
    const bouton = evenement.target.closest("[data-vue]");
    const rejoindre = evenement.target.closest("[data-rejoindre]");
    if (bouton) {
      vue = bouton.dataset.vue;
      dessinerVue();
    }
    if (rejoindre) agir(() => demander(rejoindre.dataset.rejoindre));
  });
  corps.addEventListener("change", (evenement) => {
    if (evenement.target.name !== "cadence") return;
    cadence = evenement.target.value;
    const famille = familleDe(cadence);
    afficher(corps.querySelector("#cadence-affichee"), html`${iconeFamille(famille)} ${libelleCadence(cadence)} <span class="discret">${famille}</span>`);
    corps.querySelector(".menu-cadence").open = false;
    dessinerBandes();
  });
  corps.addEventListener("input", (evenement) => {
    const barre = corps.querySelector("#elo-barre");
    const saisie = corps.querySelector("#elo-saisie");
    if (!barre || (evenement.target !== barre && evenement.target !== saisie)) return;
    if (evenement.target === barre) saisie.value = barre.value;
    const valide = estUnEloValide(Number(saisie.value));
    if (valide) barre.value = saisie.value;
    saisie.setAttribute("aria-invalid", String(!valide));
    const aide = corps.querySelector("#elo-aide");
    aide.className = valide ? "aide" : "erreur";
    aide.textContent = valide ? AIDE_ELO : `Saisissez un nombre entier entre ${ELO_MINIMUM} et ${ELO_MAXIMUM}.`;
  });
  corps.addEventListener("submit", (evenement) => {
    evenement.preventDefault();
    const formulaire = evenement.target;
    const couleur = formulaire.elements.couleur.value;
    if (formulaire.dataset.action === "ordinateur") {
      const saisie = formulaire.elements.elo;
      if (!estUnEloValide(Number(saisie.value))) {
        saisie.focus();
        return;
      }
      agir(async () => {
        await api.contreOrdinateur({ elo: Number(saisie.value), couleur });
        feuille.close();
        aller("#/partie");
      });
      return;
    }
    agir(async () => {
      await api.creerPartie({ cadence, couleur, invite: formulaire.elements.invite?.value.trim() ?? "" });
      feuille.close();
      await charger();
    });
  });
  feuille.querySelector(".feuille-fermer").addEventListener("click", () => feuille.close());
  feuille.addEventListener("click", (evenement) => {
    // Un clic sur la feuille elle-même, c'est-à-dire sur le fond qui l'entoure.
    if (evenement.target === feuille) feuille.close();
  });

  dessinerVue();
  charger().catch((erreur) => signaler(erreur));
  const minuterie = setInterval(actualiserSansDeranger, INTERVALLE_ACTUALISATION_MS);
  return () => {
    actif = false;
    clearInterval(minuterie);
  };
}
