"""Generates Outgo's Tabler category icons from tools/tabler-icons/icons.txt.

For every icon listed there it writes
  app/src/main/res/drawable/ic_cat_tabler_<name>.xml   a 24dp VectorDrawable, redrawn filled in the
                                                       style of the app's Phosphor Fill icons (fill_style.py)
and it regenerates
  app/src/main/java/app/outgo/data/icon/TablerIcons.kt  key -> drawable, and the picker's groups,
                                                       which also place every bundled PNG (`asset:<key>`).

The icons come from the @tabler/icons npm package (outline set, MIT), pinned to TABLER_VERSION and
fetched with `npm pack` unless --tabler-dir points at an unpacked copy's icons/outline folder.

Needs Node's npm and: pip install skia-python potracer numpy

Usage, from the repo root:
  python tools/tabler-icons/gen_tabler_icons.py
  python tools/tabler-icons/gen_tabler_icons.py --tabler-dir path/to/package/icons/outline

To add icons: add their Tabler names (https://tabler.io/icons) to icons.txt, under an existing
`@group` or a new one (a new group also needs strings icon_group_<id> in values/ and values-vi/),
run this script, build. Never remove or rename a shipped name: its key is stored in users'
databases and backups. The script refuses to leave an orphaned ic_cat_tabler_* drawable behind
unless --prune is given.
"""
import argparse
import os
import re
import subprocess
import sys
import tarfile
import tempfile

import fill_style

TABLER_VERSION = "3.48.0"

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
LIST = os.path.join(HERE, "icons.txt")
DRAWABLE_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
ASSET_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "icons")
KOTLIN_OUT = os.path.join(ROOT, "app", "src", "main", "java", "app", "outgo", "data", "icon", "TablerIcons.kt")

PREFIX_DRAWABLE = "ic_cat_tabler_"
PREFIX_KEY = "tabler_"
# icons.txt line prefix placing a bundled PNG (assets/icons/<key>.png) in a group.
PREFIX_ASSET = "asset:"
# The colour of the bundled PNG icons, so both kinds look alike in the picker. Category rows
# tint every icon with the category's own colour anyway.
COLOR = "#FF455A64"
# Tabler fills ~83% of its grid, the bundled Phosphor PNGs ~69% (median glyph extent); shrinking by
# this makes the two kinds look the same size side by side.
VISUAL_SCALE = 0.825


