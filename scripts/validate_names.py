"""Validate a private one-name-per-line UTF-8 roster without printing names."""
import sys
from pathlib import Path

def validate(path):
    names = Path(path).read_text(encoding='utf-8').splitlines()
    if len(names) > 2000:
        raise ValueError('A roster may contain at most 2000 names.')
    for index, name in enumerate(names, 1):
        if not name or name != name.strip():
            raise ValueError('Blank lines or surrounding spaces at line %d.' % index)
        if len(name) > 40 or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in name):
            raise ValueError('Invalid name length or control character at line %d.' % index)
        if '\ufeff' in name:
            raise ValueError('Unexpected byte-order marker at line %d.' % index)
    return names

if __name__ == '__main__':
    try:
        names = validate(sys.argv[1])
        print('Validated roster entries:', len(names))
        if len(set(names)) != len(names):
            print('Duplicate names are preserved; confirm they represent different students.')
    except (IndexError, OSError, UnicodeError, ValueError) as error:
        print('Roster validation failed:', error, file=sys.stderr)
        sys.exit(1)
