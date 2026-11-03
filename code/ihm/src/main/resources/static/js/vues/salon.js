import { html, afficher } from "../html.js";
import { Echiquier, lirePosition } from "../echiquier.js";
import { icone, iconeFamille, libelleCadence, libelleCourt } from "../icones.js";
import { lignePartie } from "./historique.js";
import { CADENCES, classementJoueur, classements, joueurConnecte, partiesOuvertes, partiesTerminees, POSITION_INITIALE } from "../exemple.js";

// Échelle affichée sur le site : le serveur la convertit vers celle de Stockfish (100 devient 1320, 3190 reste 3190).
const ELO_MINIMUM = 100;
const ELO_MAXIMUM = 3190;
const PAS_DE_LA_BARRE = 10;
const CADENCE_PAR_DEFAUT = "5+0";
const NOMBRE_LIGNES_ACCUEIL = 3;
const AIDE_ELO = `De ${ELO_MINIMUM} à ${ELO_MAXIMUM}. Glissez la barre ou saisissez une valeur.`;
const estUnEloValide = (valeur) => Number.isInteger(valeur) && valeur >= ELO_MINIMUM && valeur <= ELO_MAXIMUM;

const COULEURS = [
  { valeur: "hasard", nom: "Au hasard" },
  { valeur: "blanc", nom: "Blancs" },
  { valeur: "noir", nom: "Noirs" },
];

const familleDe = (cadence) => CADENCES.find(({ cadences }) => cadences.includes(cadence)).famille;
const dureeEnPendule = (cadence) => `${cadence.split("+")[0]}:00`;

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
      <input id="invite" name="invite" autocomplete="off" required>
      <p class="aide">Il recevra l'invitation et pourra l'accepter ou la refuser.</p>
    </div>
    ${menuCadence(cadence)}
    ${groupeCouleurs()}
    <button class="bouton bouton-principal bouton-grand" type="submit">Envoyer l'invitation</button>
  </form>`;

const vueOrdinateur = () => html`
  ${enTeteVue("Contre l'ordinateur")}
  <form class="panneau-formulaire" id="contre-ordinateur" novalidate>
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

const ligneOuverte = (partie) => html`
  <li class="ligne-ouverte">
    <div class="ligne-ouverte-joueur">
      <strong>${partie.pseudo}</strong> <span class="discret">(${partie.classement})</span>
      <p class="discret">${iconeFamille(partie.famille)} ${libelleCadence(partie.cadence)}, il joue : ${partie.couleur.toLowerCase()}</p>
    </div>
    <a class="bouton bouton-petit" href="#/partie">Rejoindre</a>
  </li>`;

const listeOuvertes = (parties) => parties.length === 0
  ? html`<div class="vide">
      <p>Aucune partie ouverte pour le moment.</p>
      <p>Créez-en une : elle apparaîtra ici pour les autres joueurs.</p>
    </div>`
  : html`<ul class="liste-ouvertes">${parties.map(ligneOuverte)}</ul>`;

const bandeauInvitation = () => html`
  <div class="bandeau" role="status">
    <p><strong>claire_b</strong> vous invite à une partie en 10 min + 5 s.</p>
    <div class="bandeau-actions">
      <a class="bouton bouton-principal bouton-petit" href="#/partie">Accepter</a>
      <a class="bouton bouton-petit" href="#/salon">Refuser</a>
    </div>
  </div>`;

const bandeauAttente = () => html`
  <div class="bandeau" role="status">
    <p>Votre partie en 5 min attend un adversaire.</p>
    <div class="bandeau-actions">
      <a class="bouton bouton-danger bouton-petit" href="#/salon">Annuler la partie</a>
    </div>
  </div>`;

const bandeSalle = (classe, nom, detail, temps) => html`
  <div class="bande ${classe}">
    <div class="bande-identite">
      <strong>${nom}</strong>
      ${detail && html`<small>${detail}</small>`}
    </div>
    ${temps && html`<div class="pendule" data-temps>${temps}</div>`}
  </div>`;

const totalParties = () => classements.reduce((somme, { victoires, nulles, defaites }) => somme + victoires + nulles + defaites, 0);

