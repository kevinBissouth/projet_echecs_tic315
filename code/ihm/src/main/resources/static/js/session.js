import { api } from "./api.js";

// Qui visite le site : un joueur connecté, ou quelqu'un qui revient de Google sans compte encore.
export const session = { joueur: null, identiteEnAttente: null, googleDisponible: false };

export async function actualiserSession() {
  Object.assign(session, await api.moi());
}

export async function fermerSession() {
  await api.deconnecter();
  await actualiserSession();
}
