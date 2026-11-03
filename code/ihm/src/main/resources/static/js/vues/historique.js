import { html, afficher } from "../html.js";
import { Echiquier, lirePosition, appliquerDeplacements, miniature } from "../echiquier.js";
import { icone, iconeFamille, libelleCadence } from "../icones.js";
import { classements, joueurConnecte, partieOpera, partiesTerminees, POSITION_INITIALE, profil } from "../exemple.js";

const FILTRES = ["Toutes", "Bullet", "Blitz", "Rapide", "Ordinateur"];
const LARGEUR_COURBE = 600;
const HAUTEUR_COURBE = 180;
const MARGE_COURBE = 12;

function issue(partie) {
  if (partie.resultat === "½-½") return "nulle";
  const gagnant = partie.resultat === "1-0" ? partie.blancs : partie.noirs;
  return gagnant.pseudo === joueurConnecte.pseudo ? "gagnee" : "perdue";
}

const SIGNES = { gagnee: "+", perdue: "−", nulle: "=" };
const NOMS_ISSUES = { gagnee: "Partie gagnée", perdue: "Partie perdue", nulle: "Partie nulle" };

const ligneJoueur = (joueur, couleur) => html`
  <span class="joueur-ligne">
    <span class="carre-couleur ${couleur}" aria-hidden="true"></span>
    <strong>${joueur.pseudo}</strong>
    ${joueur.classement && html`<span class="discret">(${joueur.classement})</span>`}
    ${joueur.variation && html`<span class="${joueur.variation.startsWith("+") ? "variation-plus" : "variation-moins"}">${joueur.variation}</span>`}
  </span>`;

export const lignePartie = (partie) => {
  const fin = issue(partie);
  const [scoreBlancs, scoreNoirs] = partie.resultat.split("-");
  return html`
    <li>
      <a class="ligne-partie" href="#/parties/${partie.id}">
        ${miniature(partie.position)}
        <div class="ligne-cadence">
          ${iconeFamille(partie.famille)}
          <span>${partie.cadence ? libelleCadence(partie.cadence) : "Sans pendule"}</span>
        </div>
        <div class="ligne-joueurs">
          ${ligneJoueur(partie.blancs, "blanc")}
          ${ligneJoueur(partie.noirs, "noir")}
          <p class="discret">${partie.coups} coups, ${partie.motif.toLowerCase()}</p>
        </div>
        <div class="ligne-resultat">
          <span class="score"><span>${scoreBlancs}</span><span>${scoreNoirs}</span></span>
          <span class="badge-issue ${fin}" title="${NOMS_ISSUES[fin]}"><span class="visuellement-masque">${NOMS_ISSUES[fin]}</span><span aria-hidden="true">${SIGNES[fin]}</span></span>
        </div>
        <time class="ligne-date discret">${partie.date}</time>
      </a>
    </li>`;
};

export function vueParties(contenu, { parametres }) {
  const parties = parametres.get("etat") === "vide" ? [] : partiesTerminees;
  afficher(contenu, html`
    <section class="page page-large">
      <h1 class="titre-page">${icone("parties")} Mes parties</h1>
      <div class="cadre">
        <div class="cadre-outils">
          <div class="onglets-filtre" role="group" aria-label="Filtrer les parties">
            ${FILTRES.map((nom, indice) => html`<button type="button" data-filtre="${nom}" aria-pressed="${indice === 0}">${nom}</button>`)}
          </div>
          <span class="discret" id="compte-parties"></span>
        </div>
        <div id="liste-parties"></div>
      </div>
    </section>`);

  const emplacement = contenu.querySelector("#liste-parties");
  const compte = contenu.querySelector("#compte-parties");
  const dessiner = (filtre) => {
    const visibles = filtre === FILTRES[0] ? parties : parties.filter(({ famille }) => famille === filtre);
    compte.textContent = `${visibles.length} ${visibles.length > 1 ? "parties" : "partie"}`;
    afficher(emplacement, visibles.length === 0
      ? html`<div class="vide vide-cadre">
          <p>Aucune partie terminée ici pour le moment.</p>
          <p><a href="#/salon">Lancez une partie</a> : vous pourrez la revoir ici, coup par coup.</p>
        </div>`
      : html`<ul class="liste-parties">${visibles.map(lignePartie)}</ul>`);
  };
  contenu.querySelectorAll("[data-filtre]").forEach((bouton) => {
    bouton.addEventListener("click", () => {
      contenu.querySelectorAll("[data-filtre]").forEach((autre) => autre.setAttribute("aria-pressed", String(autre === bouton)));
      dessiner(bouton.dataset.filtre);
    });
  });
  dessiner(FILTRES[0]);
}

