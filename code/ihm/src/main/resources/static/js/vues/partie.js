import { html, afficher, ouvrirDialogue } from "../html.js";
import { Echiquier, lirePosition, imagePiece } from "../echiquier.js";
import { partieEnCours } from "../exemple.js";

const SECONDES_PAR_MINUTE = 60;
const SEUIL_TEMPS_BAS_S = 20;
const SECONDE_MS = 1000;
const DELAI_REPONSE_ADVERSAIRE_MS = 1400;
const PIECES_PROMOTION = ["Q", "R", "B", "N"];
const NOMS_PROMOTION = { Q: "Dame", R: "Tour", B: "Fou", N: "Cavalier" };

function formaterTemps(secondes) {
  const minutes = Math.floor(secondes / SECONDES_PAR_MINUTE);
  return `${minutes}:${String(secondes % SECONDES_PAR_MINUTE).padStart(2, "0")}`;
}

const bandeJoueur = (classe, joueur, pendule) => html`
  <div class="bande ${classe}">
    <div class="bande-identite">
      <strong>${joueur.pseudo}</strong>
      ${joueur.classement && html`<small>${joueur.classement}</small>`}
    </div>
    ${pendule && html`<div class="pendule" role="timer" data-pendule="${classe}">${formaterTemps(pendule)}</div>`}
  </div>`;

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
      <span class="coup ${dernier && !noir ? "courant" : ""}">${blanc}</span>
      <span class="coup ${dernier && noir ? "courant" : ""}">${noir}</span>
    </li>`;
  })}`;

const dialogueBilan = (titre, explication, avecClassement) => html`
  <h2>${titre}</h2>
  <p class="discret">${explication}</p>
  ${avecClassement && html`<div class="bilan">
    <span>Classement blitz</span>
    <span><strong>1532</strong> <span class="resultat-gagne">+9</span></span>
  </div>`}
  <div class="pied">
    <a class="bouton" href="#/parties/opera">Revoir la partie</a>
    <a class="bouton bouton-principal" href="#/salon" autofocus>Nouvelle partie</a>
  </div>`;

