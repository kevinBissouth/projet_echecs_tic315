import { html, afficher } from "../html.js";
import { Echiquier, lirePosition, appliquerDeplacements } from "../echiquier.js";
import { LOGO_GOOGLE } from "../icones.js";
import { URL_CONNEXION_GOOGLE } from "../api.js";
import { session } from "../session.js";
import { partieOpera, POSITION_INITIALE } from "../donnees.js";

const INTERVALLE_RELECTURE_MS = 2400;
const PAUSE_FIN_DE_PARTIE_MS = 7000;
const DUREE_DU_TRAJET_MS = 650;


export function vueAccueil(contenu) {
  afficher(contenu, html`
    <section class="accueil">
      <div class="accueil-colonne">
      <div class="accueil-texte">
        <h1>Ca<span class="lettre-neon">ï</span>ssa</h1>
        <p class="accroche">Le jeu des rois,<br>à votre mesure.</p>
        <p class="faits">Défiez des joueurs en bullet, blitz ou rapide, affrontez l'ordinateur au niveau de votre choix et suivez votre classement, partie après partie.</p>
      </div>
      <div class="accueil-actions">
        <a class="bouton bouton-principal" href="#/connexion">C'est parti</a>
        ${session.googleDisponible && html`<a class="bouton bouton-google" href="${URL_CONNEXION_GOOGLE}">${LOGO_GOOGLE} Continuer avec Google</a>`}
      </div>
      </div>
      <figure>
        <div id="plateau" aria-hidden="true"></div>
        <figcaption>La partie de l'Opéra. Morphy contre le duc de Brunswick et le comte Isouard, Paris, 1858.</figcaption>
      </figure>
    </section>`);


  const echiquier = new Echiquier(contenu.querySelector("#plateau"), { decor: true });
  return rejouerLaPartie(echiquier);
}

/** Le seul mouvement de la page : la partie de l'Opéra se rejoue lentement, puis recommence. */
function rejouerLaPartie(echiquier) {
  const initiale = lirePosition(POSITION_INITIALE);
  if (matchMedia("(prefers-reduced-motion: reduce)").matches) {
    const finale = partieOpera.coups.reduce((position, coup) => appliquerDeplacements(position, coup.deplacements), initiale);
    echiquier.afficherPosition(finale, { dernierCoup: partieOpera.coups.at(-1).deplacements[0] });
    return () => {};
  }

  let position = initiale;
  let indice = 0;
  let minuterie;
  echiquier.afficherPosition(position);

  const suivant = () => {
    if (indice === partieOpera.coups.length) {
      indice = 0;
      position = initiale;
      echiquier.afficherPosition(position);
      minuterie = setTimeout(suivant, INTERVALLE_RELECTURE_MS);
      return;
    }
    const { deplacements } = partieOpera.coups[indice];
    position = appliquerDeplacements(position, deplacements);
    echiquier.afficherPosition(position, { dernierCoup: deplacements[0], animer: true, dureeDuTrajet: DUREE_DU_TRAJET_MS });
    indice += 1;
    minuterie = setTimeout(suivant, indice === partieOpera.coups.length ? PAUSE_FIN_DE_PARTIE_MS : INTERVALLE_RELECTURE_MS);
  };
  minuterie = setTimeout(suivant, INTERVALLE_RELECTURE_MS);
  return () => clearTimeout(minuterie);
}
