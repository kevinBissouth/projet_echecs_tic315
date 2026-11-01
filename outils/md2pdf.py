"""Convert the project's Markdown documents to PDF.

Usage: python3 outils/md2pdf.py document.md [...]
Each PDF is written next to its source. Only the Markdown used in docs/ is
supported: headings (#, ##), paragraphs, bullet and numbered lists (nested by
indentation), pipe tables, **bold**, `code`, ``` code blocks and --- separators.
To assemble a long document: ![caption](file.png) adds a numbered figure, on a landscape
page when it is wide, unless followed by {portrait}; [[saut]] breaks the page;
[[sommaire]] adds the table of contents; {{inclure file.md N}} includes a document from
its first ## heading, numbered under chapter N, up to its [[fin-inclusion]] line if it has
one. {{inclure file.md N C1,C2}} keeps only these ### subsections among those named Cn.
"""
import html
import re
import sys
from dataclasses import dataclass, field
from functools import partial
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import cm
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (BaseDocTemplate, CondPageBreak, Frame, HRFlowable, Image, KeepTogether, NextPageTemplate,
                                PageBreak, PageTemplate, Paragraph, Preformatted, Spacer, Table, TableStyle)
from reportlab.platypus.tableofcontents import TableOfContents

FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")
FONT_REGULAR, FONT_BOLD, FONT_MONO = "DejaVuSans", "DejaVuSans-Bold", "DejaVuSansMono"
PAGE_MARGIN = 2 * cm
BODY_FONT_SIZE = 9.5
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
HEADING = re.compile(r"^(?P<level>#{1,4}) (?P<text>.*)$")
IMAGE = re.compile(r"^!\[(?P<caption>.*)\]\((?P<path>[^)]*)\)(?P<portrait>\{portrait\})?$")
INCLUDE = re.compile(r"^\{\{inclure (?P<file>\S+) (?P<chapter>\w+)(?: (?P<only>\S+))?\}\}$")
FILTERABLE_SUBSECTION = re.compile(r"^### (?P<name>C\d+) ")
SECTION_NUMBER = re.compile(r"^(?P<number>\d+(?:\.\d+)?)\.? ")
SECTION_REFERENCE = re.compile(r"\bsection (\d)")
PAGE_BREAK, CONTENTS, END_OF_INCLUSION = "[[saut]]", "[[sommaire]]", "[[fin-inclusion]]"
PORTRAIT, LANDSCAPE = "portrait", "paysage"
# A figure wider than this goes on a landscape page, where it is more readable
LANDSCAPE_ASPECT_RATIO = 1.3
PIXEL_IN_POINTS = 0.75  # 96 dpi: a small figure keeps its natural size
FRAME_PADDING = 6  # reportlab's default frame padding, in points
CAPTION_RESERVED_HEIGHT = 40
# A heading never stays alone at the bottom of a page
HEADING_MINIMUM_SPACE = 3 * cm
CONTENTS_LEVELS = {"h2": 0}
TABLE_SEPARATOR = re.compile(r"^\|[\s:|-]+\|$")


@dataclass
class Block:
    kind: str  # heading, paragraph, item, table, rule, code, image, break, contents
    text: str = ""
    level: int = 0
    marker: str = ""
    path: str = ""
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
        elif stripped == END_OF_INCLUSION:
            continue
        elif not stripped:
            blocks.append(Block("blank"))
        elif stripped == PAGE_BREAK:
            blocks.append(Block("break"))
        elif stripped == CONTENTS:
            blocks.append(Block("contents"))
        elif match := IMAGE.match(stripped):
            marker = PORTRAIT if match["portrait"] else ""
            blocks.append(Block("image", match["caption"], path=match["path"], marker=marker))
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
        "h4": ParagraphStyle("h4", parent=body, fontName=FONT_BOLD, fontSize=10,
                             leading=14, spaceBefore=6, spaceAfter=3),
        "caption": ParagraphStyle("caption", parent=body, fontSize=TABLE_FONT_SIZE, alignment=TA_CENTER,
                                  spaceBefore=4, textColor=HEADER_BACKGROUND),
        "contents0": ParagraphStyle("contents0", parent=body, fontName=FONT_BOLD, spaceBefore=6, spaceAfter=2),
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


def expand_includes(lines: list[str], base: Path) -> list[str]:
    expanded = []
    for line in lines:
        match = INCLUDE.match(line.strip())
        expanded += included_lines(base / match["file"], match["chapter"], match["only"]) if match else [line]
    return expanded


def included_lines(source: Path, chapter: str, only: str | None) -> list[str]:
    lines = source.read_text(encoding="utf-8").splitlines()
    start = next(index for index, line in enumerate(lines) if line.startswith("## "))
    end = lines.index(END_OF_INCLUSION) if END_OF_INCLUSION in lines else len(lines)
    kept = filtered_subsections(lines[start:end], only.split(",")) if only else lines[start:end]
    return [renumbered(line, chapter) for line in kept]


