import { html } from "./html.js";

// L'échiquier : il affiche une position et signale les coups demandés. Il ne connaît aucune règle :
// le serveur décide des coups permis et les lui donne par `destinations`.

const COLONNES = ["a", "b", "c", "d", "e", "f", "g", "h"];
const RANGEES = [8, 7, 6, 5, 4, 3, 2, 1];
const SEUIL_GLISSEMENT_PX = 6;
const DUREE_ANIMATION_MS = 170;

const NOMS_PIECES = { k: "roi", q: "dame", r: "tour", b: "fou", n: "cavalier", p: "pion" };

const estBlanche = (piece) => piece === piece.toUpperCase();

export function imagePiece(piece) {
  return `assets/pieces/${piece.toLowerCase()}${estBlanche(piece) ? "l" : "d"}t.svg`;
}

function nomPiece(piece) {
  return `${NOMS_PIECES[piece.toLowerCase()]} ${estBlanche(piece) ? "blanc" : "noir"}`;
}

/** Lit le placement d'une notation FEN : { e4: "P", ... }. */
export function lirePosition(placement) {
  const position = {};
  placement.split("/").forEach((rangee, indexRangee) => {
    let colonne = 0;
    for (const caractere of rangee) {
      if (/\d/.test(caractere)) {
        colonne += Number(caractere);
      } else {
        position[COLONNES[colonne] + RANGEES[indexRangee]] = caractere;
        colonne += 1;
      }
    }
  });
  return position;
}

const animationsPermises = () => !matchMedia("(prefers-reduced-motion: reduce)").matches;

export class Echiquier {
  constructor(conteneur, { orientation = "blanc", destinations = () => [], surCoup = () => {}, decor = false } = {}) {
    this.orientation = orientation;
    this.destinations = destinations;
    this.surCoup = surCoup;
    this.position = {};
    this.selection = null;
    this.dernierCoup = null;
    this.caseEnEchec = null;
    this.element = document.createElement("div");
    this.element.className = "echiquier";
    this.element.setAttribute("role", "group");
    this.element.setAttribute("aria-label", "Échiquier");
    conteneur.append(this.element);
    // Un décor n'a pas besoin de boutons : ni clic, ni clavier, et un bouton aplatirait sa mise en perspective.
    this.decor = decor;
    this.creerCases();
    if (!decor) this.ecouter();
  }

  creerCases() {
    const colonnes = this.orientation === "blanc" ? COLONNES : [...COLONNES].reverse();
    const rangees = this.orientation === "blanc" ? RANGEES : [...RANGEES].reverse();
    this.cases = new Map();
    for (const rangee of rangees) {
      for (const colonne of colonnes) {
        const nom = colonne + rangee;
        const bouton = document.createElement(this.decor ? "div" : "button");
        if (!this.decor) bouton.type = "button";
        bouton.dataset.case = nom;
        bouton.className = (COLONNES.indexOf(colonne) + rangee) % 2 === 1 ? "case" : "case claire";
        if (colonne === colonnes[0]) bouton.append(this.creerCoordonnee("coord-rangee", rangee));
        if (rangee === rangees[rangees.length - 1]) bouton.append(this.creerCoordonnee("coord-colonne", colonne));
        this.cases.set(nom, bouton);
        this.element.append(bouton);
      }
    }
  }

  creerCoordonnee(classe, texte) {
    const coordonnee = document.createElement("span");
    coordonnee.className = `coord ${classe}`;
    coordonnee.textContent = texte;
    coordonnee.setAttribute("aria-hidden", "true");
    return coordonnee;
  }

  /** Affiche une position ; `coup` anime la pièce qui vient de bouger. */
  afficherPosition(position, { dernierCoup = null, caseEnEchec = null, animer = false, dureeDuTrajet = DUREE_ANIMATION_MS } = {}) {
    this.position = position;
    this.dernierCoup = dernierCoup;
    this.caseEnEchec = caseEnEchec;
    this.selection = null;
    this.dessiner();
    if (animer && dernierCoup) this.animer(dernierCoup[0], dernierCoup[1], dureeDuTrajet);
  }

  dessiner() {
    const permises = this.selection ? this.destinations(this.selection) : [];
    let actif = false;
    for (const [nom, bouton] of this.cases) {
      const piece = this.position[nom];
      this.dessinerPiece(bouton, piece);
      const jouable = Boolean(piece) && this.destinations(nom).length > 0;
      actif ||= jouable;
      bouton.classList.toggle("jouable", jouable);
      bouton.classList.toggle("selection", nom === this.selection);
      bouton.classList.toggle("destination", permises.includes(nom));
      bouton.classList.toggle("occupee", permises.includes(nom) && Boolean(piece));
      bouton.classList.toggle("dernier", Boolean(this.dernierCoup?.includes(nom)));
      bouton.classList.toggle("echec", nom === this.caseEnEchec);
      if (!this.decor) bouton.setAttribute("aria-label", piece ? `${nom}, ${nomPiece(piece)}` : nom);
    }
    this.element.classList.toggle("actif", actif);
  }

