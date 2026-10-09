"""Validate private tab-separated student ID/name lines without printing identities."""
import sys
from pathlib import Path

def validate_roster(path):
    rows=Path(path).read_text(encoding='utf-8').splitlines()
    if not rows or len(rows)>2000:
        raise ValueError('A complete roster requires 1–2000 entries.')
    ids=set()
    for i,row in enumerate(rows,1):
        fields=row.split('\t')
        if len(fields)!=2:
            raise ValueError('Expected student ID and name separated by one tab at line %d.' % i)
        student_id,name=fields
        if not student_id or student_id!=student_id.strip() or len(student_id)>64 or not name or name!=name.strip() or len(name)>40:
            raise ValueError('Invalid field at line %d.' % i)
        if any(ord(c)<32 or 127<=ord(c)<=159 or c=='\ufeff' for c in student_id+name):
            raise ValueError('Invalid control/BOM character at line %d.' % i)
        if student_id in ids:
            raise ValueError('Duplicate student ID at line %d.' % i)
        ids.add(student_id)
    return len(rows)

if __name__=='__main__':
    try: print('Validated complete private roster entries:',validate_roster(sys.argv[1]))
    except (IndexError,OSError,UnicodeError,ValueError) as error:
        print('Roster validation failed:',error,file=sys.stderr);sys.exit(1)
