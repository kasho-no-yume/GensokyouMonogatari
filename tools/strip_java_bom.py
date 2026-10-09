"""Strip UTF-8 BOM from every .java file under src/main/java and src/test/java.

PowerShell 5.1's `Set-Content -Encoding UTF8` emits a BOM, which javac rejects with
"illegal character: '\ufeff'". Run this after any PowerShell-driven in-place edit of
Java sources (the repo's .ps1 helpers are pure-ASCII, but ad-hoc inline scripts are not).
"""
import os
import sys

ROOTS = ['src/main/java', 'src/test/java']
BOM = b'\xef\xbb\xbf'
stripped = []
scanned = 0

for root in ROOTS:
    if not os.path.isdir(root):
        continue
    for dirpath, _dirnames, filenames in os.walk(root):
        for filename in filenames:
            if not filename.endswith('.java'):
                continue
            path = os.path.join(dirpath, filename)
            scanned += 1
            with open(path, 'rb') as fh:
                raw = fh.read()
            if raw.startswith(BOM):
                with open(path, 'wb') as fh:
                    fh.write(raw[len(BOM):])
                stripped.append(path)

print('scanned %d java files, stripped BOM from %d' % (scanned, len(stripped)))
for path in stripped:
    print('  ' + path.replace('\\', '/'))
if stripped:
    sys.exit(1)
