package echecs.metier.jeu;

import java.util.ArrayList;
import java.util.List;

public class Roi extends Piece {
    static final String COLONNE_INITIALE = "e";
    static final String COLONNE_TOUR_PETIT_ROQUE = "h";
    static final String COLONNE_TOUR_GRAND_ROQUE = "a";
    private static final List<String> TRAJET_PETIT_ROQUE = List.of("f", "g");
    private static final List<String> LIBRES_PETIT_ROQUE = List.of("f", "g");
    private static final List<String> TRAJET_GRAND_ROQUE = List.of("d", "c");
    // On the queen side the rook also crosses b, which the king itself never reaches.
    private static final List<String> LIBRES_GRAND_ROQUE = List.of("d", "c", "b");

    public Roi(Couleur couleur) {
        super(couleur);
    }

    @Override
    public List<Case> casesAttaquees(Plateau plateau, Case depart) {
        return casesASaut(plateau, depart, Direction.TOUTES);
    }

    @Override
    public List<Case> deplacementsPossibles(Plateau plateau, Case depart) {
        List<Case> deplacements = new ArrayList<>(super.deplacementsPossibles(plateau, depart));
        if (!aDejaBouge() && depart.colonne().equals(COLONNE_INITIALE) && !plateau.enEchec(couleur())) {
            ajouterRoque(deplacements, plateau, depart, COLONNE_TOUR_PETIT_ROQUE, TRAJET_PETIT_ROQUE, LIBRES_PETIT_ROQUE);
            ajouterRoque(deplacements, plateau, depart, COLONNE_TOUR_GRAND_ROQUE, TRAJET_GRAND_ROQUE, LIBRES_GRAND_ROQUE);
        }
        return deplacements;
    }

    private void ajouterRoque(List<Case> deplacements, Plateau plateau, Case depart, String colonneTour,
                              List<String> trajet, List<String> casesLibres) {
        int rangee = depart.rangee();
        boolean tourDisponible = tourSansAvoirBouge(plateau.caseEn(colonneTour, rangee));
        boolean cheminLibre = casesLibres.stream().allMatch(colonne -> plateau.caseEn(colonne, rangee).estLibre());
        boolean trajetSur = trajet.stream()
                .noneMatch(colonne -> plateau.estAttaquee(plateau.caseEn(colonne, rangee), couleur().adverse()));
        if (tourDisponible && cheminLibre && trajetSur) {
            deplacements.add(plateau.caseEn(trajet.getLast(), rangee));
        }
    }

    private boolean tourSansAvoirBouge(Case coin) {
        return coin.piece()
                .filter(piece -> piece instanceof Tour && piece.couleur() == couleur() && !piece.aDejaBouge())
                .isPresent();
    }

    @Override
    char lettreFen() {
        return 'K';
    }

    @Override
    String initiale() {
        return "R";
    }

    @Override
    Piece nouvelleInstance() {
        return new Roi(couleur());
    }
}
