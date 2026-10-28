#!/usr/bin/env python3
"""
One-off refactor: moves inline CSS / inline event handlers out of Thymeleaf templates
so the app can run with a strict Content-Security-Policy (no 'unsafe-inline').

- <style> blocks          -> static/css/<page>.css  (verbatim)
- style="..." attributes  -> utility classes in static/css/utilities.css
- onsubmit="return confirm('..')" -> data-confirm="..." (handled by static/js/app.js)
"""
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEMPLATES = ROOT / "src/main/resources/templates"
CSS_DIR = ROOT / "src/main/resources/static/css"
CSS_DIR.mkdir(parents=True, exist_ok=True)

TAG_RE = re.compile(
    r"<[a-zA-Z][a-zA-Z0-9]*"
    r"(?:\s+[^\s\"'=<>/]+(?:\s*=\s*(?:\"[^\"]*\"|'[^']*'|[^\s\"'>]+))?)*"
    r"\s*/?>"
)
STYLE_ATTR_RE = re.compile(r'(\s)style\s*=\s*"([^"]*)"')
CLASS_ATTR_RE = re.compile(r'(\s)class\s*=\s*"([^"]*)"')
STYLE_BLOCK_RE = re.compile(r"[ \t]*<style>(.*?)</style>[ \t]*\r?\n?", re.S)
CONFIRM_RE = re.compile(r'\s+onsubmit\s*=\s*"return confirm\(\'([^\']*)\'\);?\s*"', re.S)
WS_RE = re.compile(r"\s+")
CONFIRM_DATASET_RE = re.compile(r'\s+onsubmit\s*=\s*"return confirm\(this\.dataset\.confirm\);"')

utilities = {}  # class name -> normalized declarations


def normalize(decl: str) -> str:
    parts = []
    for item in decl.split(";"):
        item = item.strip()
        if not item:
            continue
        prop, _, value = item.partition(":")
        value = WS_RE.sub(" ", value.strip())
        parts.append(prop.strip().lower() + ": " + value)
    return "; ".join(parts) + ";"


def class_for(decl: str) -> str:
    key = normalize(decl)
    slug = re.sub(r"[^a-z0-9]+", "-", key.lower().replace("%", "pct").replace(".", "_")).strip("-")
    name = "u-" + slug
    if len(name) > 50:
        name = "u-" + hashlib.sha1(key.encode()).hexdigest()[:8]
    utilities[name] = key
    return name


def rewrite_tag(match: re.Match) -> str:
    tag = match.group(0)
    style = STYLE_ATTR_RE.search(tag)
    if not style:
        return tag
    cls = class_for(style.group(2))
    tag = STYLE_ATTR_RE.sub("", tag, count=1)
    existing = CLASS_ATTR_RE.search(tag)
    if existing:
        return CLASS_ATTR_RE.sub(lambda m: f'{m.group(1)}class="{m.group(2)} {cls}"', tag, count=1)
    # insert class attribute right after the tag name
    return re.sub(r"^(<[a-zA-Z][a-zA-Z0-9]*)", rf'\1 class="{cls}"', tag, count=1)


def page_name(path: Path) -> str:
    rel = path.relative_to(TEMPLATES).with_suffix("")
    return "-".join(rel.parts)


def main() -> int:
    for path in sorted(TEMPLATES.rglob("*.html")):
        text = path.read_bytes().decode("utf-8")
        original = text
        name = page_name(path)

        # 1. <style> block -> css file
        blocks = STYLE_BLOCK_RE.findall(text)
        if len(blocks) != 1:
            print(f"!! {path}: expected exactly 1 <style> block, found {len(blocks)}")
            return 1
        css = blocks[0].strip("\r\n")
        # dedent common indentation
        lines = css.splitlines()
        indent = min((len(l) - len(l.lstrip()) for l in lines if l.strip()), default=0)
        css = "\n".join(l[indent:] if l.strip() else "" for l in lines) + "\n"
        (CSS_DIR / f"{name}.css").write_bytes(css.encode("utf-8"))

        # 2. inline style attributes -> utility classes
        before = len(utilities)
        text = STYLE_BLOCK_RE.sub("@@STYLE@@\n", text, count=1)
        text = TAG_RE.sub(rewrite_tag, text)

        # 3. confirm handlers -> data-confirm
        text = CONFIRM_DATASET_RE.sub("", text)
        text = CONFIRM_RE.sub(lambda m: f' data-confirm="{m.group(1)}"', text)

        uses_utils = "u-" in text or "text-correct" in text
        indent_head = "    "
        links = f'{indent_head}<link rel="stylesheet" th:href="@{{/css/{name}.css}}">\n'
        if uses_utils:
            links += f'{indent_head}<link rel="stylesheet" th:href="@{{/css/utilities.css}}">\n'
        if name != "error":
            links += f'{indent_head}<script th:src="@{{/js/app.js}}" defer></script>\n'
        text = text.replace("@@STYLE@@\n", links, 1)

        if text != original:
            path.write_bytes(text.encode("utf-8"))
        print(f"{name}: css written, +{len(utilities) - before} new utility classes")

    # utilities.css (declarations get !important to keep inline-style precedence)
    out = ["/* Generated from former inline style=\"...\" attributes. */\n"]
    for cls in sorted(utilities):
        decls = [d.strip() for d in utilities[cls].split(";") if d.strip()]
        body = "\n".join(f"    {d} !important;" for d in decls)
        out.append(f".{cls} {{\n{body}\n}}\n")
    out.append(".text-correct { color: #86efac !important; }\n")
    out.append(".text-wrong { color: #fca5a5 !important; }\n")
    (CSS_DIR / "utilities.css").write_bytes("\n".join(out).encode("utf-8"))
    print(f"utilities.css: {len(utilities)} classes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
