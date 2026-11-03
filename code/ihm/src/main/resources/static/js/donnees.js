// Ce qui ne dépend pas du serveur : les cadences proposées et la partie rejouée sur la page d'accueil.

export const POSITION_INITIALE = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";

export const CADENCES = [
  { famille: "Bullet", cadences: ["1+0", "2+1"] },
  { famille: "Blitz", cadences: ["3+0", "3+2", "5+0"] },
  { famille: "Rapide", cadences: ["10+0", "10+5", "15+10"] },
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
