import { html } from "./html.js";

// Icônes dessinées pour Caïssa, d'un seul trait : elles prennent la couleur du texte qui les entoure.
const TRACES = {
  jouer: html`<path d="M12 3a3 3 0 0 1 2 5.2c.7 3 2 5.3 3.5 7.3H6.5C8 13.5 9.3 11.2 10 8.2A3 3 0 0 1 12 3Z"/><path d="M5 20.5h14M6.5 15.5 5.5 20.5M17.5 15.5l1 5"/>`,
  parties: html`<rect x="4" y="4" width="16" height="16" rx="1.5"/><path d="M4 12h16M12 4v16M4 8h4M16 16h4M8 4v4M16 16v4"/>`,
  classements: html`<path d="M5 20v-6M12 20V5M19 20v-9"/><path d="M3 20.5h18"/>`,
  comptes: html`<circle cx="9" cy="8.5" r="3.5"/><path d="M3 20c.5-3.8 2.8-6 6-6s5.5 2.2 6 6"/><path d="M16 5.5a3.5 3.5 0 0 1 0 6.5M18 14.5c1.7.9 2.7 2.8 3 5.5"/>`,
  sortir: html`<path d="M14 4H6a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h8"/><path d="M11 12h10M17.5 8.5 21 12l-3.5 3.5"/>`,
  bullet: html`<path d="M4 20c0-5 6-13 16-16-3 10-11 16-16 16Z"/><path d="M9 11l4 4"/>`,
  blitz: html`<path d="M13.5 3 5.5 13.5H11L9.5 21l9-11.5H13L13.5 3Z"/>`,
  rapide: html`<circle cx="12" cy="13.5" r="7.5"/><path d="M12 9v4.5l3 2M9.5 2.5h5M12 2.5V6"/>`,
  ordinateur: html`<rect x="6" y="6" width="12" height="12" rx="1.5"/><rect x="9.5" y="9.5" width="5" height="5"/><path d="M9 2.5V6M15 2.5V6M9 18v3.5M15 18v3.5M2.5 9H6M2.5 15H6M18 9h3.5M18 15h3.5"/>`,
  defier: html`<path d="M4 20 15 9M15 3l6 6-3 1-4-4 1-3Z"/><path d="M4 16l4 4"/>`,
  personne: html`<circle cx="12" cy="8" r="4"/><path d="M4 21c.7-4.7 3.8-7.5 8-7.5s7.3 2.8 8 7.5"/>`,
  plus: html`<path d="M4 6.5h16M4 12h16M4 17.5h16"/>`,
  fermer: html`<path d="m6 6 12 12M18 6 6 18"/>`,
  oeil: html`<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/>`,
  oeilBarre: html`<path d="M4 4l16 16M9.9 5.8A9.5 9.5 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-3.1 3.9M6.3 7.5A16.5 16.5 0 0 0 2.5 12S6 18.5 12 18.5c1.8 0 3.400-.5 4.8-1.3M10 10a3 3 0 0 0 4 4"/>`,
  chevron: html`<path d="m6 9 6 6 6-6"/>`,
  suite: html`<path d="m9 6 6 6-6 6"/>`,
  retour: html`<path d="m15 6-6 6 6 6"/>`,
  recherche: html`<circle cx="11" cy="11" r="6.5"/><path d="m16 16 5 5"/>`,
};

export const LOGO_GOOGLE = html`<svg viewBox="0 0 48 48" aria-hidden="true">
  <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
  <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
  <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
  <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
</svg>`;

export function icone(nom) {
  return html`<svg class="icone" viewBox="0 0 24 24" aria-hidden="true">${TRACES[nom]}</svg>`;
}

export const iconeFamille = (famille) => icone(famille.toLowerCase());

/** « 5+0 » devient « 5 min », « 10+5 » devient « 10 min + 5 s ». */
/** Plus court, pour une grille étroite : « 5+0 » devient « 5 min », « 10+5 » devient « 10 | 5 ». */
export function libelleCourt(cadence) {
  const [minutes, increment] = cadence.split("+");
  return Number(increment) === 0 ? `${minutes} min` : `${minutes} | ${increment}`;
}

export function libelleCadence(cadence) {
  const [minutes, increment] = cadence.split("+");
  return Number(increment) === 0 ? `${minutes} min` : `${minutes} min + ${increment} s`;
}
