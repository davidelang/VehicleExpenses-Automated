#!/usr/bin/env python3
"""Render docs/user-manual.md to HTML for browsers + app assets.

Markdown is the edit source. HTML is the browser-facing / in-app document
(with screenshots). Run: ./scripts/render-user-manual.sh
"""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path

try:
    import markdown
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "--user", "markdown", "-q"])
    import markdown


def normalize_md_for_html(text: str, img_prefix: str) -> str:
    def repl(m: re.Match[str]) -> str:
        alt, url = m.group(1), m.group(2)
        name = url.rstrip("/").split("/")[-1]
        return f"![{alt}]({img_prefix}{name})"

    return re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", repl, text)


def render(md: str) -> str:
    body = markdown.markdown(
        md,
        extensions=["tables", "fenced_code", "toc", "sane_lists", "nl2br"],
        extension_configs={"toc": {"permalink": False}},
    )
    body = body.replace(
        "<img ",
        '<img loading="lazy" style="max-width:100%;height:auto;border:1px solid #333;border-radius:8px;margin:12px 0;" ',
    )
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Vehicle Expenses Automated — User Manual</title>
  <style>
    :root {{ color-scheme: light dark; }}
    body {{
      font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
      line-height: 1.5;
      max-width: 52rem;
      margin: 0 auto;
      padding: 1rem 1.25rem 3rem;
      color: #111;
      background: #fafafa;
    }}
    @media (prefers-color-scheme: dark) {{
      body {{ color: #eee; background: #121212; }}
      a {{ color: #8ab4f8; }}
      table {{ border-color: #444; }}
      th, td {{ border-color: #444; }}
      code {{ background: #2a2a2a; }}
      pre {{ background: #1e1e1e; }}
      h2 {{ border-bottom-color: #444; }}
    }}
    h1, h2, h3 {{ line-height: 1.25; }}
    h1 {{ font-size: 1.75rem; }}
    h2 {{
      font-size: 1.35rem;
      margin-top: 2rem;
      border-bottom: 1px solid #ccc;
      padding-bottom: 0.25rem;
    }}
    table {{ border-collapse: collapse; width: 100%; margin: 1rem 0; font-size: 0.95rem; }}
    th, td {{ border: 1px solid #ccc; padding: 0.4rem 0.55rem; vertical-align: top; }}
    th {{ background: rgba(0,0,0,0.05); text-align: left; }}
    code {{ font-size: 0.9em; padding: 0.1em 0.3em; border-radius: 4px; background: #eee; }}
    pre {{ padding: 0.75rem; overflow: auto; border-radius: 8px; background: #f0f0f0; }}
    pre code {{ background: transparent; padding: 0; }}
    hr {{ border: none; border-top: 1px solid #ccc; margin: 1.5rem 0; }}
    ul, ol {{ padding-left: 1.4rem; }}
    .note {{ font-size: 0.9rem; opacity: 0.85; margin-bottom: 1.5rem; }}
  </style>
</head>
<body>
  <p class="note">Vehicle Expenses Automated — full illustrated user manual (HTML for browsers). Edit source: <code>docs/user-manual.md</code>; regenerate with <code>./scripts/render-user-manual.sh</code>.</p>
  {body}
</body>
</html>
"""


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    md_path = root / "docs" / "user-manual.md"
    if not md_path.is_file():
        raise SystemExit(f"missing {md_path}")
    md_text = md_path.read_text(encoding="utf-8")

    # Browser: docs/user-manual.html with relative user-manual/images/
    web_html = render(normalize_md_for_html(md_text, "user-manual/images/"))
    web_out = root / "docs" / "user-manual.html"
    web_out.write_text(web_html, encoding="utf-8")
    print(f"wrote {web_out} ({web_out.stat().st_size} bytes, {web_html.count('<img ')} images)")

    # App assets
    asset_root = root / "app" / "src" / "main" / "assets" / "user-manual"
    asset_img = asset_root / "images"
    asset_img.mkdir(parents=True, exist_ok=True)
    asset_html = render(normalize_md_for_html(md_text, "images/"))
    (asset_root / "index.html").write_text(asset_html, encoding="utf-8")
    src_imgs = root / "docs" / "user-manual" / "images"
    n = 0
    for p in sorted(src_imgs.glob("*.jpg")):
        shutil.copy2(p, asset_img / p.name)
        n += 1
    print(f"wrote {asset_root / 'index.html'} + {n} jpgs")


if __name__ == "__main__":
    main()
