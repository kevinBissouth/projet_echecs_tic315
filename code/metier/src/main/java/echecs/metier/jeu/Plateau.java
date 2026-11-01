package echecs.metier.jeu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class Plateau {
    private static final String COLONNES = "abcdefgh";
    private static final int TAILLE = 8;
    private static final int PREMIERE_RANGEE = 1;
    private static final int RANGEE_INITIALE_PIONS_BLANCS = 2;
    private static final int RANGEE_INITIALE_PIONS_NOIRS = 7;
    private static final int RANGEE_INITIALE_NOIRS = 8;
    private static final int ECART_AVANCE_DOUBLE = 2;
    private static final int ECART_ROQUE = 2;
    private static final String COLONNE_ARRIVEE_TOUR_PETIT_ROQUE = "f";
    private static final String COLONNE_ARRIVEE_TOUR_GRAND_ROQUE = "d";
    private static final String COLONNE_ARRIVEE_PETIT_ROQUE = "g";
    private static final String AUCUN = "-";
    private static final List<Function<Couleur, Piece>> ORDRE_INITIAL = List.of(
            Tour::new, Cavalier::new, Fou::new, Dame::new, Roi::new, Fou::new, Cavalier::new, Tour::new);

    private final Case[][] cases = new Case[TAILLE][TAILLE];
    private Case casePriseEnPassant;

    public Plateau() {
        for (int colonne = 0; colonne < TAILLE; colonne++) {
            for (int rangee = 0; rangee < TAILLE; rangee++) {
                cases[colonne][rangee] = new Case(COLONNES.substring(colonne, colonne + 1), rangee + PREMIERE_RANGEE);
            }
        }
        placerPositionInitiale();
    }

    /** An empty board, to set up a given position: used by tests only. */
    static Plateau vide() {
        Plateau plateau = new Plateau();
        plateau.toutesLesCases().forEach(uneCase -> uneCase.placer(null));
        return plateau;
    }

    private void placerPositionInitiale() {
        for (int colonne = 0; colonne < TAILLE; colonne++) {
            cases[colonne][PREMIERE_RANGEE - 1].placer(ORDRE_INITIAL.get(colonne).apply(Couleur.BLANC));
            cases[colonne][RANGEE_INITIALE_PIONS_BLANCS - 1].placer(new Pion(Couleur.BLANC));
            cases[colonne][RANGEE_INITIALE_PIONS_NOIRS - 1].placer(new Pion(Couleur.NOIR));
            cases[colonne][RANGEE_INITIALE_NOIRS - 1].placer(ORDRE_INITIAL.get(colonne).apply(Couleur.NOIR));
        }
    }

    public Case caseEn(String colonne, int rangee) {
        int indiceColonne = COLONNES.indexOf(colonne);
        if (colonne.length() != 1 || indiceColonne < 0 || !estUneRangee(rangee)) {
            throw new IllegalArgumentException("Case inexistante : " + colonne + rangee);
        }
        return cases[indiceColonne][rangee - PREMIERE_RANGEE];
    }

    public boolean estLegal(Case depart, Case arrivee) {
        return destinationsLegales(depart).contains(arrivee);
    }

    /** The squares the piece on this square may really reach: its moves that leave its king safe. */
    public List<Case> destinationsLegales(Case depart) {
        return depart.piece()
                .map(piece -> piece.deplacementsPossibles(this, depart).stream()
                        .filter(arrivee -> !laisseRoiEnEchec(depart, arrivee))
                        .toList())
                .orElse(List.of());
    }

    /**
     * Plays a legal move.
     *
     * @param promotion the chosen piece, required when a pawn reaches the last rank, otherwise null
     * @return the captured piece, if any
     */
    public Optional<Piece> deplacer(Case depart, Case arrivee, TypePromotion promotion) {
        if (!estLegal(depart, arrivee)) {
            throw new IllegalArgumentException("Coup illégal : " + depart + " vers " + arrivee);
        }
        Piece piece = depart.piece().orElseThrow();
        boolean promeut = piece instanceof Pion pion && pion.atteintLaDerniereRangee(arrivee);
        if (promeut != (promotion != null)) {
            throw new IllegalArgumentException("Promotion invalide pour le coup " + depart + " vers " + arrivee);
        }
        Optional<Piece> piecePrise = executer(depart, arrivee);
        piece.marquerDeplacee();
        if (promeut) {
            arrivee.placer(pieceDePromotion(promotion, piece.couleur()));
        }
        return piecePrise;
    }

    /** Whether the game ends with this colour to move. */
    public Optional<MotifFin> situationFinale(Couleur couleur) {
        boolean peutJouer = piecesDe(couleur).anyMatch(uneCase -> !destinationsLegales(uneCase).isEmpty());
        if (!peutJouer) {
            return Optional.of(enEchec(couleur) ? MotifFin.MAT : MotifFin.PAT);
        }
        return materielInsuffisant() ? Optional.of(MotifFin.MATERIEL_INSUFFISANT) : Optional.empty();
    }

    public boolean enEchec(Couleur couleur) {
        Case caseDuRoi = piecesDe(couleur)
                .filter(uneCase -> uneCase.piece().orElseThrow() instanceof Roi)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun roi pour les " + couleur));
        return estAttaquee(caseDuRoi, couleur.adverse());
    }

    public boolean estAttaquee(Case visee, Couleur attaquant) {
        return piecesDe(attaquant)
                .anyMatch(uneCase -> uneCase.piece().orElseThrow().casesAttaquees(this, uneCase).contains(visee));
    }

    /**
     * A king alone, or with a single bishop or knight, cannot force a mate: this colour loses no
     * game on time against such material (RG5). Rarer drawn endings stay out of scope.
     */
    public boolean peutMater(Couleur couleur) {
        List<Piece> pieces = piecesDe(couleur).map(uneCase -> uneCase.piece().orElseThrow())
                .filter(piece -> !(piece instanceof Roi)).toList();
        return pieces.size() > 1 || pieces.stream().anyMatch(piece -> !(piece instanceof Fou || piece instanceof Cavalier));
    }

    /** The first four FEN fields: enough for an engine, and to compare positions (RG4, RG9). */
    public String position(Couleur trait) {
        return String.join(" ", placementFen(), trait == Couleur.BLANC ? "w" : "b", droitsDeRoque(),
                casePriseEnPassant == null ? AUCUN : casePriseEnPassant.nom());
    }

    Optional<Case> casePriseEnPassant() {
        return Optional.ofNullable(casePriseEnPassant);
    }

    Optional<Case> decaler(Case depart, Direction direction) {
        int colonne = COLONNES.indexOf(depart.colonne()) + direction.colonnes();
        int rangee = depart.rangee() + direction.rangees();
        if (colonne < 0 || colonne >= TAILLE || !estUneRangee(rangee)) {
            return Optional.empty();
        }
        return Optional.of(cases[colonne][rangee - PREMIERE_RANGEE]);
    }

    /** An independent copy of this board, on which a move can be tried. */
    Plateau copie() {
        Plateau copie = Plateau.vide();
        toutesLesCases().forEach(uneCase -> uneCase.piece().ifPresent(piece ->
                copie.caseEn(uneCase.colonne(), uneCase.rangee()).placer(piece.copie())));
        casePriseEnPassant().ifPresent(uneCase ->
                copie.casePriseEnPassant = copie.caseEn(uneCase.colonne(), uneCase.rangee()));
        return copie;
    }

    private boolean laisseRoiEnEchec(Case depart, Case arrivee) {
        Plateau essai = copie();
        Couleur couleur = depart.piece().orElseThrow().couleur();
        essai.executer(essai.caseEn(depart.colonne(), depart.rangee()), essai.caseEn(arrivee.colonne(), arrivee.rangee()));
        return essai.enEchec(couleur);
    }

    /** Moves the pieces, with the special moves of RG2, without checking anything. */
    private Optional<Piece> executer(Case depart, Case arrivee) {
        Piece piece = depart.piece().orElseThrow();
        Optional<Piece> piecePrise = arrivee.piece();
        if (piece instanceof Pion && arrivee.equals(casePriseEnPassant)) {
            Case casePionPris = caseEn(arrivee.colonne(), depart.rangee());
            piecePrise = casePionPris.piece();
            casePionPris.placer(null);
        }
        if (piece instanceof Roi && ecartColonnes(depart, arrivee) == ECART_ROQUE) {
            deplacerTourDuRoque(arrivee);
        }
        casePriseEnPassant = caseFranchieParUnPion(piece, depart, arrivee);
        arrivee.placer(piece);
        depart.placer(null);
        return piecePrise;
    }

    private void deplacerTourDuRoque(Case arriveeRoi) {
        boolean petitRoque = arriveeRoi.colonne().equals(COLONNE_ARRIVEE_PETIT_ROQUE);
        int rangee = arriveeRoi.rangee();
        Case depart = caseEn(petitRoque ? Roi.COLONNE_TOUR_PETIT_ROQUE : Roi.COLONNE_TOUR_GRAND_ROQUE, rangee);
        Case arrivee = caseEn(petitRoque ? COLONNE_ARRIVEE_TOUR_PETIT_ROQUE : COLONNE_ARRIVEE_TOUR_GRAND_ROQUE, rangee);
        Piece tour = depart.piece().orElseThrow();
        arrivee.placer(tour);
        depart.placer(null);
        tour.marquerDeplacee();
    }

    private Case caseFranchieParUnPion(Piece piece, Case depart, Case arrivee) {
        if (piece instanceof Pion && Math.abs(arrivee.rangee() - depart.rangee()) == ECART_AVANCE_DOUBLE) {
            return caseEn(depart.colonne(), (depart.rangee() + arrivee.rangee()) / 2);
        }
        return null;
    }

    private Piece pieceDePromotion(TypePromotion promotion, Couleur couleur) {
        return switch (promotion) {
            case DAME -> new Dame(couleur);
            case TOUR -> new Tour(couleur);
            case FOU -> new Fou(couleur);
            case CAVALIER -> new Cavalier(couleur);
        };
    }

    private boolean materielInsuffisant() {
        if (peutMater(Couleur.BLANC) || peutMater(Couleur.NOIR)) {
            return false;
        }
        boolean cavalierEnJeu = toutesLesCases()
                .anyMatch(uneCase -> uneCase.piece().filter(Cavalier.class::isInstance).isPresent());
        long couleursDeCasesDesFous = toutesLesCases()
                .filter(uneCase -> uneCase.piece().filter(Fou.class::isInstance).isPresent())
                .map(this::estUneCaseClaire)
                .distinct()
                .count();
        int nombrePieces = (int) toutesLesCases().filter(uneCase -> !uneCase.estLibre()).count();
        // Each side has at most one minor piece here: bare kings plus one minor piece never mate,
        // and two bishops only when they stand on squares of the same colour.
        return nombrePieces <= 3 || (!cavalierEnJeu && couleursDeCasesDesFous == 1);
    }

    private boolean estUneCaseClaire(Case uneCase) {
        return (COLONNES.indexOf(uneCase.colonne()) + uneCase.rangee()) % 2 == 0;
    }

    private int ecartColonnes(Case depart, Case arrivee) {
        return Math.abs(COLONNES.indexOf(depart.colonne()) - COLONNES.indexOf(arrivee.colonne()));
    }

    private boolean estUneRangee(int rangee) {
        return rangee >= PREMIERE_RANGEE && rangee < PREMIERE_RANGEE + TAILLE;
    }

    private Stream<Case> toutesLesCases() {
        return Arrays.stream(cases).flatMap(Arrays::stream);
    }

    private Stream<Case> piecesDe(Couleur couleur) {
        return toutesLesCases().filter(uneCase -> uneCase.piece().filter(piece -> piece.couleur() == couleur).isPresent());
    }

    private String placementFen() {
        List<String> rangees = new ArrayList<>();
        for (int rangee = TAILLE - 1; rangee >= 0; rangee--) {
            rangees.add(rangeeFen(rangee));
        }
        return String.join("/", rangees);
    }

    private String rangeeFen(int rangee) {
        StringBuilder texte = new StringBuilder();
        int casesLibres = 0;
        for (int colonne = 0; colonne < TAILLE; colonne++) {
            var piece = cases[colonne][rangee].piece();
            if (piece.isEmpty()) {
                casesLibres++;
                continue;
            }
            if (casesLibres > 0) {
                texte.append(casesLibres);
                casesLibres = 0;
            }
            texte.append(piece.get().symboleFen());
        }
        return casesLibres > 0 ? texte.append(casesLibres).toString() : texte.toString();
    }

    private String droitsDeRoque() {
        String droits = droitsDeRoque(Couleur.BLANC, PREMIERE_RANGEE, "KQ") + droitsDeRoque(Couleur.NOIR, RANGEE_INITIALE_NOIRS, "kq");
        return droits.isEmpty() ? AUCUN : droits;
    }

    private String droitsDeRoque(Couleur couleur, int rangee, String lettres) {
        boolean roiEnPlace = caseEn(Roi.COLONNE_INITIALE, rangee).piece()
                .filter(piece -> piece instanceof Roi && piece.couleur() == couleur && !piece.aDejaBouge()).isPresent();
        if (!roiEnPlace) {
            return "";
        }
        return (tourEnPlace(Roi.COLONNE_TOUR_PETIT_ROQUE, rangee, couleur) ? lettres.substring(0, 1) : "")
                + (tourEnPlace(Roi.COLONNE_TOUR_GRAND_ROQUE, rangee, couleur) ? lettres.substring(1) : "");
    }

    private boolean tourEnPlace(String colonne, int rangee, Couleur couleur) {
        return caseEn(colonne, rangee).piece()
                .filter(piece -> piece instanceof Tour && piece.couleur() == couleur && !piece.aDejaBouge()).isPresent();
    }
}