  dessinerPiece(bouton, piece) {
    const image = bouton.querySelector("img");
    if (!piece) {
      image?.remove();
      return;
    }
    const source = imagePiece(piece);
    if (image?.getAttribute("src") === source) return;
    image?.remove();
    const nouvelle = document.createElement("img");
    nouvelle.src = source;
    nouvelle.alt = "";
    nouvelle.draggable = false;
    bouton.prepend(nouvelle);
  }

  animer(depart, arrivee, dureeDuTrajet = DUREE_ANIMATION_MS) {
    const image = this.cases.get(arrivee).querySelector("img");
    if (!image || !animationsPermises()) return;
    const origine = this.cases.get(depart);
    const fin = this.cases.get(arrivee);
    const decalage = `${origine.offsetLeft - fin.offsetLeft}px ${origine.offsetTop - fin.offsetTop}px`;
    // « translate » plutôt que « transform » : mesuré dans le plan de l'échiquier, le trajet reste juste
    // même quand l'échiquier est incliné, et la pièce garde sa propre transformation.
    image.animate({ translate: [decalage, "0 0"] }, { duration: dureeDuTrajet, easing: "ease-out" });
  }

  selectionner(nom) {
    this.selection = nom;
    this.dessiner();
  }

  jouer(depart, arrivee) {
    this.selection = null;
    this.dessiner();
    this.surCoup(depart, arrivee);
  }

  /** Un clic sur une case, au doigt, à la souris ou au clavier. */
  cliquer(nom) {
    if (this.selection && this.destinations(this.selection).includes(nom)) {
      this.jouer(this.selection, nom);
    } else if (nom !== this.selection && this.destinations(nom).length > 0) {
      this.selectionner(nom);
    } else if (this.selection) {
      this.selectionner(null);
    }
  }

  ecouter() {
    this.element.addEventListener("click", (evenement) => {
      const bouton = evenement.target.closest(".case");
      if (!bouton) return;
      if (this.glissementTermine) {
        this.glissementTermine = false;
        return;
      }
      this.cliquer(bouton.dataset.case);
    });
    this.element.addEventListener("pointerdown", (evenement) => this.commencerGlissement(evenement));
  }

  commencerGlissement(evenement) {
    const bouton = evenement.target.closest(".case");
    if (!bouton || evenement.button !== 0 || this.destinations(bouton.dataset.case).length === 0) return;
    const depart = bouton.dataset.case;
    const origine = { x: evenement.clientX, y: evenement.clientY };
    let tenue = null;

    const deplacer = (mouvement) => {
      const distance = Math.hypot(mouvement.clientX - origine.x, mouvement.clientY - origine.y);
      if (!tenue && distance < SEUIL_GLISSEMENT_PX) return;
      if (!tenue) {
        tenue = this.prendrePiece(bouton, depart);
      }
      mouvement.preventDefault();
      tenue.style.left = `${mouvement.clientX}px`;
      tenue.style.top = `${mouvement.clientY}px`;
    };
    const relacher = (fin) => {
      removeEventListener("pointermove", deplacer);
      removeEventListener("pointerup", relacher);
      removeEventListener("pointercancel", relacher);
      if (!tenue) return;
      tenue.remove();
      bouton.querySelector("img")?.style.removeProperty("visibility");
      this.glissementTermine = true;
      const cible = document.elementFromPoint(fin.clientX, fin.clientY)?.closest(".case");
      if (fin.type === "pointerup" && cible && this.destinations(depart).includes(cible.dataset.case)) {
        this.jouer(depart, cible.dataset.case);
      }
    };
    addEventListener("pointermove", deplacer, { passive: false });
    addEventListener("pointerup", relacher);
    addEventListener("pointercancel", relacher);
  }

  prendrePiece(bouton, depart) {
    this.selectionner(depart);
    const image = bouton.querySelector("img");
    const tenue = image.cloneNode();
    tenue.className = "piece-tenue";
    tenue.style.width = tenue.style.height = `${bouton.getBoundingClientRect().width}px`;
    image.style.visibility = "hidden";
    document.body.append(tenue);
    return tenue;
  }
}

/** Applique un déplacement simple : la position du coup suivant, pour la relecture. */
export function appliquerDeplacements(position, deplacements) {
  const suivante = { ...position };
  for (const [depart, arrivee] of deplacements) {
    suivante[arrivee] = suivante[depart];
    delete suivante[depart];
  }
  return suivante;
}

/** Une position figée, en petit : pour reconnaître une partie dans une liste. */
export function miniature(placement) {
  const position = lirePosition(placement);
  return html`<div class="miniature" aria-hidden="true">${RANGEES.flatMap((rangee) => COLONNES.map((colonne) => {
    const piece = position[colonne + rangee];
    return html`<span class="${(COLONNES.indexOf(colonne) + rangee) % 2 === 1 ? "" : "claire"}">${piece && html`<img src="${imagePiece(piece)}" alt="" loading="lazy">`}</span>`;
  }))}</div>`;
}
