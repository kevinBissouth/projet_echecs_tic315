-- Comptes des joueurs (BF1, BF2, RG8, RG12, RG17) et leur classement par famille de cadence (RG6).
-- Les règles d'unicité sont ici : deux requêtes simultanées passeraient une vérification faite en code.

create table joueurs (
    pseudo                  text primary key,
    email                   text not null unique,
    empreinte_mot_de_passe  text not null,
    role                    text not null check (role in ('JOUEUR', 'ADMINISTRATEUR')),
    etat                    text not null check (etat in ('ACTIF', 'SUSPENDU')),
    cree_le                 timestamptz not null default now()
);

create unique index joueurs_pseudo_sans_casse on joueurs (lower(pseudo));

create table classements (
    pseudo     text not null references joueurs (pseudo) on delete cascade,
    categorie  text not null check (categorie in ('BULLET', 'BLITZ', 'RAPIDE')),
    points     integer not null,
    primary key (pseudo, categorie)
);