def filtered_subsections(lines: list[str], names: list[str]) -> list[str]:
    kept, keeping = [], True
    for line in lines:
        if line.startswith(("## ", "### ")):
            match = FILTERABLE_SUBSECTION.match(line)
            keeping = match is None or match["name"] in names
        if keeping:
            kept.append(line)
    return kept


def renumbered(line: str, chapter: str) -> str:
    match = HEADING.match(line)
    if match:
        title = SECTION_NUMBER.sub(lambda number: f"{chapter}.{number['number']} ", match["text"])
        return f"#{match['level']} {title}"
    return SECTION_REFERENCE.sub(rf"section {chapter}.\1", line)


def is_wide(block: Block, path: Path) -> bool:
    if block.marker == PORTRAIT:
        return False
    width, height = ImageReader(str(path)).getSize()
    return width / height > LANDSCAPE_ASPECT_RATIO and width * PIXEL_IN_POINTS > A4[0] - 2 * PAGE_MARGIN


def build_figure(block: Block, number: int, path: Path, styles: dict) -> KeepTogether:
    page_width, page_height = landscape(A4) if is_wide(block, path) else A4
    available_width = page_width - 2 * (PAGE_MARGIN + FRAME_PADDING)
    available_height = page_height - 2 * (PAGE_MARGIN + FRAME_PADDING) - CAPTION_RESERVED_HEIGHT
    width, height = ImageReader(str(path)).getSize()
    scale = min(available_width / width, available_height / height, PIXEL_IN_POINTS)
    caption = f"Figure {number} — {block.text}"
    return KeepTogether([Image(str(path), width=width * scale, height=height * scale),
                         Paragraph(format_inline(caption), styles["caption"])])


def build_contents(styles: dict) -> TableOfContents:
    contents = TableOfContents()
    contents.levelStyles = [styles["contents0"]]
    return contents


def build_story(blocks: list[Block], styles: dict, available_width: float, base: Path) -> list:
    story = []
    figure_number = 0
    on_landscape_page = False
    for block in blocks:
        is_wide_figure = block.kind == "image" and is_wide(block, base / block.path)
        if on_landscape_page and not is_wide_figure:
            story += [NextPageTemplate(PORTRAIT), PageBreak()]
            on_landscape_page = False
        if block.kind == "heading":
            story += [CondPageBreak(HEADING_MINIMUM_SPACE),
                      Paragraph(format_inline(block.text), styles[f"h{block.level}"])]
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
        elif block.kind == "break":
            story.append(PageBreak())
        elif block.kind == "contents":
            story.append(build_contents(styles))
        elif block.kind == "image":
            if is_wide_figure:
                story += ([PageBreak()] if on_landscape_page else [NextPageTemplate(LANDSCAPE), PageBreak()])
                on_landscape_page = True
            figure_number += 1
            story.append(build_figure(block, figure_number, base / block.path, styles))
    return story


def draw_page_number(page_width: float, canvas, document) -> None:
    canvas.setFont(FONT_REGULAR, 8)
    canvas.drawCentredString(page_width / 2, PAGE_MARGIN / 2, f"Page {document.page}")


class ProjectDocument(BaseDocTemplate):
    """A4 document with portrait and landscape pages and a table of contents."""

    def __init__(self, target: Path, title: str):
        super().__init__(str(target), pagesize=A4, title=title, leftMargin=PAGE_MARGIN, rightMargin=PAGE_MARGIN,
                         topMargin=PAGE_MARGIN, bottomMargin=PAGE_MARGIN)
        self.addPageTemplates([self.page_template(PORTRAIT, A4), self.page_template(LANDSCAPE, landscape(A4))])

    def page_template(self, name: str, size: tuple[float, float]) -> PageTemplate:
        frame = Frame(PAGE_MARGIN, PAGE_MARGIN, size[0] - 2 * PAGE_MARGIN, size[1] - 2 * PAGE_MARGIN)
        return PageTemplate(id=name, frames=[frame], pagesize=size, onPage=partial(draw_page_number, size[0]))

    def afterFlowable(self, flowable) -> None:
        if isinstance(flowable, Paragraph) and flowable.style.name in CONTENTS_LEVELS:
            entry = (CONTENTS_LEVELS[flowable.style.name], flowable.getPlainText(), self.page)
            self.notify("TOCEntry", entry)


def convert(source: Path) -> Path:
    lines = expand_includes(source.read_text(encoding="utf-8").splitlines(), source.parent)
    blocks = parse_blocks(lines)
    title = next((block.text for block in blocks if block.kind == "heading"), source.stem)
    target = source.with_suffix(".pdf")
    document = ProjectDocument(target, title)
    document.multiBuild(build_story(blocks, build_styles(), document.width, source.parent))
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
