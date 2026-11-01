"""Convert the project's Markdown documents to PDF.

Usage: python3 outils/md2pdf.py document.md [...]
Each PDF is written next to its source. Only the Markdown used in docs/ is
supported: headings (#, ##), paragraphs, bullet and numbered lists (nested by
indentation), pipe tables, **bold**, `code`, ``` code blocks and --- separators.
"""
import html
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (HRFlowable, Paragraph, Preformatted, SimpleDocTemplate, Spacer, Table,
                                TableStyle)

FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")
FONT_REGULAR, FONT_BOLD, FONT_MONO = "DejaVuSans", "DejaVuSans-Bold", "DejaVuSansMono"
PAGE_MARGIN = 2 * cm
BODY_FONT_SIZE = 10
TABLE_FONT_SIZE = 9
# Code blocks never wrap (indentation carries meaning): 8 pt fits 90 monospaced characters on A4
CODE_FONT_SIZE = 8
CODE_PADDING = 4
CODE_FENCE = "```"
LIST_INDENT_PER_LEVEL = 14
SPACES_PER_LIST_LEVEL = 3
HEADER_BACKGROUND = colors.HexColor("#1F4E79")
GRID_COLOR = colors.HexColor("#B4C6E7")
CODE_BACKGROUND = colors.HexColor("#F2F2F2")
CELL_PADDING = 6  # reportlab's default left and right padding, in points
NON_BREAKING_SPACE = " "

LIST_ITEM = re.compile(r"^(?P<indent> *)(?P<marker>[-*]|\d+\.) (?P<text>.*)$")
HEADING = re.compile(r"^(?P<level>#{1,3}) (?P<text>.*)$")
TABLE_SEPARATOR = re.compile(r"^\|[\s:|-]+\|$")


@dataclass
class Block:
    kind: str  # heading, paragraph, item, table, rule, code
    text: str = ""
    level: int = 0
    marker: str = ""
    rows: list = field(default_factory=list)


def register_fonts() -> None:
    for name in (FONT_REGULAR, FONT_BOLD, FONT_MONO):
        pdfmetrics.registerFont(TTFont(name, str(FONT_DIR / f"{name}.ttf")))
    pdfmetrics.registerFontFamily(FONT_REGULAR, normal=FONT_REGULAR, bold=FONT_BOLD)


def parse_blocks(lines: list[str]) -> list[Block]:
    blocks: list[Block] = []
    inside_code = False
    for line in lines:
        stripped = line.strip()
        previous = blocks[-1] if blocks else None
        if stripped.startswith(CODE_FENCE):
            inside_code = not inside_code
            if inside_code:
                blocks.append(Block("code"))
        elif inside_code:
            previous.text += line + "\n"  # kept verbatim, indentation included
        elif not stripped:
            blocks.append(Block("blank"))
        elif stripped == "---":
            blocks.append(Block("rule"))
        elif match := HEADING.match(stripped):
            blocks.append(Block("heading", match["text"], level=len(match["level"])))
        elif stripped.startswith("|"):
            if previous is None or previous.kind != "table":
                previous = Block("table")
                blocks.append(previous)
            if not TABLE_SEPARATOR.match(stripped):
                previous.rows.append([cell.strip() for cell in stripped.strip("|").split("|")])
        elif match := LIST_ITEM.match(line):
            level = len(match["indent"]) // SPACES_PER_LIST_LEVEL
            blocks.append(Block("item", match["text"], level=level, marker=match["marker"]))
        elif previous is not None and previous.kind in ("paragraph", "item"):
            previous.text += " " + stripped  # continuation of a wrapped line
        else:
            blocks.append(Block("paragraph", stripped))
    return [block for block in blocks if block.kind != "blank"]


def format_inline(text: str) -> str:
    # French typography: quotes and double punctuation never stay alone at a line break
    text = text.replace("« ", "«" + NON_BREAKING_SPACE)
    text = re.sub(r" ([»;:!?])", NON_BREAKING_SPACE + r"\1", text)
    escaped = html.escape(text, quote=False)
    escaped = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", escaped)
    return re.sub(r"`(.+?)`", rf'<font name="{FONT_MONO}">\1</font>', escaped)


