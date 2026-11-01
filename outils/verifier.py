"""Check the consistency of the project's diagrams and documents.

Usage: python3 outils/verifier.py
Prints one line per check, then a summary. Exit code 1 if a check fails.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIAGRAMS = ROOT / "diagrams"
DOCS = ROOT / "docs"
# Lifelines that are actors or the controller (designed in J4): no class operation expected
NON_CLASS_LIFELINES = {"joueur", "bob", "createur", "adversaire", "moteur", "controleur"}
# Change events (when) are not operations
NON_OPERATION_EVENTS = {"when"}
ASSOCIATION = re.compile(r"(\*--|-left->|-right->|-->|--)")
OCL_DOCUMENT = DOCS / "contraintes-ocl.md"
OCL_VIEWS = ("classes-ocl-invariants.puml", "classes-ocl-contrats.puml")
# Predefined OCL operations, reachable with a dot like model properties
OCL_BUILTINS = {"allInstances", "oclIsUndefined", "oclIsKindOf", "oclIsTypeOf", "oclAsType", "mod"}
NOTE = re.compile(r"^note\b.*?\n(.*?)^end note", re.M | re.S)
CODE_BLOCK = re.compile(r"^```\n(.*?)^```", re.M | re.S)
NOTE_INDENT = "  "
DESIGN_SEQUENCE_PREFIX = "sequence-conception-"
# In design sequences, only the actors are outside the class model
DESIGN_NON_CLASS_LIFELINES = {"joueur", "adversaire"}
ABSTRACT_STATE = "EtatDePartie"
# UML visibilities: public, private, package, protected
VISIBILITY = r"[+\-~#]"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def without_notes(diagram: str) -> str:
    return NOTE.sub("", diagram)


def lower_first(name: str) -> str:
    return name[0].lower() + name[1:]


def class_operations(classes: str) -> set[str]:
    return set(re.findall(rf"^\s*{VISIBILITY}\s*(?:\{{abstract\}}\s*)?(\w+)\(", classes, re.M))


def check_sequence_messages(operations: set[str], diagrams: list[Path],
                            non_class_lifelines: set[str]) -> list[str]:
    problems = []
    for diagram in diagrams:
        for match in re.finditer(r"^\s*(\w+)\s*->>?\s*(\w+)\s*(?:\+\+)?\s*:\s*(\w+)\(", read(diagram), re.M):
            target, operation = match.group(2), match.group(3)
            if target not in non_class_lifelines and operation not in operations:
                problems.append(f"{diagram.stem} : message {operation}() sans opération")
    return problems


def analysis_sequences() -> list[Path]:
    return [diagram for diagram in sorted(DIAGRAMS.glob("sequence-*.puml"))
            if not diagram.stem.startswith(DESIGN_SEQUENCE_PREFIX)]


def design_operations() -> set[str]:
    return set().union(*(class_operations(read(diagram)) for diagram in DIAGRAMS.glob("classes-conception-*.puml")))


def state_events() -> set[str]:
    states = read(DIAGRAMS / "etats-partie.puml")
    events = set(re.findall(r"-->\s*\w+\s*:\s*(\w+)\(", states)) | set(re.findall(r":\s*(jouerCoup)\b", states))
    return events - NON_OPERATION_EVENTS


def check_state_events(operations: set[str]) -> list[str]:
    return [f"événement {e}() sans opération" for e in sorted(state_events() - operations)]


def check_state_pattern() -> list[str]:
    """Pattern State: each event of the state diagram is an operation of the abstract state."""
    design = read(DIAGRAMS / "classes-conception-partie.puml")
    abstract_state = re.search(rf"class {ABSTRACT_STATE} \{{(.*?)^\s*\}}", design, re.M | re.S)
    if abstract_state is None:
        return [f"classe {ABSTRACT_STATE} introuvable"]
    operations = class_operations(abstract_state.group(1))
    return [f"événement {e}() sans opération dans {ABSTRACT_STATE}" for e in sorted(state_events() - operations)]


def check_multiplicities() -> list[str]:
    problems = []
    for diagram in sorted(DIAGRAMS.glob("classes-*.puml")):
        for line in without_notes(read(diagram)).splitlines():
            text = line.strip()
            if text.startswith("'") or "<|--" in text or "hidden" in text or text.startswith("("):
                continue
            parts = ASSOCIATION.split(text, maxsplit=1)
            if len(parts) != 3 or ":" in parts[0]:
                continue
            left, _, right = parts
            if not (re.search(r'"[^"]*"', left) and re.search(r'"[^"]*"', right)):
                problems.append(f"{diagram.stem} : multiplicité manquante : {text}")
    return problems


def check_enumeration_view(classes: str) -> list[str]:
    view = read(DIAGRAMS / "classes-enumerations.puml")
    enums = set(re.findall(r"^enum (\w+)", classes, re.M))
    used = set()
    for match in re.finditer(r"^(?:abstract )?class (\w+) \{(.*?)^\}", classes, re.M | re.S):
        used |= {(match.group(1), e) for e in enums if re.search(rf"\b{e}\b", match.group(2))}
    used |= set(re.findall(r"^(\w+) \[\w+ : (\w+)\]", classes, re.M))
    drawn = set(re.findall(r"^(\w+) \.\.> (\w+)", view, re.M))
    return ([f"usage sans dépendance dans la vue : {u}" for u in sorted(used - drawn)]
            + [f"dépendance sans usage : {d}" for d in sorted(drawn - used)])


def defined_rules() -> set[str]:
    return set(re.findall(r"^\| (RG\d+) \|", read(DOCS / "cahier-des-charges.md"), re.M))


def cited_rules() -> set[str]:
    sources = [*DIAGRAMS.glob("*.puml"), *DOCS.glob("*.md")]
    sources.remove(DOCS / "cahier-des-charges.md")
    return {rule for source in sources for rule in re.findall(r"RG\d+", read(source))}


def check_rules() -> list[str]:
    return [f"{rule} citée mais non définie" for rule in sorted(cited_rules() - defined_rules())]


def uncited_rules() -> list[str]:
    return sorted(defined_rules() - cited_rules(), key=lambda rule: int(rule[2:]))


def check_needs_coverage() -> list[str]:
    needs = re.findall(r"^\| (BF\d+) \|", read(DOCS / "cahier-des-charges.md"), re.M)
    use_cases = re.findall(r'usecase "([^"]+)"', read(DIAGRAMS / "cas-utilisation.puml"))
    # One included use case (Jouer un coup) has no need of its own
    return [] if len(use_cases) >= len(needs) else [f"{len(needs)} besoins pour {len(use_cases)} cas d'utilisation"]


def ocl_blocks() -> set[tuple[str, ...]]:
    """Each OCL code block of the document, as its non-blank lines."""
    return {tuple(line for line in block.splitlines() if line.strip())
            for block in CODE_BLOCK.findall(read(OCL_DOCUMENT))}


def note_segments(diagram: str) -> set[tuple[str, ...]]:
    """The OCL text of each note, cut at its markup lines (titles, cross-references)."""
    segments, current = set(), []
    for note in NOTE.findall(diagram):
        for line in [*note.splitlines(), "<end>"]:
            if line.strip().startswith("<"):
                if current:
                    segments.add(tuple(current))
                current = []
            elif line.strip():
                current.append(line.removeprefix(NOTE_INDENT))
    return segments


def check_ocl_notes() -> list[str]:
    blocks = ocl_blocks()
    notes = set().union(*(note_segments(read(DIAGRAMS / view)) for view in OCL_VIEWS))
    return ([f"bloc absent des vues OCL : {block[0]} / {block[1]}" for block in sorted(blocks - notes)]
            + [f"note absente du document : {note[0]} / {note[1]}" for note in sorted(notes - blocks)])


def model_names(classes: str) -> tuple[set[str], dict[str, set[str]]]:
    """Attributes, operations and role names of the model, then the literals of each enumeration."""
    body = without_notes(classes)
    names = set(re.findall(rf"^\s*{VISIBILITY}\s*/?(\w+)\s*:", body, re.M)) | class_operations(body)
    names |= {lower_first(name) for name in re.findall(r"^(?:abstract )?class (\w+)", body, re.M)}
    for line in body.splitlines():
        if ASSOCIATION.search(line) and not line.lstrip().startswith("'"):
            label_text = line.replace("\\n", " ")  # PlantUML line breaks inside labels
            names |= set(re.findall(r'"[^"]*?([A-Za-z]\w*)\s*(?:\{\w+\})?"', label_text))
    literals = {name: set(re.findall(r"^\s*([A-Z_]+)\s*$", values, re.M))
                for name, values in re.findall(r"^enum (\w+).*?\{(.*?)\}", body, re.M | re.S)}
    return names, literals


def check_ocl_names(classes: str) -> list[str]:
    names, literals = model_names(classes)
    defined_in_ocl = set(re.findall(r"def:\s*(\w+)", read(OCL_DOCUMENT)))
    problems = []
    for block in CODE_BLOCK.findall(read(OCL_DOCUMENT)):
        for name in re.findall(r"\.(\w+)", block):
            if name not in names | OCL_BUILTINS | defined_in_ocl:
                problems.append(f"propriété inconnue : .{name}")
        for enum, literal in re.findall(r"\b([A-Z]\w+)::([A-Z_]+)\b", block):
            if literal not in literals.get(enum, set()):
                problems.append(f"littéral inconnu : {enum}::{literal}")
        for owner, member in re.findall(r"context (\w+)::(\w+)", block):
            if member not in names:
                problems.append(f"contexte inconnu : {owner}::{member}")
    return sorted(set(problems))


def main() -> int:
    classes = read(DIAGRAMS / "classes-domaine.puml")
    operations = class_operations(classes)
    checks = {
        "Messages de séquence → opérations": check_sequence_messages(
            operations, analysis_sequences(), NON_CLASS_LIFELINES),
        "Événements d'état → opérations": check_state_events(operations),
        "Séquences de conception → opérations de conception": check_sequence_messages(
            design_operations(), sorted(DIAGRAMS.glob(f"{DESIGN_SEQUENCE_PREFIX}*.puml")),
            DESIGN_NON_CLASS_LIFELINES),
        "Patron État : événements → opérations de l'état": check_state_pattern(),
        "Multiplicités aux deux extrémités": check_multiplicities(),
        "Vue des énumérations ↔ diagramme principal": check_enumeration_view(classes),
        "Règles citées → définies": check_rules(),
        "Besoins → cas d'utilisation": check_needs_coverage(),
        "Contraintes OCL : document ↔ notes des vues": check_ocl_notes(),
        "Contraintes OCL : noms → modèle du domaine": check_ocl_names(classes),
    }
    failures = 0
    for name, problems in checks.items():
        print(f"{'OK    ' if not problems else 'ÉCHEC '} {name}")
        for problem in problems:
            print(f"         - {problem}")
        failures += len(problems)
    print(f"INFO   Règles jamais citées dans un diagramme ni une fiche : {', '.join(uncited_rules()) or 'aucune'}")
    print(f"\n{failures} problème(s) trouvé(s).")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
