import { html, afficher, ouvrirDialogue } from "../html.js";
import { api, ErreurApi } from "../api.js";
import { Echiquier, lirePosition, imagePiece, animationsPermises, DUREE_ANNONCE_MS } from "../echiquier.js";
import { actualiserSession } from "../session.js";
import { ecouter } from "../temps-reel.js";
import { formaterTemps } from "../duree.js";

// La pendule passe au rouge dès qu'elle affiche 0:59.
const SEUIL_TEMPS_BAS_MS = 59 * 1000;
const INTERVALLE_PENDULE_MS = 200;
const RANGEE_DE_PROMOTION = { BLANC: "8", NOIR: "1" };
const PROMOTIONS = [
  { lettre: "q", type: "DAME", nom: "Dame" },
  { lettre: "r", type: "TOUR", nom: "Tour" },
  { lettre: "b", type: "FOU", nom: "Fou" },
  { lettre: "n", type: "CAVALIER", nom: "Cavalier" },
];
const MOTIFS = {
  MAT: "Échec et mat.",
  PAT: "Pat : le joueur au trait n'a plus de coup légal.",
  ABANDON: "Partie terminée par abandon.",
  TEMPS_ECOULE: "Temps écoulé.",
  ACCORD: "Nulle par accord.",
  TRIPLE_REPETITION: "Nulle par triple répétition.",
  CINQUANTE_COUPS: "Nulle par la règle des 50 coups.",
  MATERIEL_INSUFFISANT: "Nulle : matériel insuffisant pour mater.",
};
const TITRES = { gagnee: "Vous avez gagné", perdue: "Vous avez perdu", nulle: "Partie nulle", annulee: "Partie annulée" };

const nomDe = (camp) => camp?.pseudo ?? "En attente d'un adversaire";

const detailDe = (camp, partie) => {
  if (!camp?.classement) return null;
  return partie.contreOrdinateur && camp === partie.adversaire ? `${camp.classement} Elo` : camp.classement;
};

const bandeJoueur = (classe, camp, partie) => html`
  <div class="bande ${classe}">
    <div class="bande-identite">
      <strong>${nomDe(camp)}</strong>
      ${detailDe(camp, partie) && html`<small>${detailDe(camp, partie)}</small>`}
    </div>
    ${camp?.tempsMillisecondes !== null && camp?.tempsMillisecondes !== undefined && html`<div class="pendule" role="timer" data-pendule></div>`}
  </div>`;

/** Le roque déplace aussi une tour : on la fait glisser, comme le roi. */
function trajetDeLaTour({ arrivee }) {
  const rangee = arrivee[1];
  return arrivee[0] === "g" ? [`h${rangee}`, `f${rangee}`] : [`a${rangee}`, `d${rangee}`];
}

/** Ce que le dernier coup a produit : c'est ce que l'échiquier va montrer. */
export function effetsDuCoup(coup, positionAvant, { caseEnEchec, mat }) {
  const effets = {};
  if (coup.notation.includes("x")) effets.prise = { case: coup.arrivee, piece: positionAvant[coup.arrivee] };
  if (coup.notation.includes("=")) effets.promotion = coup.arrivee;
  if (coup.notation.startsWith("O-O")) effets.roque = trajetDeLaTour(coup);
  if (mat) effets.mat = caseEnEchec;
  else if (caseEnEchec) effets.echec = caseEnEchec;
  return effets;
}

function regrouperParCoup(coups) {
  const lignes = [];
  for (let indice = 0; indice < coups.length; indice += 2) {
    lignes.push({ numero: indice / 2 + 1, blanc: coups[indice], noir: coups[indice + 1] });
  }
  return lignes;
}

const listeCoups = (coups) => html`
  ${regrouperParCoup(coups).map(({ numero, blanc, noir }, indice, lignes) => {
    const dernier = indice === lignes.length - 1;
    return html`<li>
      <span class="numero">${numero}.</span>
      <span class="coup ${dernier && !noir ? "courant" : ""}">${blanc.notation}</span>
      <span class="coup ${dernier && noir ? "courant" : ""}">${noir?.notation}</span>
    </li>`;
  })}`;