/* L'accueil du téléphone : le profil, les parties, et un seul bouton pour jouer. */
const tableauDeBord = (etat, parties) => html`
  <div class="tableau-bord">
    <section class="carte-profil" aria-label="Votre profil">
      <a class="profil-tete" href="#/classements">
        <span class="avatar avatar-moyen" aria-hidden="true">${joueurConnecte.pseudo[0].toUpperCase()}</span>
        <span class="profil-nom">
          <strong>${joueurConnecte.pseudo}</strong>
          <small>${totalParties()} parties classées</small>
        </span>
        ${icone("suite")}
      </a>
      <ul class="mini-classements">
        ${classements.map(({ famille, points }) => html`
          <li>
            <a href="#/classements">
              <span class="mini-famille">${iconeFamille(famille)} ${famille}</span>
              <strong>${points}</strong>
            </a>
          </li>`)}
      </ul>
    </section>

    ${etat === "invitation" && bandeauInvitation()}
    ${etat === "attente" && bandeauAttente()}

    <section aria-labelledby="titre-ouvertes">
      <div class="section-tete">
        <h2 id="titre-ouvertes">Parties ouvertes</h2>
        ${parties.length > NOMBRE_LIGNES_ACCUEIL && html`<button class="lien-discret" type="button" data-ouvrir="ouvertes">Tout voir (${parties.length})</button>`}
      </div>
      <div class="cadre">${listeOuvertes(parties.slice(0, NOMBRE_LIGNES_ACCUEIL))}</div>
    </section>

    <section aria-labelledby="titre-dernieres">
      <div class="section-tete">
        <h2 id="titre-dernieres">Mes dernières parties</h2>
        <a class="lien-discret" href="#/parties">Tout voir</a>
      </div>
      <div class="cadre">
        <ul class="liste-parties">${partiesTerminees.slice(0, NOMBRE_LIGNES_ACCUEIL).map(lignePartie)}</ul>
      </div>
    </section>

    <div class="barre-jouer">
      <button class="bouton bouton-principal bouton-grand" type="button" data-ouvrir="nouvelle" ${etat === "attente" ? html`disabled` : ""}>
        ${etat === "attente" ? "Partie en attente" : "Jouer"}
      </button>
    </div>
  </div>`;

export function vueSalon(contenu, { parametres }) {
  const etat = parametres.get("etat");
  const parties = etat === "vide" ? [] : partiesOuvertes;
  let cadence = CADENCE_PAR_DEFAUT;
  let vue = parametres.get("type") === "ordinateur" ? "ordinateur" : "principale";

  afficher(contenu, html`
    <section class="salle">
      <div class="salle-plateau">
        <div id="bande-haut"></div>
        <div id="plateau"></div>
        <div id="bande-bas"></div>
      </div>
      ${tableauDeBord(etat, parties)}
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

  const corps = contenu.querySelector("#corps");
  const echiquier = new Echiquier(contenu.querySelector("#plateau"));
  echiquier.afficherPosition(lirePosition(POSITION_INITIALE));

  const dessinerBandes = () => {
    const contreOrdinateur = vue === "ordinateur";
    const temps = contreOrdinateur ? null : dureeEnPendule(cadence);
    afficher(contenu.querySelector("#bande-haut"), contreOrdinateur
      ? bandeSalle("bande-adversaire", "Ordinateur", "Niveau au choix", null)
      : bandeSalle("bande-adversaire", "Adversaire", null, temps));
    afficher(contenu.querySelector("#bande-bas"),
      bandeSalle("bande-joueur", joueurConnecte.pseudo, classementJoueur(familleDe(cadence)), temps));
  };

  const VUES = { principale: vuePrincipale, invitation: vueInvitation, ordinateur: vueOrdinateur };
  const dessinerVue = () => {
    afficher(corps, VUES[vue](cadence));
    dessinerBandes();
  };
  const choisirOnglet = (onglet) => {
    contenu.querySelectorAll("[data-onglet]").forEach((bouton) => {
      bouton.setAttribute("aria-selected", String(bouton.dataset.onglet === onglet));
    });
    if (onglet === "ouvertes") afficher(corps, listeOuvertes(parties));
    else dessinerVue();
  };

  contenu.querySelectorAll("[data-onglet]").forEach((bouton) => {
    bouton.addEventListener("click", () => choisirOnglet(bouton.dataset.onglet));
  });
  corps.addEventListener("click", (evenement) => {
    const bouton = evenement.target.closest("[data-vue]");
    if (bouton) {
      vue = bouton.dataset.vue;
      dessinerVue();
    }
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
    if (formulaire.id !== "contre-ordinateur") {
      location.hash = "#/salon?etat=attente";
      return;
    }
    const saisie = formulaire.querySelector("#elo-saisie");
    if (estUnEloValide(Number(saisie.value))) {
      location.hash = `#/partie?apercu=ordinateur&elo=${Number(saisie.value)}`;
    } else {
      saisie.focus();
    }
  });

  const voirOuvertes = parametres.get("vue") === "parties" || etat === "vide";
  choisirOnglet(voirOuvertes ? "ouvertes" : "nouvelle");
  dessinerBandes();

  const feuille = contenu.querySelector(".feuille");
  const ouvrirFeuille = (onglet) => {
    choisirOnglet(onglet);
    feuille.showModal();
    // Le focus va sur la feuille, pas sur sa croix : ouvrir pour jouer ne doit pas proposer de fermer.
    feuille.focus();
  };
  contenu.querySelectorAll("[data-ouvrir]").forEach((bouton) => {
    bouton.addEventListener("click", () => ouvrirFeuille(bouton.dataset.ouvrir));
  });
  feuille.querySelector(".feuille-fermer").addEventListener("click", () => feuille.close());
  feuille.addEventListener("click", (evenement) => {
    // Un clic sur la feuille elle-même, c'est-à-dire sur le fond qui l'entoure.
    if (evenement.target === feuille) feuille.close();
  });

  if (parametres.has("feuille") || parametres.has("type") || parametres.has("vue")) {
    ouvrirFeuille(voirOuvertes ? "ouvertes" : "nouvelle");
  }
}
