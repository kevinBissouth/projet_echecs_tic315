package echecs.metier.joueur;

import java.util.EnumMap;
import java.util.Map;

public class Joueur {
    private final String pseudo;
    private final String email;
    private final Map<CategorieCadence, Classement> classements = new EnumMap<>(CategorieCadence.class);
    private EtatCompte etat = EtatCompte.ACTIF;
    // Kept by the game itself: a Joueur does not know its games, to avoid a package cycle.
    private boolean occupe = false;

    public Joueur(String pseudo, String email) {
        this.pseudo = exigerRenseigne(pseudo, "pseudo");
        this.email = exigerRenseigne(email, "email");
        for (CategorieCadence categorie : CategorieCadence.values()) {
            classements.put(categorie, new Classement());
        }
    }

    public String pseudo() {
        return pseudo;
    }

    public String email() {
        return email;
    }

    public EtatCompte etat() {
        return etat;
    }

    public boolean peutJouer() {
        return etat == EtatCompte.ACTIF && !occupe;
    }

    public Classement classement(CategorieCadence categorie) {
        return classements.get(categorie);
    }

    public void suspendre() {
        if (etat == EtatCompte.SUSPENDU) {
            throw new IllegalStateException("Le compte de " + pseudo + " est déjà suspendu");
        }
        etat = EtatCompte.SUSPENDU;
    }

    public void reactiver() {
        if (etat == EtatCompte.ACTIF) {
            throw new IllegalStateException("Le compte de " + pseudo + " est déjà actif");
        }
        etat = EtatCompte.ACTIF;
    }

    public void occuper() {
        if (!peutJouer()) {
            throw new IllegalStateException(pseudo + " ne peut pas entrer dans une partie");
        }
        occupe = true;
    }

    public void liberer() {
        occupe = false;
    }

    private static String exigerRenseigne(String valeur, String nom) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le champ " + nom + " est obligatoire");
        }
        return valeur;
    }
}
