import { html, afficher, ouvrirDialogue } from "../html.js";
import { icone, LOGO_GOOGLE } from "../icones.js";
import { api, ErreurApi, URL_CONNEXION_GOOGLE } from "../api.js";
import { session, actualiserSession } from "../session.js";

const PSEUDO_VALIDE = /^[\w.-]{3,20}$/;
const EMAIL_VALIDE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const LONGUEUR_MINIMALE_MOT_DE_PASSE = 8;

const AIDE_PSEUDO = "De 3 à 20 caractères : lettres, chiffres, point, tiret ou tiret bas.";
const AIDE_MOT_DE_PASSE = `Au moins ${LONGUEUR_MINIMALE_MOT_DE_PASSE} caractères.`;

const erreurDuPseudo = (pseudo) => (PSEUDO_VALIDE.test(pseudo) ? "" : AIDE_PSEUDO);

const erreurDeLEmail = (email) => (EMAIL_VALIDE.test(email) ? "" : "Saisissez une adresse e-mail valide.");
const erreurDuMotDePasse = (motDePasse) => (motDePasse.length >= LONGUEUR_MINIMALE_MOT_DE_PASSE ? "" : `Votre mot de passe doit contenir ${AIDE_MOT_DE_PASSE.toLowerCase()}`);

const champ = ({ nom, libelle, type = "text", autocomplete, aide = "", valeur = "", inputmode = "" }) => html`
  <div class="champ">
    <label for="${nom}">${libelle}</label>
    <input id="${nom}" name="${nom}" type="${type}" autocomplete="${autocomplete}" value="${valeur}"
           ${inputmode ? html`inputmode="${inputmode}"` : ""} autocapitalize="none" spellcheck="false"
           aria-describedby="${nom}-message">
    <p class="${aide ? "aide" : "message-vide"}" id="${nom}-message" data-aide="${aide}">${aide}</p>
  </div>`;

const champMotDePasse = ({ nom, libelle, autocomplete, aide = "" }) => html`
  <div class="champ">
    <label for="${nom}">${libelle}</label>
    <div class="champ-mot-de-passe">
      <input id="${nom}" name="${nom}" type="password" autocomplete="${autocomplete}" autocapitalize="none" spellcheck="false"
             aria-describedby="${nom}-message">
      <button type="button" data-voir="${nom}" aria-label="Afficher le mot de passe" aria-pressed="false">${icone("oeil")}</button>
    </div>
    <p class="${aide ? "aide" : "message-vide"}" id="${nom}-message" data-aide="${aide}">${aide}</p>
  </div>`;

function afficherMessage(formulaire, nom, erreur) {
  const message = formulaire.querySelector(`#${nom}-message`);
  const aide = message.dataset.aide;
  formulaire.querySelector(`#${nom}`).setAttribute("aria-invalid", String(Boolean(erreur)));
  message.textContent = erreur || aide;
  message.className = erreur ? "erreur" : aide ? "aide" : "message-vide";
}

/** Vérifie chaque champ, affiche ses erreurs et place le curseur sur le premier à corriger. */
function valider(formulaire, regles) {
  const erreurs = Object.entries(regles).map(([nom, regle]) => {
    const erreur = regle(formulaire.elements[nom].value.trim());
    afficherMessage(formulaire, nom, erreur);
    return erreur ? nom : null;
  }).filter(Boolean);
  if (erreurs.length > 0) formulaire.elements[erreurs[0]].focus();
  return erreurs.length === 0;
}

function afficherAlerte(formulaire, message) {
  let alerte = formulaire.querySelector(".alerte-formulaire");
  if (!alerte) {
    alerte = document.createElement("p");
    alerte.className = "alerte-formulaire";
    alerte.setAttribute("role", "alert");
    formulaire.prepend(alerte);
  }
  alerte.textContent = message;
}

