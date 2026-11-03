// TEMPORAIRE : données d'exemple, le temps de valider les écrans. Elles disparaîtront quand les vues
// appelleront le serveur ; aucune autre partie du site n'en dépend pour fonctionner.

export const POSITION_INITIALE = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";

export const joueurConnecte = { pseudo: "a_fotso", estAdministrateur: true };

export const CADENCES = [
  { famille: "Bullet", cadences: ["1+0", "2+1"] },
  { famille: "Blitz", cadences: ["3+0", "3+2", "5+0"] },
  { famille: "Rapide", cadences: ["10+0", "10+5", "15+10"] },
];

export const partiesOuvertes = [
  { pseudo: "ndongo.p", classement: 1612, cadence: "5+0", famille: "Blitz", couleur: "Au hasard" },
  { pseudo: "claire_b", classement: 1488, cadence: "10+5", famille: "Rapide", couleur: "Blancs" },
  { pseudo: "ibrahim.k", classement: 1730, cadence: "3+2", famille: "Blitz", couleur: "Noirs" },
  { pseudo: "mbarga_j", classement: 1395, cadence: "1+0", famille: "Bullet", couleur: "Au hasard" },
];

export const profil = { depuis: "14 octobre 2026" };

export const classements = [
  { famille: "Bullet", points: 1480, meilleur: 1502, victoires: 6, nulles: 1, defaites: 7, evolution: [1500, 1491, 1502, 1494, 1487, 1496, 1489, 1478, 1486, 1475, 1483, 1480] },
  { famille: "Blitz", points: 1523, meilleur: 1531, victoires: 19, nulles: 4, defaites: 14, evolution: [1500, 1488, 1497, 1505, 1496, 1509, 1518, 1507, 1516, 1531, 1514, 1523] },
  { famille: "Rapide", points: 1547, meilleur: 1547, victoires: 12, nulles: 3, defaites: 6, evolution: [1500, 1509, 1502, 1514, 1521, 1513, 1526, 1534, 1528, 1539, 1535, 1547] },
];

export const classementJoueur = (famille) => classements.find((classement) => classement.famille === famille).points;

const moi = (classement, variation) => ({ pseudo: "a_fotso", classement, variation });
const lui = (pseudo, classement, variation) => ({ pseudo, classement, variation });

export const partiesTerminees = [
  { id: "opera", date: "2 nov. 2026", famille: "Blitz", cadence: "5+0", blancs: moi(1523, "+9"), noirs: lui("ibrahim.k", 1730, "-9"), resultat: "1-0", coups: 33, motif: "Mat",
    position: "1n1Rkb1r/p4ppp/4q3/4p1B1/4P3/8/PPP2PPP/2K5" },
  { id: "opera", date: "1 nov. 2026", famille: "Rapide", cadence: "10+5", blancs: lui("claire_b", 1488, "-1"), noirs: moi(1547, "+1"), resultat: "½-½", coups: 61, motif: "Accord",
    position: "8/5pk1/6p1/3R4/3r4/6P1/5PK1/8" },
  { id: "opera", date: "31 oct. 2026", famille: "Ordinateur", cadence: null, blancs: moi(null, null), noirs: lui("Ordinateur", 1600, null), resultat: "0-1", coups: 42, motif: "Abandon",
    position: "r4rk1/pp3ppp/2n5/3q4/3P4/2P2N2/P4PPP/R2Q1RK1" },
  { id: "opera", date: "29 oct. 2026", famille: "Blitz", cadence: "3+2", blancs: lui("ndongo.p", 1612, "+11"), noirs: moi(1514, "-11"), resultat: "1-0", coups: 74, motif: "Temps écoulé",
    position: "6k1/5ppp/8/8/1R6/6P1/r4P1P/6K1" },
  { id: "opera", date: "27 oct. 2026", famille: "Bullet", cadence: "1+0", blancs: moi(1475, "+8"), noirs: lui("mbarga_j", 1395, "-8"), resultat: "1-0", coups: 28, motif: "Mat",
    position: "r1bq1rk1/pppp1Qpp/2n2n2/2b1p3/2B1P3/8/PPPP1PPP/RNB1K1NR" },
];

