"""Read-only check of a completed smoke fixture after an application restart."""
import argparse
import importlib.util
import json
import os
import pathlib
from decimal import Decimal


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('fixture')
    parser.add_argument('--base', default='http://localhost:8080')
    parser.add_argument('--origin', default='http://localhost:5173')
    args = parser.parse_args()
    password = os.environ.get('DEMO_PASSWORD')
    if not password:
        parser.error('Set DEMO_PASSWORD to the password used for this synthetic fixture.')
    fixture = json.loads(pathlib.Path(args.fixture).read_text(encoding='utf-8'))
    if not fixture.get('completed'):
        parser.error('Use a completed scripts/smoke.py fixture.')
    spec = importlib.util.spec_from_file_location('lotline_seed', pathlib.Path(__file__).with_name('seed-demo.py'))
    seed = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(seed)
    client = seed.Client(args.base, args.origin)
    session = client.login(fixture['ownerEmail'], password)
    assert any(m['workspace_id'] == fixture['workspaceId'] for m in session['memberships'])
    incident = client.call('/recalls/' + fixture['recallId'])
    assert incident['recall']['status'] == 'CLOSED', incident['recall']['status']
    preview = client.call('/recalls/preview', {'roots': [fixture['root']]})
    expected = {'recovered': '1180', 'accountableLoss': '20', 'recoverable': '0', 'inTransit': '0', 'unresolved': '0'}
    for name, quantity in expected.items():
        assert Decimal(preview['totals'][name]) == Decimal(quantity), (name, preview['totals'][name])
    assert client.call('/evidence/projection-check')['matches']
    print('Persisted smoke state verified: same workspace; incident CLOSED; 1180 recovered; 20 accountable loss; no unresolved affected quantity; projection matches journal.')


if __name__ == '__main__':
    main()
