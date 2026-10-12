"""Verify an exported Lotline snapshot without installing dependencies."""
import hashlib
import json
import pathlib
import sys
import zipfile

def verify(path):
    path = pathlib.Path(path)
    if path.is_dir():
        read = lambda name: (path / name).read_bytes()
        actual = {p.name for p in path.iterdir() if p.is_file()}
    else:
        archive = zipfile.ZipFile(path)
        names = archive.namelist()
        if len(names) != len(set(names)) or any('/' in name or '\\' in name for name in names):
            raise ValueError('Duplicate or non-local filenames in evidence packet')
        read = archive.read
        actual = set(names)
    manifest = json.loads(read('manifest.json'))
    if manifest['schemaVersion'] != 1:
        raise ValueError('Unsupported manifest schema')
    expected = set(manifest['files']) | {'manifest.json'}
    if actual != expected:
        raise ValueError(f'File inventory differs: {actual ^ expected}')
    for name, entry in manifest['files'].items():
        if pathlib.PurePath(name).name != name or '/' in name or '\\' in name:
            raise ValueError('Manifest contains a non-local path')
        content = read(name)
        if len(content) != entry['bytes'] or hashlib.sha256(content).hexdigest() != entry['sha256']:
            raise ValueError(f'Changed bytes: {name}')
    return manifest

if __name__ == '__main__':
    try:
        result = verify(sys.argv[1])
        print(f"Verified {len(result['files'])} files at sequence {result['sequence']} for workspace {result['workspaceId']}.")
        print('Snapshot byte integrity only; not physical authenticity or an external trust anchor.')
    except (IndexError, KeyError, ValueError, OSError, zipfile.BadZipFile) as error:
        print(f'Verification failed: {error}', file=sys.stderr)
        sys.exit(1)
