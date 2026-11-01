package echecs.metier.jeu;

public enum Couleur {
    BLANC,
    NOIR;

    public Couleur adverse() {
        return this == BLANC ? NOIR : BLANC;
    }
}
