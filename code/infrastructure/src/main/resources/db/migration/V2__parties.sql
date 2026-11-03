-- Les parties terminées et leurs coups (BF11, RG7). Un camp sans joueur est l'ordinateur.

create table parties (
    id                 uuid primary key,
    blancs             text references joueurs (pseudo),
    noirs              text references joueurs (pseudo),
    force_ordinateur   integer,
    cadence            text,
    categorie          text check (categorie in ('BULLET', 'BLITZ', 'RAPIDE')),
    resultat           text not null,
    motif              text not null,
    points_blancs      integer,
    points_noirs       integer,
    variation_blancs   integer,
    variation_noirs    integer,
    position_finale    text not null,
    nombre_coups       integer not null,
    terminee_le        timestamptz not null
);

create index parties_des_blancs on parties (blancs, terminee_le desc);
create index parties_des_noirs on parties (noirs, terminee_le desc);

create table coups (
    partie          uuid not null references parties (id) on delete cascade,
    numero          integer not null,
    notation        text not null,
    depart          text not null,
    arrivee         text not null,
    position_apres  text not null,
    primary key (partie, numero)
);