function ouvrirBilan(fragment) {
  const dialogue = ouvrirDialogue(fragment);
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

function afficherChoixPromotion(plateau, surChoix) {
  const zone = document.createElement("div");
  zone.className = "promotion";
  afficher(zone, html`
    <div class="promotion-boite" role="dialog" aria-label="Choisir la pièce de promotion">
      <p>Promouvoir votre pion en :</p>
      <div class="promotion-pieces">
        ${PIECES_PROMOTION.map((piece) => html`
          <button type="button" data-piece="${piece}" aria-label="${NOMS_PROMOTION[piece]}"><img src="${imagePiece(piece)}" alt=""></button>`)}
      </div>
    </div>`);
  zone.addEventListener("click", (evenement) => {
    const bouton = evenement.target.closest("[data-piece]");
    if (bouton) {
      zone.remove();
      surChoix(bouton.dataset.piece);
    }
  });
  plateau.append(zone);
  zone.querySelector("button").focus();
}

export function vuePartie(contenu, { parametres }) {
  const apercu = parametres.get("apercu");
  const contreOrdinateur = apercu === "ordinateur";
  const partie = partieEnCours;
  const coups = [...partie.coups];
  const temps = { adversaire: apercu === "temps" ? 41 : partie.tempsAdversaire, joueur: apercu === "temps" ? 14 : partie.tempsJoueur };
  let auTrait = "joueur";
  let termine = false;
  let position = lirePosition(partie.position);
  const minuteries = [];

  const adversaire = contreOrdinateur ? { pseudo: `Ordinateur, ${parametres.get("elo") ?? 100} Elo` } : partie.adversaire;
  const pendules = !contreOrdinateur;

  afficher(contenu, html`
    <section class="partie">
      ${bandeJoueur("bande-adversaire", adversaire, pendules && temps.adversaire)}
      <div class="plateau" id="plateau"></div>
      ${bandeJoueur("bande-joueur", partie.joueur, pendules && temps.joueur)}
      <div class="panneau">
        <aside class="panneau-coups" aria-label="Coups joués">
          <h2>${pendules ? partie.cadence : "Sans pendule, non classée"}</h2>
          <ol class="liste-coups" id="coups"></ol>
        </aside>
        <div class="panneau-actions" id="actions"></div>
      </div>
    </section>`);

  const plateau = contenu.querySelector("#plateau");
  const elementCoups = contenu.querySelector("#coups");
  const elementActions = contenu.querySelector("#actions");

  const actualiserPendules = () => {
    if (!pendules) return;
    for (const [role, classe] of [["adversaire", "bande-adversaire"], ["joueur", "bande-joueur"]]) {
      const pendule = contenu.querySelector(`[data-pendule="${classe}"]`);
      pendule.textContent = formaterTemps(temps[role]);
      const active = !termine && auTrait === role;
      pendule.classList.toggle("active", active && temps[role] > SEUIL_TEMPS_BAS_S);
      pendule.classList.toggle("basse", active && temps[role] <= SEUIL_TEMPS_BAS_S);
    }
  };

  const actualiserCoups = () => {
    afficher(elementCoups, listeCoups(coups));
    elementCoups.scrollTop = elementCoups.scrollHeight;
    elementCoups.scrollLeft = elementCoups.scrollWidth;
  };

  const terminer = (fragment) => {
    termine = true;
    actualiserPendules();
    afficherActions();
    ouvrirBilan(fragment);
  };

  function afficherActions() {
    if (termine) {
      afficher(elementActions, html`
        <a class="bouton" href="#/parties/opera">Revoir la partie</a>
        <a class="bouton bouton-principal" href="#/salon">Nouvelle partie</a>`);
      return;
    }
    if (apercu === "nulle") {
      afficher(elementActions, html`
        <p class="message-partie" style="flex-basis:100%" role="status">${adversaire.pseudo} propose la nulle. Jouer un coup la refuse.</p>
        <button class="bouton bouton-principal" type="button" data-action="accepter">Accepter la nulle</button>
        <button class="bouton" type="button" data-action="refuser">Refuser</button>`);
      return;
    }
    afficher(elementActions, html`
      ${pendules && html`<button class="bouton" type="button" data-action="nulle">Proposer la nulle</button>`}
      ${coups.length < 2 && html`<button class="bouton" type="button" data-action="annuler">Annuler la partie</button>`}
      <button class="bouton bouton-danger" type="button" data-action="abandonner">Abandonner</button>`);
  }

  elementActions.addEventListener("click", (evenement) => {
    const action = evenement.target.closest("[data-action]")?.dataset.action;
    if (action === "abandonner") {
      demanderConfirmation({
        titre: "Abandonner la partie ?",
        explication: pendules ? "Votre adversaire gagne et votre classement blitz baisse." : "Votre classement ne change pas.",
        action: "Abandonner",
        danger: true,
      }, () => terminer(dialogueBilan("Vous avez abandonné", `${adversaire.pseudo} gagne la partie.`, false)));
    } else if (action === "nulle") {
      demanderConfirmation({
        titre: "Proposer la nulle ?",
        explication: `${adversaire.pseudo} pourra accepter ou continuer la partie.`,
        action: "Proposer la nulle",
      }, () => {
        afficher(elementActions, html`<p class="message-partie" style="flex-basis:100%" role="status">Nulle proposée. En attente de la réponse de ${adversaire.pseudo}.</p>`);
      });
    } else if (action === "accepter") {
      terminer(dialogueBilan("Partie nulle", "Vous avez accepté la nulle.", true));
    } else if (action === "refuser") {
      location.hash = "#/partie";
    }
  });

  const echiquier = new Echiquier(plateau, {
    destinations: (nom) => (!termine && auTrait === "joueur" && partie.destinations[nom]) || [],
    surCoup: (depart, arrivee) => {
      position = { ...position, [arrivee]: position[depart] };
      delete position[depart];
      if (depart === "e1" && arrivee === "g1") {
        position.f1 = position.h1;
        delete position.h1;
      }
      coups.push(partie.notations[depart + arrivee] ?? arrivee);
      auTrait = "adversaire";
      echiquier.afficherPosition(position, { dernierCoup: [depart, arrivee] });
      actualiserPendules();
      actualiserCoups();
      minuteries.push(setTimeout(() => {
        position = { ...position, [partie.reponse.arrivee]: position[partie.reponse.depart] };
        delete position[partie.reponse.depart];
        coups.push(partie.reponse.notation);
        echiquier.afficherPosition(position, { dernierCoup: [partie.reponse.depart, partie.reponse.arrivee], animer: true });
        actualiserCoups();
      }, DELAI_REPONSE_ADVERSAIRE_MS));
    },
  });
  echiquier.afficherPosition(position, { dernierCoup: partie.dernierCoup });
  actualiserPendules();
  actualiserCoups();
  afficherActions();

  const tic = setInterval(() => {
    if (termine || !pendules) return;
    temps[auTrait] = Math.max(0, temps[auTrait] - 1);
    actualiserPendules();
  }, SECONDE_MS);

  if (apercu === "promotion") {
    afficherChoixPromotion(plateau, () => {});
  } else if (apercu === "fin") {
    terminer(dialogueBilan("Vous avez gagné", "Échec et mat au 24e coup.", true));
  }

  return () => {
    clearInterval(tic);
    minuteries.forEach(clearTimeout);
  };
}
