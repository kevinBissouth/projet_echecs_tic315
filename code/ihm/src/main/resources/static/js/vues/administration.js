import { html, afficher, ouvrirDialogue } from "../html.js";
import { icone } from "../icones.js";
import { api, ErreurApi } from "../api.js";
import { session } from "../session.js";

const estAdministrateur = (compte) => compte.administrateur;

const FILTRES = [
  { valeur: "tous", nom: "Tous", garde: () => true },
  { valeur: "actifs", nom: "Actifs", garde: (compte) => !compte.suspendu },
  { valeur: "suspendus", nom: "Suspendus", garde: (compte) => compte.suspendu },
  { valeur: "administrateurs", nom: "Administrateurs", garde: estAdministrateur },
];

function actionsCompte(compte) {
  if (estAdministrateur(compte)) {
    return html`<span class="discret">Un administrateur ne peut pas être suspendu</span>`;
  }
  return html`
    <span class="actions-ligne">
      ${compte.suspendu
        ? html`<button class="bouton bouton-petit" type="button" data-action="reactiver" data-pseudo="${compte.pseudo}">Réactiver</button>`
        : html`
          <button class="bouton bouton-petit" type="button" data-action="nommer" data-pseudo="${compte.pseudo}">Nommer administrateur</button>
          <button class="bouton bouton-danger bouton-petit" type="button" data-action="suspendre" data-pseudo="${compte.pseudo}">Suspendre</button>`}
    </span>`;
}

const ligneCompte = (compte) => html`
  <tr>
    <td class="pseudo"><span class="avatar">${compte.pseudo[0].toUpperCase()}</span> ${compte.pseudo}${compte.pseudo === session.joueur.pseudo && html` <span class="discret">(vous)</span>`}</td>
    <td class="discret">${compte.email}</td>
    <td data-etiquette="Rôle">${compte.administrateur ? "Administrateur" : "Joueur"}</td>
    <td data-etiquette="État"><span class="pastille ${compte.suspendu ? "suspendu" : ""}">${compte.suspendu ? "Suspendu" : "Actif"}</span></td>
    <td>${actionsCompte(compte)}</td>
  </tr>`;

const CONFIRMATIONS = {
  suspendre: {
    titre: (pseudo) => `Suspendre ${pseudo} ?`,
    explication: "Ce joueur ne pourra plus jouer. Sa partie en cours est perdue par abandon, et sa partie en attente est annulée.",
    action: "Suspendre",
    danger: true,
  },
  reactiver: {
    titre: (pseudo) => `Réactiver ${pseudo} ?`,
    explication: "Ce joueur pourra de nouveau jouer.",
    action: "Réactiver",
  },
  nommer: {
    titre: (pseudo) => `Nommer ${pseudo} administrateur ?`,
    explication: "Ce compte pourra gérer les comptes, mais il ne pourra plus jouer de parties.",
    action: "Nommer administrateur",
  },
};

const ACTIONS = { suspendre: api.suspendre, reactiver: api.reactiver, nommer: api.nommer };

export function vueAdministration(contenu) {
  let liste = [];
  let filtre = FILTRES[0];
  let recherche = "";

  afficher(contenu, html`
    <section class="page page-large">
      <h1 class="titre-page">${icone("comptes")} Gestion des comptes</h1>
      <div class="cadre">
        <div class="cadre-outils">
          <div class="onglets-filtre" role="group" aria-label="Filtrer les comptes">
            ${FILTRES.map(({ valeur, nom }, indice) => html`<button type="button" data-filtre="${valeur}" aria-pressed="${indice === 0}">${nom}</button>`)}
          </div>
          <label class="recherche">
            ${icone("recherche")}
            <span class="visuellement-masque">Rechercher un compte</span>
            <input id="recherche" type="search" placeholder="Pseudo ou adresse e-mail" autocomplete="off">
          </label>
        </div>
        <div id="liste-comptes"></div>
      </div>
    </section>`);

  const emplacement = contenu.querySelector("#liste-comptes");

  const dessinerListe = () => {
    const visibles = liste.filter(filtre.garde).filter(({ pseudo, email }) => `${pseudo} ${email}`.toLowerCase().includes(recherche));
    afficher(emplacement, visibles.length === 0
      ? html`<div class="vide vide-cadre"><p>Aucun compte ne correspond à cette recherche.</p></div>`
      : html`<table class="tableau tableau-cadre tableau-comptes">
          <thead><tr><th>Pseudo</th><th>Adresse e-mail</th><th>Rôle</th><th>État</th><th><span class="visuellement-masque">Actions</span></th></tr></thead>
          <tbody>${visibles.map(ligneCompte)}</tbody>
        </table>`);
  };

  const charger = async () => {
    liste = await api.comptes();
    dessinerListe();
  };

  /** Le serveur vérifie le rôle à chaque action : son refus s'affiche, et la liste repart de son état réel. */
  const executer = async (action, pseudo) => {
    try {
      await ACTIONS[action](pseudo);
      await charger();
    } catch (erreur) {
      if (!(erreur instanceof ErreurApi)) throw erreur;
      emplacement.querySelector(".alerte-formulaire")?.remove();
      const alerte = document.createElement("p");
      alerte.className = "alerte-formulaire";
      alerte.setAttribute("role", "alert");
      alerte.textContent = erreur.message;
      emplacement.prepend(alerte);
    }
  };

  contenu.querySelectorAll("[data-filtre]").forEach((bouton) => {
    bouton.addEventListener("click", () => {
      filtre = FILTRES.find(({ valeur }) => valeur === bouton.dataset.filtre);
      contenu.querySelectorAll("[data-filtre]").forEach((autre) => autre.setAttribute("aria-pressed", String(autre === bouton)));
      dessinerListe();
    });
  });
  contenu.querySelector("#recherche").addEventListener("input", (evenement) => {
    recherche = evenement.target.value.trim().toLowerCase();
    dessinerListe();
  });
  emplacement.addEventListener("click", (evenement) => {
    const bouton = evenement.target.closest("[data-action]");
    if (!bouton) return;
    const { action, pseudo } = bouton.dataset;
    const confirmation = CONFIRMATIONS[action];
    const dialogue = ouvrirDialogue(html`
      <h2>${confirmation.titre(pseudo)}</h2>
      <p class="discret">${confirmation.explication}</p>
      <div class="pied">
        <button class="bouton" type="button" data-fermer>Conserver le compte tel quel</button>
        <button class="bouton ${confirmation.danger ? "bouton-danger" : "bouton-principal"}" type="button" data-confirmer>${confirmation.action}</button>
      </div>`);
    dialogue.querySelector("[data-fermer]").addEventListener("click", () => dialogue.close());
    dialogue.querySelector("[data-confirmer]").addEventListener("click", () => {
      dialogue.close();
      executer(action, pseudo);
    });
  });

  charger().catch(() => afficher(emplacement, html`<div class="vide vide-cadre"><p>Le serveur n'a pas répondu.</p><p>Rechargez la page pour réessayer.</p></div>`));
}
