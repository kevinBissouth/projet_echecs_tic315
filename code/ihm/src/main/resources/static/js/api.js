// Les appels au serveur. La session vit dans un cookie ; ce module ne voit jamais de mot de passe stocké.

export const URL_CONNEXION_GOOGLE = "/oauth2/authorization/google";

export class ErreurApi extends Error {
  constructor(statut, message) {
    super(message);
    this.statut = statut;
  }
}

function jetonAntiFalsification() {
  const cookie = document.cookie.split("; ").find((entree) => entree.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.split("=")[1]) : "";
}

async function appeler(methode, chemin, corps) {
  const reponse = await fetch(chemin, {
    method: methode,
    headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": jetonAntiFalsification() },
    body: corps ? JSON.stringify(corps) : undefined,
  });
  if (reponse.status === 204) return null;
  const donnees = await reponse.json().catch(() => null);
  if (!reponse.ok) {
    throw new ErreurApi(reponse.status, donnees?.message ?? "Le serveur ne répond pas correctement. Réessayez.");
  }
  return donnees;
}

export const api = {
  moi: () => appeler("GET", "/api/moi"),
  inscrire: (demande) => appeler("POST", "/api/inscription", demande),
  connecter: (demande) => appeler("POST", "/api/connexion", demande),
  finaliserGoogle: (demande) => appeler("POST", "/api/google/finalisation", demande),
  annulerGoogle: () => appeler("POST", "/api/google/annulation"),
  deconnecter: () => appeler("POST", "/api/deconnexion"),
};
