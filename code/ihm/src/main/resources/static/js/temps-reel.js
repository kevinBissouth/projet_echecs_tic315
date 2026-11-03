// La ligne ouverte avec le serveur : c'est par elle qu'arrivent les coups de l'adversaire.

import { api } from "./api.js";

const DELAI_DE_RECONNEXION_MS = 2000;

const ecouteurs = new Set();
let ligne = null;
let voulue = false;

function connecter() {
  const schema = location.protocol === "https:" ? "wss://" : "ws://";
  ligne = new WebSocket(`${schema}${location.host}/ws/partie`);
  ligne.addEventListener("message", (evenement) => {
    const partie = JSON.parse(evenement.data);
    ecouteurs.forEach((ecouteur) => ecouteur(partie));
  });
  ligne.addEventListener("close", () => {
    ligne = null;
    if (voulue) reconnecterPlusTard();
  });
}

/**
 * Le serveur a pu redémarrer et perdre la session : un appel le vérifie d'abord, et api.js ramène alors à la connexion.
 * Sans réseau, l'appel échoue : on réessaie plus tard, comme pour la ligne elle-même.
 */
function reconnecterPlusTard() {
  setTimeout(async () => {
    if (!voulue || ligne) return;
    try {
      await api.partie();
      connecter();
    } catch {
      reconnecterPlusTard();
    }
  }, DELAI_DE_RECONNEXION_MS);
}

export function ouvrirLigne() {
  voulue = true;
  if (!ligne) connecter();
}

export function fermerLigne() {
  voulue = false;
  ligne?.close();
}

/** @return de quoi cesser d'écouter */
export function ecouter(ecouteur) {
  ecouteurs.add(ecouteur);
  return () => ecouteurs.delete(ecouteur);
}