/** La courbe du classement : chaque point est le classement après une partie. */
function courbe(evolution) {
  const minimum = Math.min(...evolution);
  const maximum = Math.max(...evolution);
  const etendue = Math.max(maximum - minimum, 1);
  const abscisse = (indice) => MARGE_COURBE + (indice * (LARGEUR_COURBE - 2 * MARGE_COURBE)) / (evolution.length - 1);
  const ordonnee = (points) => HAUTEUR_COURBE - MARGE_COURBE - ((points - minimum) * (HAUTEUR_COURBE - 2 * MARGE_COURBE)) / etendue;
  const trace = evolution.map((points, indice) => `${abscisse(indice).toFixed(1)},${ordonnee(points).toFixed(1)}`).join(" ");
  return html`
    <div class="courbe">
      <div class="courbe-echelle discret" aria-hidden="true"><span>${maximum}</span><span>${minimum}</span></div>
      <svg viewBox="0 0 ${LARGEUR_COURBE} ${HAUTEUR_COURBE}" preserveAspectRatio="none" role="img"
           aria-label="Classement sur les ${evolution.length} dernières parties : de ${evolution[0]} à ${evolution.at(-1)}">
        <polyline points="${trace}" vector-effect="non-scaling-stroke"></polyline>
      </svg>
    </div>`;
}

const detailFamille = (classement) => {
  const total = classement.victoires + classement.nulles + classement.defaites;
  const part = (nombre) => `${(nombre * 100) / total}%`;
  return html`
    <div class="detail-famille">
      <div>
        <h2 class="section-titre">Évolution en ${classement.famille.toLowerCase()}</h2>
        ${courbe(classement.evolution)}
      </div>
      <div>
        <h2 class="section-titre">Bilan</h2>
        <dl class="bilan-famille">
          <div><dt>Classement actuel</dt><dd>${classement.points}</dd></div>
          <div><dt>Meilleur classement</dt><dd>${classement.meilleur}</dd></div>
          <div><dt>Parties jouées</dt><dd>${total}</dd></div>
        </dl>
        <div class="barre-bilan" role="img" aria-label="${classement.victoires} victoires, ${classement.nulles} nulles, ${classement.defaites} défaites">
          <span class="gagnee" style="width:${part(classement.victoires)}"></span>
          <span class="nulle" style="width:${part(classement.nulles)}"></span>
          <span class="perdue" style="width:${part(classement.defaites)}"></span>
        </div>
      </div>
    </div>`;
};

export function vueClassements(contenu) {
  const total = classements.reduce((somme, { victoires, nulles, defaites }) => somme + victoires + nulles + defaites, 0);
  afficher(contenu, html`
    <section class="page page-large">
      <header class="profil">
        <span class="avatar avatar-grand" aria-hidden="true">${joueurConnecte.pseudo[0].toUpperCase()}</span>
        <div>
          <h1>${joueurConnecte.pseudo}</h1>
          <p class="discret">Membre depuis le ${profil.depuis}, ${total} parties classées</p>
        </div>
      </header>
      <div class="cadre">
        <table class="tableau tableau-cadre tableau-classements">
          <thead><tr><th>Cadence</th><th>Classement</th><th>Parties</th><th>Victoires</th><th>Nulles</th><th>Défaites</th></tr></thead>
          <tbody>
            ${classements.map((classement, indice) => html`
              <tr data-famille="${indice}" class="${indice === 1 ? "selectionnee" : ""}">
                <td><button class="bouton-ligne" type="button" aria-pressed="${indice === 1}">${iconeFamille(classement.famille)} ${classement.famille}</button></td>
                <td data-etiquette="Classement" class="chiffre-fort">${classement.points}</td>
                <td data-etiquette="Parties">${classement.victoires + classement.nulles + classement.defaites}</td>
                <td data-etiquette="Victoires">${classement.victoires}</td>
                <td data-etiquette="Nulles">${classement.nulles}</td>
                <td data-etiquette="Défaites">${classement.defaites}</td>
              </tr>`)}
          </tbody>
        </table>
        <div id="detail"></div>
      </div>
    </section>`);

  const detail = contenu.querySelector("#detail");
  const choisir = (indice) => {
    afficher(detail, detailFamille(classements[indice]));
    contenu.querySelectorAll("[data-famille]").forEach((ligne) => {
      const choisie = Number(ligne.dataset.famille) === indice;
      ligne.classList.toggle("selectionnee", choisie);
      ligne.querySelector("button").setAttribute("aria-pressed", String(choisie));
    });
  };
  contenu.querySelectorAll("[data-famille]").forEach((ligne) => {
    ligne.addEventListener("click", () => choisir(Number(ligne.dataset.famille)));
  });
  choisir(1);
}