function ouvrirBilan(partie) {
  const categorie = partie.categorie;
  const dialogue = ouvrirDialogue(html`
    <h2>${TITRES[partie.issue]}</h2>
    <p class="discret">${MOTIFS[partie.motif] ?? "Cette partie ne compte pas pour le classement."}</p>
    ${categorie && partie.issue !== "annulee" && html`<div class="bilan">
      <span>Classement</span>
      <span><strong>${partie.moi.classement}</strong></span>
    </div>`}
    <div class="pied">
      ${partie.etat === "TERMINEE" && html`<a class="bouton" href="#/parties/${partie.id}">Revoir la partie</a>`}
      <a class="bouton bouton-principal" href="#/salon" autofocus>Nouvelle partie</a>
    </div>`);
  dialogue.querySelectorAll("a").forEach((lien) => lien.addEventListener("click", () => dialogue.close()));
}

function demanderConfirmation({ titre, explication, action, danger }, surConfirmation) {
  const dialogue = ouvrirDialogue(html`
    <h2>${titre}</h2>
    <p class="discret">${explication}</p>
    <div class="pied">
      <button class="bouton" data-fermer type="button">Continuer la partie</button>
      <button class="bouton ${danger ? "bouton-danger" : "bouton-principal"}" data-confirmer type="button">${action}</button>
    </div>`);
  dialogue.querySelector("[data-fermer]").addEventListener("click", () => dialogue.close());
  dialogue.querySelector("[data-confirmer]").addEventListener("click", () => {
    dialogue.close();
    surConfirmation();
  });
}

function afficherChoixPromotion(plateau, couleur, surChoix) {
  const zone = document.createElement("div");
  zone.className = "promotion";
  const piece = (lettre) => (couleur === "BLANC" ? lettre.toUpperCase() : lettre);
  afficher(zone, html`
    <div class="promotion-boite" role="dialog" aria-label="Choisir la pièce de promotion">
      <p>Promouvoir votre pion en :</p>
      <div class="promotion-pieces">
        ${PROMOTIONS.map(({ lettre, type, nom }) => html`
          <button type="button" data-promotion="${type}" aria-label="${nom}"><img src="${imagePiece(piece(lettre))}" alt=""></button>`)}
      </div>
    </div>`);
  zone.addEventListener("click", (evenement) => {
    const bouton = evenement.target.closest("[data-promotion]");
    if (bouton) {
      zone.remove();
      surChoix(bouton.dataset.promotion);
    }
  });
  plateau.append(zone);
  zone.querySelector("button").focus();
}

export function vuePartie(contenu, { aller }) {
  let actif = true;
  let arreter = () => {};
  api.partie().then((partie) => {
    if (!actif) return;
    if (!partie) {
      aller("#/salon");
      return;
    }
    arreter = lancerPartie(contenu, partie);
  }).catch(() => aller("#/salon"));
  return () => {
    actif = false;
    arreter();
  };
}

