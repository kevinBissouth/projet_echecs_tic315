"""Render the project's PlantUML diagrams to PNG through the public plantuml.com server.

Usage: python3 outils/png.py [file.puml ...]
Without arguments, every diagrams/*.puml file is rendered. Each PNG is written next
to its source. The diagram text is sent to plantuml.com: never use it for
confidential content.
"""
import sys
import urllib.error
import urllib.request
import zlib
from pathlib import Path

PLANTUML_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
SERVER_PNG_URL = "https://www.plantuml.com/plantuml/png/"
DIAGRAMS_DIR = Path(__file__).resolve().parent.parent / "diagrams"
REQUEST_TIMEOUT_SECONDS = 30
# The server answers 403 to urllib's default "Python-urllib" user agent.
USER_AGENT = "tic315-echecs-png/1.0"
RAW_DEFLATE_WINDOW_BITS = -15  # negative: deflate without the zlib header, as PlantUML expects
BITS_PER_CHARACTER = 6
HTTP_BAD_REQUEST = 400


def encode(text: str) -> str:
    compressor = zlib.compressobj(9, zlib.DEFLATED, RAW_DEFLATE_WINDOW_BITS)
    data = compressor.compress(text.encode("utf-8")) + compressor.flush()
    bits = "".join(f"{byte:08b}" for byte in data)
    bits += "0" * (-len(bits) % BITS_PER_CHARACTER)
    return "".join(
        PLANTUML_ALPHABET[int(bits[i:i + BITS_PER_CHARACTER], 2)]
        for i in range(0, len(bits), BITS_PER_CHARACTER)
    )


def render(source: Path) -> Path:
    url = SERVER_PNG_URL + encode(source.read_text(encoding="utf-8"))
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    target = source.with_suffix(".png")
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            target.write_bytes(response.read())
    except urllib.error.HTTPError as error:
        if error.code != HTTP_BAD_REQUEST:
            raise
        # On a syntax error the server still returns an image showing the faulty line
        target.write_bytes(error.read())
        raise ValueError(f"erreur de syntaxe PlantUML, détail dans {target}") from error
    return target


def main(arguments: list[str]) -> int:
    sources = [Path(name) for name in arguments] or sorted(DIAGRAMS_DIR.glob("*.puml"))
    failures = 0
    for source in sources:
        try:
            print(f"OK     {render(source)}")
        except (OSError, ValueError) as error:
            failures += 1
            print(f"ÉCHEC  {source} : {error}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
