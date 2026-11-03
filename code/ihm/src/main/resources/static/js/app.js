import { html, afficher } from "./html.js";
import { icone } from "./icones.js";
import { ouvrirPlus } from "./plus.js";
import { session, actualiserSession, fermerSession } from "./session.js";
import { vueAccueil } from "./vues/accueil.js";
import { vueConnexion, vueInscription } from "./vues/authentification.js";
import { vueSalon } from "./vues/salon.js";
import { vuePartie } from "./vues/partie.js";
import { vueParties, vueClassements, vueRevoir } from "./vues/historique.js";
import { vueAdministration } from "./vues/administration.js";

let contenu = document.querySelector("#contenu");
const entete = document.querySelector("#entete");
const onglets = document.querySelector("#onglets");

const LIENS = [
  { cible: "#/salon", nom: "Jouer", image: "jouer" },
  { cible: "#/parties", nom: "Mes parties", image: "parties" },
  { cible: "#/classements", nom: "Classements", image: "classements" },
];
const LIEN_ADMINISTRATION = { cible: "#/administration", nom: "Comptes", image: "comptes" };

const ROUTES = [
  { motif: /^#\/$/, vue: vueAccueil, nom: "Accueil", sansNavigation: true },
  { motif: /^#\/connexion$/, vue: vueConnexion, nom: "Connexion", sansNavigation: true },
  { motif: /^#\/inscription$/, vue: vueInscription, nom: "Créer un compte", sansNavigation: true },
  { motif: /^#\/salon$/, vue: vueSalon, nom: "Jouer" },
  { motif: /^#\/partie$/, vue: vuePartie, nom: "Partie", plein: true, sansBarre: true },
  { motif: /^#\/parties$/, vue: vueParties, nom: "Mes parties" },
  { motif: /^#\/parties\/[\w-]+$/, vue: vueRevoir, nom: "Revoir une partie", plein: true },
  { motif: /^#\/classements$/, vue: vueClassements, nom: "Mes classements" },
  { motif: /^#\/administration$/, vue: vueAdministration, nom: "Gestion des comptes" },
];

function liensDe(joueur) {
  return joueur.administrateur ? [...LIENS, LIEN_ADMINISTRATION] : LIENS;
}

const estCourant = (cible, chemin) => (chemin === cible || chemin.startsWith(`${cible}/`) ? html`aria-current="page"` : "");

const lienNavigation = (chemin) => ({ cible, nom, image }) => html`
  <a href="${cible}" ${estCourant(cible, chemin)}>${icone(image)} <span>${nom}</span></a>`;

const boutonPlus = html`<button class="lien-plus" type="button" data-plus>${icone("plus")} <span>Plus</span></button>`;

function afficherNavigation(route, chemin) {
  document.body.classList.toggle("avec-barre", !route.sansNavigation && !route.sansBarre);
  if (route.sansNavigation) {
    afficher(entete, html``);
    afficher(onglets, html``);
    return;
  }
  const liens = liensDe(session.joueur);
  afficher(entete, html`
    <a class="marque" href="#/salon">Ca<span class="lettre-neon">ï</span>ssa</a>
    <nav class="navigation" aria-label="Navigation principale">
      ${liens.map(lienNavigation(chemin))}
      ${boutonPlus}
    </nav>
    <div class="compte">
      <a class="compte-pseudo" href="#/classements"><span class="avatar">${session.joueur.pseudo[0].toUpperCase()}</span> <span>${session.joueur.pseudo}</span></a>
      <button type="button" id="deconnexion" data-deconnexion>${icone("sortir")} <span>Se déconnecter</span></button>
      ${route.plein && html`<button class="plus-entete" type="button" data-plus aria-label="Plus">${icone("plus")}</button>`}
    </div>`);
  // Pendant une partie, l'échiquier a besoin de toute la hauteur de l'écran.
  afficher(onglets, route.plein ? html`` : html`${LIENS.map(lienNavigation(chemin))}${boutonPlus}`);
}

let nettoyer = null;

function naviguer() {
  const adresse = location.hash || "#/";
  const [chemin, requete] = adresse.split("?");
  const route = ROUTES.find(({ motif }) => motif.test(chemin));
  if (!route) {
    location.hash = "#/";
    return;
  }
  // Masquer une page ne protège rien : c'est le serveur qui refuse. Ceci évite seulement d'afficher un écran inutile.
  const pagePublique = Boolean(route.sansNavigation);
  if (!session.joueur && !pagePublique) {
    location.hash = "#/connexion";
    return;
  }
  if (session.joueur && pagePublique) {
    location.hash = "#/salon";
    return;
  }
  nettoyer?.();
  // Une vue peut avoir posé des écouteurs sur le conteneur : on repart d'un élément neuf.
  const neuf = contenu.cloneNode(false);
  contenu.replaceWith(neuf);
  contenu = neuf;
  document.querySelectorAll("dialog.feuille-plus[open]").forEach((feuille) => feuille.close());
  afficherNavigation(route, chemin);
  document.title = route.nom === "Accueil" ? "Caïssa, échecs en ligne" : `${route.nom}, Caïssa`;
  const parametres = new URLSearchParams(requete);
  nettoyer = route.vue(neuf, {
    parametres,
    aller: (cible) => { location.hash = cible; },
  });
  scrollTo(0, 0);
  neuf.focus({ preventScroll: true });
}

addEventListener("click", async (evenement) => {
  if (evenement.target.closest("[data-plus]")) ouvrirPlus(session.joueur, location.hash || "#/");
  if (evenement.target.closest("[data-deconnexion]")) {
    await fermerSession();
    location.hash = "#/";
    naviguer();
  }
});
addEventListener("hashchange", naviguer);
// Sans réponse du serveur, on reste sur les pages publiques : leurs formulaires afficheront l'erreur.
actualiserSession().catch(() => {}).finally(naviguer);
