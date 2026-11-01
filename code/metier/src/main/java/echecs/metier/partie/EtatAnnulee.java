package echecs.metier.partie;

public class EtatAnnulee extends EtatDePartie {
    @Override
    public EtatPartie valeur() {
        return EtatPartie.ANNULEE;
    }
}