/** Le serveur a le dernier mot : ce qu'il refuse s'affiche en tête du formulaire. */
async function envoyer(formulaire, action) {
  const bouton = formulaire.querySelector('button[type="submit"]');
  bouton.disabled = true;
  try {
    await action();
  } catch (erreur) {
    if (!(erreur instanceof ErreurApi)) throw erreur;
    afficherAlerte(formulaire, erreur.message);
  } finally {
    bouton.disabled = false;
  }
}

function brancherFormulaire(formulaire, regles, action) {
  formulaire.addEventListener("submit", (evenement) => {
    evenement.preventDefault();
    if (valider(formulaire, regles)) envoyer(formulaire, action);
  });
  // Une erreur disparaît dès que le joueur corrige le champ.
  formulaire.addEventListener("input", ({ target }) => {
    if (target.getAttribute("aria-invalid") === "true") afficherMessage(formulaire, target.id, "");
  });
  formulaire.addEventListener("click", (evenement) => {
    const bouton = evenement.target.closest("[data-voir]");
    if (!bouton) return;
    const champSaisie = formulaire.elements[bouton.dataset.voir];
    const visible = champSaisie.type === "password";
    champSaisie.type = visible ? "text" : "password";
    bouton.setAttribute("aria-pressed", String(visible));
    bouton.setAttribute("aria-label", visible ? "Masquer le mot de passe" : "Afficher le mot de passe");
    afficher(bouton, icone(visible ? "oeilBarre" : "oeil"));
  });
}

const cadreAuthentification = ({ titre, sousTitre, corps, pied }) => html`
  <section class="auth">
    <a class="auth-retour" href="#/" aria-label="Retour à l'accueil">${icone("retour")}</a>
    <div class="auth-carte">
      <a class="marque" href="#/">Ca<span class="lettre-neon">ï</span>ssa</a>
      <h1>${titre}</h1>
      <p class="auth-sous-titre">${sousTitre}</p>
      ${corps}
      <p class="auth-pied">${pied}</p>
    </div>
  </section>`;

const boutonGoogle = () => session.googleDisponible && html`
  <div class="separateur" role="separator"><span>ou</span></div>
  <a class="bouton bouton-google" href="${URL_CONNEXION_GOOGLE}">${LOGO_GOOGLE} Continuer avec Google</a>`;

/**
 * Premier passage avec Google : Google a vérifié l'identité, il reste à choisir un pseudo et un mot de passe.
 * Le compte n'est créé qu'à la validation : annuler ne laisse aucun compte à moitié rempli.
 */
function ouvrirFinalisationGoogle(identite, surSucces) {
  const pseudoPropose = identite.email.split("@")[0].replace(/[^\w.-]/g, "").slice(0, 20);
  const feuille = ouvrirDialogue(html`
    <div class="feuille-corps">
      <h2>Finalisez votre compte</h2>
      <p class="discret">Dernière étape avant votre première partie.</p>
      <div class="identite-google">
        <span class="avatar">${(identite.nom || identite.email)[0].toUpperCase()}</span>
        <span class="rangee-texte"><strong>${identite.nom}</strong><small>${identite.email}</small></span>
        ${LOGO_GOOGLE}
      </div>
      <form id="finalisation" novalidate>
        ${champ({ nom: "pseudo-google", libelle: "Pseudo", autocomplete: "username", valeur: pseudoPropose, aide: AIDE_PSEUDO })}
        ${champMotDePasse({ nom: "mot-de-passe-google", libelle: "Mot de passe", autocomplete: "new-password", aide: `${AIDE_MOT_DE_PASSE} Il vous permettra aussi de vous connecter sans Google.` })}
        <button class="bouton bouton-principal bouton-large" type="submit">Terminer</button>
        <button class="bouton bouton-discret" type="button" data-annuler>Annuler</button>
      </form>
    </div>`);
  feuille.classList.add("feuille", "feuille-compte");
  feuille.setAttribute("aria-label", "Finalisez votre compte");
  feuille.tabIndex = -1;
  feuille.focus();
  let termine = false;
  feuille.addEventListener("close", () => {
    if (!termine) api.annulerGoogle().then(actualiserSession);
  });
  feuille.querySelector("[data-annuler]").addEventListener("click", () => feuille.close());
  const formulaire = feuille.querySelector("#finalisation");
  brancherFormulaire(formulaire, {
    "pseudo-google": erreurDuPseudo,
    "mot-de-passe-google": erreurDuMotDePasse,
  }, async () => {
    await api.finaliserGoogle({
      pseudo: formulaire.elements["pseudo-google"].value.trim(),
      motDePasse: formulaire.elements["mot-de-passe-google"].value,
    });
    termine = true;
    await actualiserSession();
    feuille.close();
    surSucces();
  });
}

