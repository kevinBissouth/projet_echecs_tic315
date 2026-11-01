package echecs.metier.partie;

public class EtatTerminee extends EtatDePartie {
    @Override
    public EtatPartie valeur() {
        return EtatPartie.TERMINEE;
    }
}
