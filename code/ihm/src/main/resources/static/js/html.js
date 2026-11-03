// Gabarits HTML : toute valeur insérée est échappée par défaut, car un pseudo vient d'un joueur
// et pourrait contenir du code. Seul un gabarit déjà échappé passe tel quel.

const ENTITES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };

class Fragment {
  constructor(texte) {
    this.texte = texte;
  }
  toString() {
    return this.texte;
  }
}

function echapper(valeur) {
  if (valeur instanceof Fragment) return valeur.texte;
  if (Array.isArray(valeur)) return valeur.map(echapper).join("");
  if (valeur === null || valeur === undefined || valeur === false) return "";
  return String(valeur).replace(/[&<>"']/g, (caractere) => ENTITES[caractere]);
}

export function html(chaines, ...valeurs) {
  return new Fragment(chaines.reduce((texte, chaine, index) => texte + echapper(valeurs[index - 1]) + chaine));
}

export function afficher(element, fragment) {
  element.innerHTML = fragment.toString();
}

/** Ouvre une boîte de dialogue et la retire du document à sa fermeture. */
export function ouvrirDialogue(fragment) {
  const dialogue = document.createElement("dialog");
  afficher(dialogue, fragment);
  document.body.append(dialogue);
  dialogue.addEventListener("close", () => dialogue.remove());
  dialogue.showModal();
  return dialogue;
}