export function vueRevoir(contenu) {
  const partie = partieOpera;
  const positions = [lirePosition(POSITION_INITIALE)];
  for (const coup of partie.coups) {
    positions.push(appliquerDeplacements(positions.at(-1), coup.deplacements));
  }
  let courant = positions.length - 1;

  afficher(contenu, html`
    <section class="partie relecture">
      <header class="entete-relecture" style="grid-area:adversaire">
        <h1>${partie.titre}</h1>
        <p class="discret">${partie.sousTitre}</p>
      </header>
      <div class="plateau" id="plateau"></div>
      <div class="panneau-actions" style="grid-area:joueur">
        <div class="navigation-coups" role="group" aria-label="Parcourir les coups">
          <button class="bouton" type="button" data-aller="debut" aria-label="Début de la partie">Début</button>
          <button class="bouton" type="button" data-aller="precedent" aria-label="Coup précédent">Précédent</button>
          <button class="bouton" type="button" data-aller="suivant" aria-label="Coup suivant">Suivant</button>
          <button class="bouton" type="button" data-aller="fin" aria-label="Fin de la partie">Fin</button>
        </div>
      </div>
      <div class="panneau">
        <aside class="panneau-coups" aria-label="Coups joués">
          <h2>Coups</h2>
          <ol class="liste-coups" id="coups"></ol>
        </aside>
        <div class="panneau-actions"><a class="bouton" href="#/parties">Retour à mes parties</a></div>
      </div>
    </section>`);

  const echiquier = new Echiquier(contenu.querySelector("#plateau"));
  const elementCoups = contenu.querySelector("#coups");

  const lignes = [];
  for (let indice = 0; indice < partie.coups.length; indice += 2) lignes.push(indice);
  afficher(elementCoups, html`${lignes.map((indice) => html`
    <li>
      <span class="numero">${indice / 2 + 1}.</span>
      <button class="coup" type="button" data-coup="${indice + 1}">${partie.coups[indice].notation}</button>
      ${partie.coups[indice + 1] && html`<button class="coup" type="button" data-coup="${indice + 2}">${partie.coups[indice + 1].notation}</button>`}
    </li>`)}`);

  // On ne fait défiler que la liste : la page, elle, doit rester sur l'échiquier.
  const centrerDansLaListe = (bouton) => {
    elementCoups.scrollTo({
      left: bouton.offsetLeft - elementCoups.clientWidth / 2,
      top: bouton.offsetTop - elementCoups.clientHeight / 2,
    });
  };

  const aller = (cible, animer = false) => {
    courant = Math.min(Math.max(cible, 0), positions.length - 1);
    const dernierCoup = courant > 0 ? partie.coups[courant - 1].deplacements[0] : null;
    echiquier.afficherPosition(positions[courant], { dernierCoup, animer });
    elementCoups.querySelectorAll(".coup").forEach((bouton) => {
      const estCourant = Number(bouton.dataset.coup) === courant;
      bouton.classList.toggle("courant", estCourant);
      if (estCourant) bouton.setAttribute("aria-current", "true");
      else bouton.removeAttribute("aria-current");
      if (estCourant) centrerDansLaListe(bouton);
    });
  };

  const DEPLACEMENTS = {
    debut: () => aller(0),
    precedent: () => aller(courant - 1),
    suivant: () => aller(courant + 1, true),
    fin: () => aller(positions.length - 1),
  };

  contenu.querySelectorAll("[data-aller]").forEach((bouton) => {
    bouton.addEventListener("click", () => DEPLACEMENTS[bouton.dataset.aller]());
  });
  elementCoups.addEventListener("click", (evenement) => {
    const bouton = evenement.target.closest("[data-coup]");
    if (bouton) aller(Number(bouton.dataset.coup));
  });

  const TOUCHES = { ArrowLeft: "precedent", ArrowRight: "suivant", Home: "debut", End: "fin" };
  const auClavier = (evenement) => {
    const action = TOUCHES[evenement.key];
    if (!action) return;
    evenement.preventDefault();
    DEPLACEMENTS[action]();
  };
  addEventListener("keydown", auClavier);
  aller(courant);
  return () => removeEventListener("keydown", auClavier);
}