def read_list():
    groups, seen = [], set()
    for number, raw in enumerate(open(LIST, encoding="utf-8"), 1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("@group"):
            group = line.split()[1]
            if not re.fullmatch(r"[a-z][a-z0-9_]*", group):
                sys.exit(f"icons.txt:{number}: bad group id {group!r}")
            groups.append((group, []))
            continue
        if not groups:
            sys.exit(f"icons.txt:{number}: {line!r} comes before any @group")
        if line.startswith(PREFIX_ASSET):
            if not os.path.exists(os.path.join(ASSET_DIR, line[len(PREFIX_ASSET):] + ".png")):
                sys.exit(f"icons.txt:{number}: no assets/icons/{line[len(PREFIX_ASSET):]}.png")
        elif not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", line):
            sys.exit(f"icons.txt:{number}: {line!r} is not a Tabler icon name")
        if line in seen:
            sys.exit(f"icons.txt:{number}: {line!r} is listed twice")
        seen.add(line)
        groups[-1][1].append(line)
    unplaced = sorted(f[:-4] for f in os.listdir(ASSET_DIR) if f.endswith(".png") and PREFIX_ASSET + f[:-4] not in seen)
    if unplaced:
        sys.exit("Bundled icons in no group of icons.txt: " + ", ".join(unplaced))
    return groups


def asset_key(entry):
    """The icon.asset_key an icons.txt entry is stored under."""
    return entry[len(PREFIX_ASSET):] if entry.startswith(PREFIX_ASSET) else PREFIX_KEY + snake(entry)


def fetch_tabler(tmp):
    """Unpacks the pinned @tabler/icons package into tmp and returns its outline folder."""
    npm = "npm.cmd" if os.name == "nt" else "npm"
    subprocess.run([npm, "pack", f"@tabler/icons@{TABLER_VERSION}", "--silent"], cwd=tmp, check=True,
                   stdout=subprocess.DEVNULL)
    tgz = next(f for f in os.listdir(tmp) if f.endswith(".tgz"))
    with tarfile.open(os.path.join(tmp, tgz)) as tar:
        members = [m for m in tar.getmembers() if m.name.startswith("package/icons/outline/")]
        tar.extractall(tmp, members=members, filter="data")
    return os.path.join(tmp, "package", "icons", "outline")


def snake(name):
    return name.replace("-", "_")


def vector_xml(name, svg):
    ds = []
    for attrs in re.findall(r"<path\b([^>]*?)/?>", svg):
        if 'stroke="none"' in attrs:
            continue  # Tabler's invisible 24x24 bounding box
        d = re.search(r'\bd="([^"]+)"', attrs)
        if not d:
            sys.exit(f"{name}.svg: a path without d")
        others = set(re.findall(r"([\w-]+)=", attrs)) - {"d", "fill"}
        if others:
            sys.exit(f"{name}.svg: unsupported path attributes {sorted(others)}")
        ds.append(d.group(1))
    if re.search(r"<(circle|rect|line|polyline|polygon|ellipse)\b", svg):
        sys.exit(f"{name}.svg: has shapes other than <path>; teach vector_xml() to convert them")
    if not ds:
        sys.exit(f"{name}.svg: no drawable paths")
    return (
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        f"<!-- Tabler Icons {TABLER_VERSION} \"{name}\" (outline, MIT), redrawn filled. Generated by tools/tabler-icons/gen_tabler_icons.py. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24">\n'
        "    <path\n"
        f'        android:pathData="{fill_style.filled_path_data(ds, VISUAL_SCALE)}"\n'
        '        android:fillType="evenOdd"\n'
        f'        android:fillColor="{COLOR}" />\n'
        "</vector>\n"
    )


def kotlin(groups):
    group_lines, when_lines = [], []
    for group, names in groups:
        keys = ", ".join(f'"{asset_key(n)}"' for n in names)
        group_lines.append(f"        Group(R.string.icon_group_{group}, listOf({keys})),")
        when_lines += [f'        "{asset_key(n)}" -> R.drawable.{PREFIX_DRAWABLE}{snake(n)}'
                       for n in names if not n.startswith(PREFIX_ASSET)]
    return f"""// GENERATED by tools/tabler-icons/gen_tabler_icons.py from tools/tabler-icons/icons.txt. Do not edit.
package app.outgo.data.icon

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.outgo.R

/**
 * Category icons from Tabler Icons {TABLER_VERSION} (outline, MIT), redrawn filled to match the
 * Phosphor Fill UI and built-in icons, shipped as VectorDrawables.
 * Their `icon.asset_key` is "{PREFIX_KEY}<name>"; the bundled PNGs' keys never start with it.
 * [GROUPS] also places every bundled PNG (by its plain key) in a group, ahead of the Tabler icons.
 * The drawables are referenced here directly, so resource shrinking keeps them.
 */
object TablerIcons {{
    const val KEY_PREFIX = "{PREFIX_KEY}"

    /** A titled section of the category icon picker. */
    class Group(@StringRes val title: Int, val keys: List<String>)

    val GROUPS: List<Group> = listOf(
{chr(10).join(group_lines)}
    )

    @DrawableRes
    fun drawable(assetKey: String): Int? = when (assetKey) {{
{chr(10).join(when_lines)}
        else -> null
    }}
}}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tabler-dir", help="an unpacked @tabler/icons icons/outline folder (default: npm pack)")
    parser.add_argument("--prune", action="store_true", help="delete ic_cat_tabler_* drawables no longer listed")
    args = parser.parse_args()

    groups = read_list()
    names = [n for _, ns in groups for n in ns if not n.startswith(PREFIX_ASSET)]
    wanted = {f"{PREFIX_DRAWABLE}{snake(n)}.xml" for n in names}
    stale = sorted(f for f in os.listdir(DRAWABLE_DIR) if f.startswith(PREFIX_DRAWABLE) and f not in wanted)
    if stale and not args.prune:
        sys.exit("Shipped icons are missing from icons.txt (their keys may be in use): "
                 + ", ".join(stale) + "\nPut them back, or pass --prune if they were never released.")

    with tempfile.TemporaryDirectory() as tmp:
        source = args.tabler_dir or fetch_tabler(tmp)
        missing = [n for n in names if not os.path.exists(os.path.join(source, n + ".svg"))]
        if missing:
            sys.exit(f"Not in Tabler {TABLER_VERSION} outline: {', '.join(missing)}")
        for n in names:
            svg = open(os.path.join(source, n + ".svg"), encoding="utf-8").read()
            with open(os.path.join(DRAWABLE_DIR, f"{PREFIX_DRAWABLE}{snake(n)}.xml"), "w", encoding="utf-8", newline="\n") as out:
                out.write(vector_xml(n, svg))
    for f in stale:
        os.remove(os.path.join(DRAWABLE_DIR, f))
    with open(KOTLIN_OUT, "w", encoding="utf-8", newline="\n") as out:
        out.write(kotlin(groups))
    print(f"{len(names)} icons in {len(groups)} groups -> res/drawable/{PREFIX_DRAWABLE}*.xml, TablerIcons.kt"
          + (f"; pruned {len(stale)}" if stale else ""))


if __name__ == "__main__":
    main()
