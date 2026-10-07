"""Reject common private artifacts from Git's staged snapshot, even git add -f."""
import re
import subprocess
import sys
from pathlib import PurePosixPath

def git(*args):
    return subprocess.check_output(['git', *args])

def main():
    paths = git('ls-files', '-z').decode('utf-8').split('\0')
    forbidden_ext = {'.apk', '.aab', '.dex', '.class', '.jar', '.zip', '.xlsx', '.xls', '.csv', '.tsv', '.db', '.sqlite', '.sqlite3', '.p12', '.pfx', '.jks', '.keystore', '.pem', '.key', '.jpg', '.jpeg', '.png', '.webp', '.pdf'}
    forbidden_dir = {'.private', 'build', 'dist', 'signing', 'input', 'downloads', 'exports', 'tooling'}
    errors = []
    for name in filter(None, paths):
        path = PurePosixPath(name)
        if path.name == 'names.txt' or path.suffix.lower() in forbidden_ext or forbidden_dir.intersection(path.parts) or path.name.startswith('.env') or path.name == 'local.properties':
            errors.append(name + ': private data or build artifact path')
            continue
        mode = git('ls-files', '-s', '--', name).split(b' ', 1)[0]
        if mode == b'120000':
            errors.append(name + ': symlinks are excluded from the public source')
            continue
        content = git('show', ':' + name)
        try:
            text = content.decode('utf-8')
        except UnicodeError:
            errors.append(name + ': unexpected binary file')
            continue
        if b'\0' in content or re.search(r'-----BEGIN (?:[A-Z ]*PRIVATE KEY|CERTIFICATE)-----', text):
            errors.append(name + ': binary or signing material')
        # The patterns are assembled so the scanner itself contains no personal path.
        personal_path = '/' + r'(?:Users|home)' + r'/[^\s/]+/'
        cloud_id = r'(?:libfile|sediment|file)' + r'_[0-9a-f]{24,}'
        direct_password = r'--(?:ks|key)-pass' + r'\s+pass:'
        if re.search(personal_path, text) or re.search(cloud_id, text) or re.search(direct_password, text):
            errors.append(name + ': personal path, file identity or inline signing password')
    if errors:
        print('Public-source check rejected the staged snapshot:', file=sys.stderr)
        for error in errors:
            print('  ' + error, file=sys.stderr)
        return 1
    print('Public-source staged snapshot check passed. Also review for names in prose/code before publishing.')
    return 0

if __name__ == '__main__':
    sys.exit(main())
