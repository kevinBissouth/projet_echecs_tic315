// Rejoindre une partie se fait en deux temps : un joueur demande, le créateur accepte ou refuse (RG11, RG13).

import { html, afficher, ouvrirDialogue } from "./html.js";
import { api, ErreurApi } from "./api.js";
import { ecouter } from "./temps-reel.js";
import { formaterTemps } from "./duree.js";
import { libelleCadence } from "./icones.js";

const fenetresDuCreateur = new Map();

const decompte = (millisecondes) => html`
  <p class="decompte-demande">Réponse attendue dans <strong data-reste="${millisecondes}">${formaterTemps(millisecondes)}</strong></p>`;

function signalerDans(dialogue, erreur) {
  if (!(erreur instanceof ErreurApi)) throw erreur;
  dialogue.querySelector(".alerte-formulaire")?.remove();
  const alerte = document.createElement("p");
  alerte.className = "alerte-formulaire";
  alerte.setAttribute("role", "alert");
  alerte.textContent = erreur.message;
  dialogue.querySelector("h2").after(alerte);
}

/** Le créateur est prévenu sur la page où il se trouve. Fermer sans répondre laisse la demande expirer. */
function proposerAuCreateur(partie) {
  if (fenetresDuCreateur.has(partie.id)) return;
  const { pseudo, classement } = partie.demande;
  const dialogue = ouvrirDialogue(html`
    <h2>${pseudo} veut jouer</h2>
    <p class="discret">Votre partie en ${libelleCadence(partie.cadence)}, contre un joueur classé ${classement}.</p>
    ${decompte(partie.delaiDeReponseMillisecondes)}
    <div class="pied">
      <button class="bouton" type="button" data-refuser>Refuser</button>
      <button class="bouton bouton-principal" type="button" data-accepter autofocus>Accepter et jouer</button>
    </div>`);
  fenetresDuCreateur.set(partie.id, dialogue);
  const expiration = setTimeout(() => dialogue.close(), partie.delaiDeReponseMillisecondes);
  dialogue.addEventListener("close", () => {
    clearTimeout(expiration);
    fenetresDuCreateur.delete(partie.id);
  });
  dialogue.querySelector("[data-accepter]").addEventListener("click", async () => {
    try {
      await api.accepterDemande(partie.id);
      dialogue.close();
      location.hash = "#/partie";
    } catch (erreur) {
      signalerDans(dialogue, erreur);
    }
  });
  dialogue.querySelector("[data-refuser]").addEventListener("click", async () => {
    try {
      await api.refuserDemande(partie.id);
      dialogue.close();
    } catch (erreur) {
      signalerDans(dialogue, erreur);
    }
  });
}

/** Une vue sans demande ferme la fenêtre : la demande a été retirée, a expiré, ou la partie a commencé. */
export function suivreLesDemandes() {
  ecouter((partie) => {
    if (partie.demande) proposerAuCreateur(partie);
    else fenetresDuCreateur.get(partie.id)?.close();
  });
}

/**
 * Le joueur qui a demandé attend la réponse. Si la partie commence, la navigation générale l'emmène à l'échiquier.
 * @param surFin appelée quand la fenêtre se ferme, quelle qu'en soit la raison
 */
export function attendreLaReponse(partie, surFin) {
  const createur = partie.adversaire.pseudo;
  const dialogue = ouvrirDialogue(html`
    <h2>Demande envoyée</h2>
    <p class="discret">La partie commence dès que ${createur} accepte.</p>
    ${decompte(partie.delaiDeReponseMillisecondes)}
    <div class="pied">
      <button class="bouton" type="button" data-retirer>Retirer ma demande</button>
    </div>`);
  const conclure = (titre, explication) => {
    afficher(dialogue, html`
      <h2>${titre}</h2>
      <p class="discret">${explication}</p>
      <div class="pied"><button class="bouton bouton-principal" type="button" data-fermer autofocus>Retour au salon</button></div>`);
    dialogue.querySelector("[data-fermer]").addEventListener("click", () => dialogue.close());
  };
  const cesserDEcouter = ecouter((nouvelle) => {
    if (nouvelle.id !== partie.id) return;
    if (nouvelle.etat === "EN_COURS" && nouvelle.moi) dialogue.close();
    else if (nouvelle.etat === "ANNULEE") conclure("La partie n'aura pas lieu", `${createur} a décliné votre demande ou retiré sa partie.`);
    else if (!nouvelle.maDemande) conclure(`${createur} n'a pas répondu`, "Votre demande a expiré. Vous pouvez la renouveler ou choisir une autre partie.");
  });
  // Échap ne retire pas la demande en silence : le joueur choisit le bouton.
  dialogue.addEventListener("cancel", (evenement) => evenement.preventDefault());
  dialogue.addEventListener("close", () => {
    cesserDEcouter();
    surFin();
  });
  dialogue.querySelector("[data-retirer]").addEventListener("click", async () => {
    try {
      await api.retirerDemande(partie.id);
      dialogue.close();
    } catch (erreur) {
      signalerDans(dialogue, erreur);
    }
  });
}
