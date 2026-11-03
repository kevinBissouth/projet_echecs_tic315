import { html, afficher } from "./html.js";
import { icone } from "./icones.js";

/** Ouvre la feuille « Plus » : le compte du joueur. */
export function ouvrirPlus(joueur) {
  const feuille = document.createElement("dialog");
  feuille.className = "feuille feuille-plus";
  feuille.setAttribute("aria-label", "Plus");
  feuille.tabIndex = -1;
  afficher(feuille, html`
    <button class="feuille-fermer" type="button" aria-label="Fermer">${icone("fermer")}</button>
    <div class="feuille-corps">
      <h2>Plus</h2>
      <section aria-labelledby="titre-compte">
        <h3 id="titre-compte">Votre compte</h3>
        <ul class="rangees-plus">
          <li>
            <a class="rangee-plus" href="#/classements">
              <span class="avatar">${joueur.pseudo[0].toUpperCase()}</span>
              <span class="rangee-texte"><strong>${joueur.pseudo}</strong><small>Voir mes classements</small></span>
              ${icone("suite")}
            </a>
          </li>
          ${joueur.administrateur && html`
            <li><a class="rangee-plus" href="#/administration">${icone("comptes")} <span class="rangee-texte"><strong>Gestion des comptes</strong></span> ${icone("suite")}</a></li>`}
          <li><button class="rangee-plus rangee-sortie" type="button" data-deconnexion>${icone("sortir")} <span class="rangee-texte"><strong>Se déconnecter</strong></span></button></li>
        </ul>
      </section>
    </div>`);
  document.body.append(feuille);
  feuille.addEventListener("close", () => feuille.remove());
  feuille.addEventListener("click", (evenement) => {
    const surFond = evenement.target === feuille;
    if (surFond || evenement.target.closest(".feuille-fermer, a, [data-deconnexion]")) feuille.close();
  });
  feuille.showModal();
  feuille.focus();
}