export const comptes = [
  { pseudo: "a_fotso", email: "a.fotso@exemple.cm", role: "Administrateur", suspendu: false },
  { pseudo: "claire_b", email: "claire.b@exemple.cm", role: "Joueur", suspendu: false },
  { pseudo: "ibrahim.k", email: "ibrahim.k@exemple.cm", role: "Joueur", suspendu: false },
  { pseudo: "mbarga_j", email: "mbarga.j@exemple.cm", role: "Joueur", suspendu: true },
  { pseudo: "ndongo.p", email: "ndongo.p@exemple.cm", role: "Joueur", suspendu: false },
];

// La partie de l'Opéra : Morphy contre le duc de Brunswick et le comte Isouard, Paris, 1858.
const d = (notation, ...deplacements) => ({ notation, deplacements });
export const partieOpera = {
  titre: "Morphy contre Brunswick et Isouard",
  sousTitre: "Paris, 1858. Les blancs gagnent par mat.",
  coups: [
    d("e4", ["e2", "e4"]), d("e5", ["e7", "e5"]),
    d("Cf3", ["g1", "f3"]), d("d6", ["d7", "d6"]),
    d("d4", ["d2", "d4"]), d("Fg4", ["c8", "g4"]),
    d("dxe5", ["d4", "e5"]), d("Fxf3", ["g4", "f3"]),
    d("Dxf3", ["d1", "f3"]), d("dxe5", ["d6", "e5"]),
    d("Fc4", ["f1", "c4"]), d("Cf6", ["g8", "f6"]),
    d("Db3", ["f3", "b3"]), d("De7", ["d8", "e7"]),
    d("Cc3", ["b1", "c3"]), d("c6", ["c7", "c6"]),
    d("Fg5", ["c1", "g5"]), d("b5", ["b7", "b5"]),
    d("Cxb5", ["c3", "b5"]), d("cxb5", ["c6", "b5"]),
    d("Fxb5+", ["c4", "b5"]), d("Cbd7", ["b8", "d7"]),
    d("O-O-O", ["e1", "c1"], ["a1", "d1"]), d("Td8", ["a8", "d8"]),
    d("Txd7", ["d1", "d7"]), d("Txd7", ["d8", "d7"]),
    d("Td1", ["h1", "d1"]), d("De6", ["e7", "e6"]),
    d("Fxd7+", ["b5", "d7"]), d("Cxd7", ["f6", "d7"]),
    d("Db8+", ["b3", "b8"]), d("Cxb8", ["d7", "b8"]),
    d("Td8#", ["d1", "d8"]),
  ],
};

// Une partie en cours : une espagnole, aux blancs de jouer.
export const partieEnCours = {
  position: "r1bqkb1r/1ppp1ppp/p1n2n2/4p3/B3P3/5N2/PPPP1PPP/RNBQK2R",
  dernierCoup: ["g8", "f6"],
  adversaire: { pseudo: "ibrahim.k", classement: 1730 },
  joueur: { pseudo: "a_fotso", classement: 1523 },
  tempsAdversaire: 268,
  tempsJoueur: 251,
  cadence: "5+0, blitz",
  coups: ["e4", "e5", "Cf3", "Cc6", "Fb5", "a6", "Fa4", "Cf6"],
  destinations: {
    a4: ["b3", "b5", "c6"], b1: ["a3", "c3"], d1: ["e2"], e1: ["e2", "f1", "g1"], f3: ["d4", "e5", "g1", "g5", "h4"],
    h1: ["f1", "g1"], a2: ["a3"], b2: ["b3", "b4"], c2: ["c3"], d2: ["d3", "d4"], g2: ["g3", "g4"], h2: ["h3", "h4"],
  },
  notations: { "e1g1": "O-O", "f3e5": "Cxe5", "a4c6": "Fxc6", "d2d4": "d4", "b1c3": "Cc3" },
  reponse: { depart: "b7", arrivee: "b5", notation: "b5" },
};
