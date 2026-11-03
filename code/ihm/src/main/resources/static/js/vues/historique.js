import { html, afficher } from "../html.js";
import { api } from "../api.js";
import { Echiquier, lirePosition, miniature } from "../echiquier.js";
import { icone, iconeFamille, libelleCadence } from "../icones.js";
import { POSITION_INITIALE } from "../donnees.js";
import { session } from "../session.js";

export const NOMS_FAMILLES = { BULLET: "Bullet", BLITZ: "Blitz", RAPIDE: "Rapide" };
const FAMILLE_ORDINATEUR = "Ordinateur";
const FILTRES = ["Toutes", "Bullet", "Blitz", "Rapide", FAMILLE_ORDINATEUR];
const NOM_ORDINATEUR = "Ordinateur";
const LARGEUR_COURBE = 600;
const HAUTEUR_COURBE = 180;
const MARGE_COURBE = 12;

const SCORES = { VICTOIRE_BLANCS: ["1", "0"], VICTOIRE_NOIRS: ["0", "1"], NULLE: ["½", "½"] };
const MOTIFS = {
  MAT: "mat",
  PAT: "pat",
  ABANDON: "abandon",
  TEMPS_ECOULE: "temps écoulé",
  ACCORD: "accord",
  TRIPLE_REPETITION: "triple répétition",
  CINQUANTE_COUPS: "règle des 50 coups",
  MATERIEL_INSUFFISANT: "matériel insuffisant",
};
const FORMAT_DATE = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "short", year: "numeric" });

const familleDe = (partie) => NOMS_FAMILLES[partie.categorie] ?? FAMILLE_ORDINATEUR;
const placement = (position) => position.split(" ")[0];
const nomDuCamp = (camp) => camp.pseudo ?? NOM_ORDINATEUR;

function issue(partie) {
  if (partie.resultat === "NULLE") return "nulle";
  const gagnant = partie.resultat === "VICTOIRE_BLANCS" ? partie.blancs : partie.noirs;
  return gagnant.pseudo === session.joueur.pseudo ? "gagnee" : "perdue";
}

const SIGNES = { gagnee: "+", perdue: "−", nulle: "=" };
const NOMS_ISSUES = { gagnee: "Partie gagnée", perdue: "Partie perdue", nulle: "Partie nulle" };

function texteVariation(variation) {
  if (variation === null || variation === undefined) return "";
  return variation > 0 ? `+${variation}` : `${variation}`.replace("-", "−");
}

const ligneJoueur = (camp, force, couleur) => {
  const classement = camp.classementApres ?? (camp.pseudo ? null : force);
  return html`
    <span class="joueur-ligne">
      <span class="carre-couleur ${couleur}" aria-hidden="true"></span>
      <strong>${nomDuCamp(camp)}</strong>
      ${classement && html`<span class="discret">(${classement})</span>`}
      ${camp.variation !== null && html`<span class="${camp.variation >= 0 ? "variation-plus" : "variation-moins"}">${texteVariation(camp.variation)}</span>`}
    </span>`;
};

export const lignePartie = (partie) => {
  const fin = issue(partie);
  const [scoreBlancs, scoreNoirs] = SCORES[partie.resultat];
  const famille = familleDe(partie);
  return html`
    <li>
      <a class="ligne-partie" href="#/parties/${partie.id}">
        ${miniature(placement(partie.positionFinale))}
        <div class="ligne-cadence">
          ${iconeFamille(famille)}
          <span>${partie.cadence ? libelleCadence(partie.cadence) : "Sans pendule"}</span>
        </div>
        <div class="ligne-joueurs">
          ${ligneJoueur(partie.blancs, partie.forceOrdinateur, "blanc")}
          ${ligneJoueur(partie.noirs, partie.forceOrdinateur, "noir")}
          <p class="discret">${partie.nombreCoups} coups, ${MOTIFS[partie.motif]}</p>
        </div>
        <div class="ligne-resultat">
          <span class="score"><span>${scoreBlancs}</span><span>${scoreNoirs}</span></span>
          <span class="badge-issue ${fin}" title="${NOMS_ISSUES[fin]}"><span class="visuellement-masque">${NOMS_ISSUES[fin]}</span><span aria-hidden="true">${SIGNES[fin]}</span></span>
        </div>
        <time class="ligne-date discret">${FORMAT_DATE.format(new Date(partie.termineeLe))}</time>
      </a>
    </li>`;
};

const erreurDeChargement = () => html`<div class="vide vide-cadre"><p>Le serveur n'a pas répondu.</p><p>Rechargez la page pour réessayer.</p></div>`;