def build_styles() -> dict[str, ParagraphStyle]:
    body = ParagraphStyle("body", fontName=FONT_REGULAR, fontSize=BODY_FONT_SIZE,
                          leading=BODY_FONT_SIZE * 1.4, alignment=TA_LEFT, spaceAfter=6)
    return {
        "body": body,
        "h1": ParagraphStyle("h1", parent=body, fontName=FONT_BOLD, fontSize=17,
                             leading=22, textColor=HEADER_BACKGROUND, spaceAfter=10),
        "h2": ParagraphStyle("h2", parent=body, fontName=FONT_BOLD, fontSize=13,
                             leading=17, textColor=HEADER_BACKGROUND, spaceBefore=12, spaceAfter=6),
        "h3": ParagraphStyle("h3", parent=body, fontName=FONT_BOLD, fontSize=11,
                             leading=15, spaceBefore=8, spaceAfter=4),
        "cell": ParagraphStyle("cell", parent=body, fontSize=TABLE_FONT_SIZE,
                               leading=TABLE_FONT_SIZE * 1.35, spaceAfter=0),
        "header": ParagraphStyle("header", parent=body, fontName=FONT_BOLD, fontSize=TABLE_FONT_SIZE,
                                 leading=TABLE_FONT_SIZE * 1.35, spaceAfter=0, textColor=colors.white),
        "code": ParagraphStyle("code", parent=body, fontName=FONT_MONO, fontSize=CODE_FONT_SIZE,
                               leading=CODE_FONT_SIZE * 1.3, backColor=CODE_BACKGROUND,
                               borderPadding=CODE_PADDING, spaceBefore=CODE_PADDING,
                               spaceAfter=CODE_PADDING + 6),
    }


def column_texts(rows: list[list[str]], index: int) -> list[str]:
    return [row[index] if index < len(row) else "" for row in rows]


def longest_word_width(texts: list[str]) -> float:
    words = [word for text in texts for word in re.sub(r"[*`]", "", text).split()]
    widest = max((pdfmetrics.stringWidth(word, FONT_BOLD, TABLE_FONT_SIZE) for word in words), default=0)
    return widest + 2 * CELL_PADDING


def column_widths(rows: list[list[str]], available_width: float) -> list[float]:
    """Share the width by text length, but never cut a word inside a column."""
    columns = [column_texts(rows, index) for index in range(len(rows[0]))]
    lengths = [max(len(text) for text in texts) for texts in columns]
    minimums = [longest_word_width(texts) for texts in columns]
    widths = [max(available_width * length / sum(lengths), minimum)
              for length, minimum in zip(lengths, minimums)]
    excess = sum(widths) - available_width
    slack = [width - minimum for width, minimum in zip(widths, minimums)]
    if excess > 0 and sum(slack) > 0:
        widths = [width - excess * room / sum(slack) for width, room in zip(widths, slack)]
    return widths


def build_table(rows: list[list[str]], styles: dict, available_width: float) -> Table:
    cells = [[Paragraph(format_inline(text), styles["header" if row_index == 0 else "cell"])
              for text in row] for row_index, row in enumerate(rows)]
    table = Table(cells, colWidths=column_widths(rows, available_width), repeatRows=1)
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), HEADER_BACKGROUND),
        ("GRID", (0, 0), (-1, -1), 0.5, GRID_COLOR),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    return table


def build_item(block: Block, styles: dict) -> Paragraph:
    indent = LIST_INDENT_PER_LEVEL * (block.level + 1)
    style = ParagraphStyle(f"item{block.level}", parent=styles["body"], leftIndent=indent,
                           bulletIndent=indent - LIST_INDENT_PER_LEVEL + 2, spaceAfter=3)
    bullet = block.marker if block.marker[0].isdigit() else "•"
    return Paragraph(format_inline(block.text), style, bulletText=bullet)


def build_story(blocks: list[Block], styles: dict, available_width: float) -> list:
    story = []
    for block in blocks:
        if block.kind == "heading":
            story.append(Paragraph(format_inline(block.text), styles[f"h{block.level}"]))
        elif block.kind == "paragraph":
            story.append(Paragraph(format_inline(block.text), styles["body"]))
        elif block.kind == "item":
            story.append(build_item(block, styles))
        elif block.kind == "table":
            story += [build_table(block.rows, styles, available_width), Spacer(1, 8)]
        elif block.kind == "code":
            story.append(Preformatted(block.text.rstrip("\n"), styles["code"]))
        elif block.kind == "rule":
            story += [Spacer(1, 4), HRFlowable(width="100%", color=GRID_COLOR), Spacer(1, 8)]
    return story


def draw_page_number(canvas, document) -> None:
    canvas.setFont(FONT_REGULAR, 8)
    canvas.drawCentredString(A4[0] / 2, PAGE_MARGIN / 2, f"Page {document.page}")


def convert(source: Path) -> Path:
    blocks = parse_blocks(source.read_text(encoding="utf-8").splitlines())
    title = next((block.text for block in blocks if block.kind == "heading"), source.stem)
    target = source.with_suffix(".pdf")
    document = SimpleDocTemplate(str(target), pagesize=A4, title=title,
                                 leftMargin=PAGE_MARGIN, rightMargin=PAGE_MARGIN,
                                 topMargin=PAGE_MARGIN, bottomMargin=PAGE_MARGIN)
    story = build_story(blocks, build_styles(), document.width)
    document.build(story, onFirstPage=draw_page_number, onLaterPages=draw_page_number)
    return target


def main(arguments: list[str]) -> int:
    if not arguments:
        print(__doc__, file=sys.stderr)
        return 2
    register_fonts()
    failures = 0
    for name in arguments:
        try:
            print(f"OK     {convert(Path(name))}")
        except (OSError, ValueError) as error:
            failures += 1
            print(f"ÉCHEC  {name} : {error}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
