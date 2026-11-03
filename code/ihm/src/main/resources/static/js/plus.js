import { html, afficher } from "./html.js";
import { icone } from "./icones.js";

// TEMPORAIRE : la liste des écrans à valider. Elle disparaîtra avec les données d'exemple ;
// la feuille gardera alors seulement la partie « Votre compte ».
const GROUPES_ECRANS = [
  { nom: "Jouer", image: "jouer", ecrans: [
    ["#/salon", "Accueil des parties"],
    ["#/salon?feuille=ouverte", "Formulaire ouvert"],
    ["#/salon?type=ordinateur", "Contre l'ordinateur"],
    ["#/salon?vue=parties", "Toutes les parties ouvertes"],
    ["#/salon?etat=attente", "Partie en attente"],
    ["#/salon?etat=invitation", "Invitation reçue"],
    ["#/salon?etat=vide", "Aucune partie ouverte"],
  ] },
  { nom: "Partie", image: "parties", ecrans: [
    ["#/partie", "Partie en cours"],
    ["#/partie?apercu=temps", "Temps presque écoulé"],
    ["#/partie?apercu=nulle", "Nulle proposée"],
    ["#/partie?apercu=promotion", "Promotion d'un pion"],
    ["#/partie?apercu=fin", "Fin de partie"],
    ["#/partie?apercu=ordinateur", "Contre l'ordinateur"],
  ] },
  { nom: "Historique", image: "classements", ecrans: [
    ["#/parties", "Mes parties"],
    ["#/parties?etat=vide", "Aucune partie terminée"],
    ["#/parties/opera", "Revoir une partie"],
    ["#/classements", "Mes classements"],
  ] },
];

/** Ouvre la feuille « Plus » : le compte, puis les écrans à valider. */
export function ouvrirPlus(joueur, adresse) {
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
      <section aria-labelledby="titre-ecrans">
        <h3 id="titre-ecrans">Aperçu des écrans</h3>
        <p class="aide">Ces écrans montrent encore des données d'exemple.</p>
        ${GROUPES_ECRANS.map(({ nom, image, ecrans }) => html`
          <details class="groupe-ecrans" ${ecrans.some(([cible]) => cible === adresse) ? html`open` : ""}>
            <summary>${icone(image)} <span>${nom}</span> ${icone("chevron")}</summary>
            <ul>
              ${ecrans.map(([cible, libelle]) => html`
                <li><a href="${cible}" ${cible === adresse ? html`aria-current="page"` : ""}>${libelle}</a></li>`)}
            </ul>
          </details>`)}
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