export function vueConnexion(contenu, { parametres, aller }) {
  const echecGoogle = parametres.get("etat") === "google";
  afficher(contenu, cadreAuthentification({
    titre: "Connexion",
    sousTitre: "Retrouvez vos parties et votre classement.",
    corps: html`
      ${echecGoogle && html`<p class="alerte-formulaire" role="alert">La connexion avec Google n'a pas abouti. Réessayez.</p>`}
      <form id="formulaire" class="auth-formulaire" novalidate>
        ${champ({ nom: "identifiant", libelle: "Pseudo ou adresse e-mail", autocomplete: "username" })}
        ${champMotDePasse({ nom: "mot-de-passe", libelle: "Mot de passe", autocomplete: "current-password" })}
        <button class="bouton bouton-principal bouton-large" type="submit">Se connecter</button>
      </form>
      ${boutonGoogle()}`,
    pied: html`Pas encore de compte ? <a href="#/inscription">Créer un compte</a>`,
  }));
  const formulaire = contenu.querySelector("#formulaire");
  brancherFormulaire(formulaire, {
    identifiant: (valeur) => (valeur ? "" : "Saisissez votre pseudo ou votre adresse e-mail."),
    "mot-de-passe": (valeur) => (valeur ? "" : "Saisissez votre mot de passe."),
  }, async () => {
    await api.connecter({
      identifiant: formulaire.elements.identifiant.value.trim(),
      motDePasse: formulaire.elements["mot-de-passe"].value,
    });
    await actualiserSession();
    aller("#/salon");
  });
  if (session.identiteEnAttente) {
    ouvrirFinalisationGoogle(session.identiteEnAttente, () => aller("#/salon"));
  }
}

export function vueInscription(contenu, { aller }) {
  afficher(contenu, cadreAuthentification({
    titre: "Créer un compte",
    sousTitre: "Quelques secondes suffisent pour jouer.",
    corps: html`
      <form id="formulaire" class="auth-formulaire" novalidate>
        ${champ({ nom: "pseudo", libelle: "Pseudo", autocomplete: "username", aide: AIDE_PSEUDO })}
        ${champ({ nom: "email", libelle: "Adresse e-mail", type: "email", autocomplete: "email", inputmode: "email" })}
        ${champMotDePasse({ nom: "mot-de-passe", libelle: "Mot de passe", autocomplete: "new-password", aide: AIDE_MOT_DE_PASSE })}
        <button class="bouton bouton-principal bouton-large" type="submit">Créer mon compte</button>
      </form>
      ${boutonGoogle()}`,
    pied: html`Déjà un compte ? <a href="#/connexion">Se connecter</a>`,
  }));
  const formulaire = contenu.querySelector("#formulaire");
  brancherFormulaire(formulaire, {
    pseudo: erreurDuPseudo,
    email: erreurDeLEmail,
    "mot-de-passe": erreurDuMotDePasse,
  }, async () => {
    await api.inscrire({
      pseudo: formulaire.elements.pseudo.value.trim(),
      email: formulaire.elements.email.value.trim(),
      motDePasse: formulaire.elements["mot-de-passe"].value,
    });
    await actualiserSession();
    aller("#/salon");
  });
}
