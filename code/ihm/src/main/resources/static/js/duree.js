// Les durées affichées : pendules, réponse attendue, expiration d'une partie.

const MILLISECONDES_PAR_SECONDE = 1000;
const SECONDES_PAR_MINUTE = 60;
const INTERVALLE_DES_DECOMPTES_MS = 250;

export function formaterTemps(millisecondes) {
  const secondes = Math.ceil(Math.max(millisecondes, 0) / MILLISECONDES_PAR_SECONDE);
  const minutes = Math.floor(secondes / SECONDES_PAR_MINUTE);
  return `${minutes}:${String(secondes % SECONDES_PAR_MINUTE).padStart(2, "0")}`;
}

/**
 * Tout élément portant data-reste (en millisecondes) affiche un décompte jusqu'à zéro.
 * Le serveur envoie une durée et non une heure : l'horloge du téléphone peut être décalée de la sienne.
 * Une seule minuterie pour toute la page : une liste redessinée n'a rien à arrêter.
 */
export function suivreLesDecomptes() {
  const mettreAJour = () => {
    const maintenant = performance.now();
    document.querySelectorAll("[data-reste]").forEach((element) => {
      element.dataset.fin ??= String(maintenant + Number(element.dataset.reste));
      element.textContent = formaterTemps(Number(element.dataset.fin) - maintenant);
    });
  };
  mettreAJour();
  setInterval(mettreAJour, INTERVALLE_DES_DECOMPTES_MS);
}