function lancerPartie(contenu, premiere) {
  let partie = premiere;
  let etatPrecedent = premiere.etat;
  let recu = performance.now();
  let message = "";

  afficher(contenu, html`
    <section class="partie">
      <div id="bande-haut" class="bande-adversaire"></div>
      <div class="plateau" id="plateau"></div>
      <div id="bande-bas" class="bande-joueur"></div>
      <div class="panneau">
        <aside class="panneau-coups" aria-label="Coups joués">
          <h2 id="cadence"></h2>
          <ol class="liste-coups" id="coups"></ol>
        </aside>
        <div class="panneau-actions" id="actions"></div>
      </div>
    </section>`);

  const plateau = contenu.querySelector("#plateau");
  const elementCoups = contenu.querySelector("#coups");
  const elementActions = contenu.querySelector("#actions");
  const bandeHaut = contenu.querySelector("#bande-haut");
  const bandeBas = contenu.querySelector("#bande-bas");

  const echiquier = new Echiquier(plateau, {
    orientation: premiere.maCouleur === "NOIR" ? "noir" : "blanc",
    destinations: (nom) => partie.destinations?.[nom] ?? [],
    surCoup: (depart, arrivee) => demanderLeCoup(depart, arrivee),
  });

  const actualiserPendules = () => {
    const ecoule = performance.now() - recu;
    for (const [bande, camp] of [[bandeHaut, partie.adversaire], [bandeBas, partie.moi]]) {
      const pendule = bande.querySelector("[data-pendule]");
      if (!pendule || !camp) continue;
      const reste = camp.tempsMillisecondes - (camp.penduleActive ? ecoule : 0);
      const basse = reste <= SEUIL_TEMPS_BAS_MS;
      pendule.textContent = formaterTemps(reste);
      pendule.classList.toggle("active", camp.penduleActive && !basse);
      pendule.classList.toggle("basse", basse);
    }
  };

  function dessinerActions() {
    if (partie.etat === "TERMINEE" || partie.etat === "ANNULEE") {
      afficher(elementActions, html`
        ${partie.etat === "TERMINEE" && html`<a class="bouton" href="#/parties/${partie.id}">Revoir la partie</a>`}
        <a class="bouton bouton-principal" href="#/salon">Nouvelle partie</a>`);
      return;
    }
    const texte = message || (partie.nulleProposeeParAdversaire ? `${partie.adversaire.pseudo} propose le nulle. Jouer un coup le refuse.`
      : partie.nulleProposeeParMoi ? `Nulle proposé. En attente de la réponse de ${partie.adversaire.pseudo}.`
      : partie.contreOrdinateur && partie.trait !== partie.maCouleur ? "L'ordinateur réfléchit."
      : "");
    afficher(elementActions, html`
      ${texte && html`<p class="message-partie" style="flex-basis:100%" role="status">${texte}</p>`}
      ${partie.nulleProposeeParAdversaire ? html`
        <button class="bouton bouton-principal" type="button" data-action="accepter">Accepter le nulle</button>
        <button class="bouton" type="button" data-action="refuser">Refuser</button>` : html`
        ${partie.etat === "EN_COURS" && !partie.contreOrdinateur && !partie.nulleProposeeParMoi && html`<button class="bouton" type="button" data-action="nulle">Proposer le nulle</button>`}
        ${partie.annulable && html`<button class="bouton" type="button" data-action="annuler">Annuler la partie</button>`}
        ${partie.etat === "EN_COURS" && html`<button class="bouton bouton-danger" type="button" data-action="abandonner">Abandonner</button>`}`}`);
  }

  function dessiner(animer, effets = {}) {
    afficher(bandeHaut, bandeJoueur("", partie.adversaire, partie));
    afficher(bandeBas, bandeJoueur("", partie.moi, partie));
    afficher(contenu.querySelector("#cadence"), html`${partie.contreOrdinateur ? "Sans pendule, non classée" : partie.cadence}`);
    afficher(elementCoups, listeCoups(partie.coups));
    elementCoups.scrollTop = elementCoups.scrollHeight;
    elementCoups.scrollLeft = elementCoups.scrollWidth;
    const dernier = partie.coups.at(-1);
    echiquier.afficherPosition(lirePosition(partie.position), {
      dernierCoup: dernier ? [dernier.depart, dernier.arrivee] : null,
      caseEnEchec: partie.caseEnEchec,
      animer,
    });
    echiquier.jouerEffets(effets);
    actualiserPendules();
    dessinerActions();
  }

  function recevoir(nouvelle) {
    const nouveauCoup = nouvelle.coups.length > partie.coups.length;
    const mat = nouvelle.motif === "MAT";
    const tempsEcoule = nouvelle.motif === "TEMPS_ECOULE" && etatPrecedent !== nouvelle.etat;
    const effets = nouveauCoup
      ? effetsDuCoup(nouvelle.coups.at(-1), echiquier.position, { caseEnEchec: nouvelle.caseEnEchec, mat })
      : {};
    if (tempsEcoule) effets.tempsEcoule = true;
    partie = nouvelle;
    recu = performance.now();
    message = "";
    dessiner(nouveauCoup, effets);
    if (tempsEcoule) marquerLaPenduleTombee();
    const finie = partie.etat === "TERMINEE" || partie.etat === "ANNULEE";
    if (finie && etatPrecedent !== partie.etat) {
      // Le mat ou la pendule tombée se voient d'abord sur l'échiquier ; le bilan vient ensuite.
      const attente = (mat || tempsEcoule) && animationsPermises() ? DUREE_ANNONCE_MS : 0;
      actualiserSession().finally(() => setTimeout(() => ouvrirBilan(partie), attente));
    }
    etatPrecedent = partie.etat;
  }

  /** La pendule à zéro tremble : on voit tout de suite quel camp a perdu au temps. */
  function marquerLaPenduleTombee() {
    for (const [bande, camp] of [[bandeHaut, partie.adversaire], [bandeBas, partie.moi]]) {
      if (camp?.tempsMillisecondes === 0) bande.querySelector("[data-pendule]")?.classList.add("tombee");
    }
  }

  /** Un refus du serveur se lit dans le panneau ; l'échiquier reprend la position qu'il connaît. */
  async function agir(action) {
    try {
      const reponse = await action();
      if (reponse) recevoir(reponse);
    } catch (erreur) {
      if (!(erreur instanceof ErreurApi)) throw erreur;
      message = erreur.message;
      dessiner(false);
    }
  }

  function demanderLeCoup(depart, arrivee) {
    const piece = echiquier.position[depart];
    const estPromotion = piece?.toLowerCase() === "p" && arrivee.endsWith(RANGEE_DE_PROMOTION[partie.maCouleur]);
    if (estPromotion) {
      afficherChoixPromotion(plateau, partie.maCouleur, (promotion) => agir(() => api.jouer({ depart, arrivee, promotion })));
    } else {
      agir(() => api.jouer({ depart, arrivee }));
    }
  }

  elementActions.addEventListener("click", (evenement) => {
    const action = evenement.target.closest("[data-action]")?.dataset.action;
    if (action === "abandonner") {
      demanderConfirmation({
        titre: "Abandonner la partie ?",
        explication: partie.contreOrdinateur ? "Votre classement ne change pas." : "Votre adversaire gagne et votre classement baisse.",
        action: "Abandonner",
        danger: true,
      }, () => agir(() => api.abandonner()));
    } else if (action === "annuler") {
      demanderConfirmation({
        titre: "Annuler la partie ?",
        explication: "Elle ne comptera pas : aucun résultat ni classement ne change.",
        action: "Annuler la partie",
        danger: true,
      }, () => agir(() => api.annuler()));
    } else if (action === "nulle") {
      demanderConfirmation({
        titre: "Proposer le nulle ?",
        explication: `${partie.adversaire.pseudo} pourra accepter ou continuer la partie.`,
        action: "Proposer le nulle",
      }, () => agir(() => api.proposerNulle()));
    } else if (action === "accepter") {
      agir(() => api.repondreNulle(true));
    } else if (action === "refuser") {
      agir(() => api.repondreNulle(false));
    }
  });

  const cesserDEcouter = ecouter((nouvelle) => {
    if (nouvelle.id === partie.id) recevoir(nouvelle);
  });
  const tic = setInterval(actualiserPendules, INTERVALLE_PENDULE_MS);
  dessiner(false);
  if (partie.etat === "TERMINEE" || partie.etat === "ANNULEE") dessinerActions();

  return () => {
    clearInterval(tic);
    cesserDEcouter();
  };
}