export function vueParties(contenu) {
  let actif = true;
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
  let parties = [];
  const dessiner = (filtre) => {
    const visibles = filtre === FILTRES[0] ? parties : parties.filter((partie) => familleDe(partie) === filtre);
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
  api.mesParties().then((reponse) => {
    if (!actif) return;
    parties = reponse;
    dessiner(FILTRES[0]);
  }).catch(() => afficher(emplacement, erreurDeChargement()));
  return () => { actif = false; };
}

/** La courbe du classement : chaque point est le classement après une partie. */
function courbe(evolution) {
  if (evolution.length < 2) {
    return html`<div class="vide"><p>Pas encore de courbe.</p><p>Elle se dessinera après votre première partie classée.</p></div>`;
  }
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
           aria-label="Classement sur les ${evolution.length} derniers relevés : de ${evolution[0]} à ${evolution.at(-1)}">
        <polyline points="${trace}" vector-effect="non-scaling-stroke"></polyline>
      </svg>
    </div>`;
}

const totalDe = ({ victoires, nulles, defaites }) => victoires + nulles + defaites;

const detailFamille = (bilan) => {
  const total = totalDe(bilan);
  const part = (nombre) => `${total === 0 ? 0 : (nombre * 100) / total}%`;
  const famille = NOMS_FAMILLES[bilan.categorie];
  return html`
    <div class="detail-famille">
      <div>
        <h2 class="section-titre">Évolution en ${famille.toLowerCase()}</h2>
        ${courbe(bilan.evolution)}
      </div>
      <div>
        <h2 class="section-titre">Bilan</h2>
        <dl class="bilan-famille">
          <div><dt>Classement actuel</dt><dd>${session.joueur.classements[bilan.categorie]}</dd></div>
          <div><dt>Meilleur classement</dt><dd>${bilan.meilleurClassement}</dd></div>
          <div><dt>Parties jouées</dt><dd>${total}</dd></div>
        </dl>
        ${total > 0 && html`<div class="barre-bilan" role="img" aria-label="${bilan.victoires} victoires, ${bilan.nulles} nulles, ${bilan.defaites} défaites">
          <span class="gagnee" style="width:${part(bilan.victoires)}"></span>
          <span class="nulle" style="width:${part(bilan.nulles)}"></span>
          <span class="perdue" style="width:${part(bilan.defaites)}"></span>
        </div>`}
      </div>
    </div>`;
};

export function vueClassements(contenu) {
  let actif = true;
  afficher(contenu, html`<section class="page page-large"><div id="classements"></div></section>`);
  const zone = contenu.querySelector("#classements");
  api.bilans().then((bilans) => {
    if (!actif) return;
    const total = bilans.reduce((somme, bilan) => somme + totalDe(bilan), 0);
    afficher(zone, html`
      <header class="profil">
        <span class="avatar avatar-grand" aria-hidden="true">${session.joueur.pseudo[0].toUpperCase()}</span>
        <div>
          <h1>${session.joueur.pseudo}</h1>
          <p class="discret">${total} ${total > 1 ? "parties classées" : "partie classée"}</p>
        </div>
      </header>
      <div class="cadre">
        <table class="tableau tableau-cadre tableau-classements">
          <thead><tr><th>Cadence</th><th>Classement</th><th>Parties</th><th>Victoires</th><th>Nulles</th><th>Défaites</th></tr></thead>
          <tbody>
            ${bilans.map((bilan, indice) => html`
              <tr data-famille="${indice}">
                <td><button class="bouton-ligne" type="button" aria-pressed="false">${iconeFamille(NOMS_FAMILLES[bilan.categorie])} ${NOMS_FAMILLES[bilan.categorie]}</button></td>
                <td data-etiquette="Classement" class="chiffre-fort">${session.joueur.classements[bilan.categorie]}</td>
                <td data-etiquette="Parties">${totalDe(bilan)}</td>
                <td data-etiquette="Victoires">${bilan.victoires}</td>
                <td data-etiquette="Nulles">${bilan.nulles}</td>
                <td data-etiquette="Défaites">${bilan.defaites}</td>
              </tr>`)}
          </tbody>
        </table>
        <div id="detail"></div>
      </div>`);
    const detail = zone.querySelector("#detail");
    const choisir = (indice) => {
      afficher(detail, detailFamille(bilans[indice]));
      zone.querySelectorAll("[data-famille]").forEach((ligne) => {
        const choisie = Number(ligne.dataset.famille) === indice;
        ligne.classList.toggle("selectionnee", choisie);
        ligne.querySelector("button").setAttribute("aria-pressed", String(choisie));
      });
    };
    zone.querySelectorAll("[data-famille]").forEach((ligne) => {
      ligne.addEventListener("click", () => choisir(Number(ligne.dataset.famille)));
    });
    // La famille la plus jouée s'ouvre d'abord : c'est celle qui a le plus à montrer.
    const plusJouee = bilans.reduce((meilleur, bilan, indice) => (totalDe(bilan) > totalDe(bilans[meilleur]) ? indice : meilleur), 0);
    choisir(plusJouee);
  }).catch(() => afficher(zone, erreurDeChargement()));
  return () => { actif = false; };
}

export function vueRevoir(contenu, { chemin, aller }) {
  const id = chemin.split("/").at(-1);
  let actif = true;
  let retirerClavier = () => {};
  api.partieArchivee(id).then((partie) => {
    if (!actif) return;
    retirerClavier = dessinerRelecture(contenu, partie);
  }).catch(() => aller("#/parties"));
  return () => {
    actif = false;
    retirerClavier();
  };
}

function dessinerRelecture(contenu, partie) {
  const positions = [lirePosition(POSITION_INITIALE), ...partie.coups.map((coup) => lirePosition(placement(coup.positionApres)))];
  let courant = positions.length - 1;
  const titre = `${nomDuCamp(partie.blancs)} contre ${nomDuCamp(partie.noirs)}`;
  const [scoreBlancs, scoreNoirs] = SCORES[partie.resultat];
  const sousTitre = `${scoreBlancs}-${scoreNoirs}, ${MOTIFS[partie.motif]}, ${FORMAT_DATE.format(new Date(partie.termineeLe))}`;

  afficher(contenu, html`
    <section class="partie relecture">
      <header class="entete-relecture" style="grid-area:adversaire">
        <h1>${titre}</h1>
        <p class="discret">${sousTitre}</p>
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

  const orientation = partie.noirs.pseudo === session.joueur.pseudo ? "noir" : "blanc";
  const echiquier = new Echiquier(contenu.querySelector("#plateau"), { orientation });
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
    const coup = courant > 0 ? partie.coups[courant - 1] : null;
    echiquier.afficherPosition(positions[courant], { dernierCoup: coup && [coup.depart, coup.arrivee], animer });
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
