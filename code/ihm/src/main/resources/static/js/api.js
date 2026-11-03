// Les appels au serveur. La session vit dans un cookie ; ce module ne voit jamais de mot de passe stocké.

export const URL_CONNEXION_GOOGLE = "/oauth2/authorization/google";
const STATUT_NON_CONNECTE = 401;

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
  // La session a disparu (serveur redémarré, par exemple) : la page repart de la connexion.
  if (reponse.status === STATUT_NON_CONNECTE && !chemin.startsWith("/api/connexion") && !chemin.startsWith("/api/google")) {
    location.hash = "#/connexion";
    location.reload();
  }
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

  rechercherJoueurs: (debut) => appeler("GET", `/api/joueurs?debut=${encodeURIComponent(debut)}`),
  salon: () => appeler("GET", "/api/parties"),
  creerPartie: (demande) => appeler("POST", "/api/parties", demande),
  contreOrdinateur: (demande) => appeler("POST", "/api/parties/ordinateur", demande),
  demander: (id) => appeler("POST", `/api/parties/${id}/demande`),
  retirerDemande: (id) => appeler("DELETE", `/api/parties/${id}/demande`),
  accepterDemande: (id) => appeler("POST", `/api/parties/${id}/demande/acceptation`),
  refuserDemande: (id) => appeler("POST", `/api/parties/${id}/demande/refus`),
  refuser: (id) => appeler("POST", `/api/parties/${id}/refus`),
  annulerEnAttente: (id) => appeler("POST", `/api/parties/${id}/annulation`),

  partie: () => appeler("GET", "/api/partie"),
  jouer: (coup) => appeler("POST", "/api/partie/coups", coup),
  abandonner: () => appeler("POST", "/api/partie/abandon"),
  annuler: () => appeler("POST", "/api/partie/annulation"),
  proposerNulle: () => appeler("POST", "/api/partie/nulle"),
  repondreNulle: (accepte) => appeler("POST", "/api/partie/nulle/reponse", { accepte }),

  mesParties: () => appeler("GET", "/api/mes-parties"),
  partieArchivee: (id) => appeler("GET", `/api/mes-parties/${id}`),
  bilans: () => appeler("GET", "/api/mes-bilans"),

  comptes: () => appeler("GET", "/api/admin/comptes"),
  suspendre: (pseudo) => appeler("POST", `/api/admin/comptes/${encodeURIComponent(pseudo)}/suspension`),
  reactiver: (pseudo) => appeler("POST", `/api/admin/comptes/${encodeURIComponent(pseudo)}/reactivation`),
  nommer: (pseudo) => appeler("POST", `/api/admin/comptes/${encodeURIComponent(pseudo)}/nomination`),
};
