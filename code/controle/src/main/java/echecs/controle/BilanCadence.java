package echecs.controle;

import echecs.metier.joueur.CategorieCadence;
import java.util.List;

/** A player's results in one family of time controls (RG6). */
public record BilanCadence(CategorieCadence categorie, int meilleurClassement, int victoires, int nulles, int defaites,
                           List<Integer> evolution) { }
