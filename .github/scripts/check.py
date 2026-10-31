"""Cheap pre-build checks for AudioFX. Usage: check.py <R.txt>
1. Java files parse (syntax only).
2. Every R.<type>.<name> used in src/ exists in the linked resources (R.txt).
3. Every class named in AndroidManifest.xml exists in src/.
shortcut: no type checking or hidden-API checks, upgrade by compiling against a hidden-API android.jar."""
import re, sys, pathlib, xml.etree.ElementTree as ET
import javalang

errs = []
src = list(pathlib.Path("src").rglob("*.java"))
pkgs = {}
for f in src:
    text = f.read_text(encoding="utf-8")
    try:
        tree = javalang.parse.parse(text)
    except Exception as e:
        errs.append(f"{f}: syntax error: {e}")
        continue
    if tree.package:
        pkgs[f"{tree.package.name}.{f.stem}"] = f

have = set()
for line in open(sys.argv[1]):
    p = line.split()
    if len(p) >= 4 and p[0] == "int":
        have.add((p[1], p[2]))
    elif len(p) >= 3 and p[0] == "int[]":
        have.add(("styleable", p[1]))
for f in src:
    text = re.sub(r"//.*|/\*.*?\*/", "", f.read_text(encoding="utf-8"), flags=re.S)
    for m in re.finditer(r"(?<![\w.])R\.(\w+)\.(\w+)", text):
        t, n = m.groups()
        if (t, n) not in have:
            errs.append(f"{f}: R.{t}.{n} not found in resources")

ns = "{http://schemas.android.com/apk/res/android}name"
man = ET.parse("AndroidManifest.xml").getroot()
pkg = man.get("package", "")
for el in man.iter():
    n = el.get(ns)
    if n and el.tag in ("activity", "activity-alias", "service", "receiver", "provider", "application"):
        full = pkg + n if n.startswith(".") else (n if "." in n else f"{pkg}.{n}")
        if full.split("$")[0] not in pkgs and not n.startswith("android."):
            errs.append(f"AndroidManifest.xml: <{el.tag}> class {full} not found in src/")

print("\n".join(errs) or "all checks passed")
sys.exit(1 if errs else 0)
