"""Explicit synthetic coffee fixture, created only through authenticated commands."""
import argparse
import json
import os
import pathlib
import urllib.request
import urllib.error
import http.cookiejar
import uuid
from datetime import datetime, timezone

class Client:
    def __init__(self, base, origin):
        self.base = base.rstrip('/')
        self.origin = origin
        self.token = None
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def call(self, path, body=None, method=None, key=None):
        headers = {'Origin': self.origin, 'Accept': 'application/json'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        if body is not None:
            headers['Content-Type'] = 'application/json'
        if method == 'POST' or body is not None:
            headers['Idempotency-Key'] = key or str(uuid.uuid4())
        request = urllib.request.Request(self.base + '/api/v1' + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method or ('POST' if body is not None else 'GET'))
        try:
            with self.opener.open(request, timeout=40) as response:
                content = response.read()
                if response.headers.get_content_type() == 'application/zip':
                    return content
                return json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            problem = json.loads(error.read())
            raise RuntimeError(f"{error.code} {problem.get('code')}: {problem.get('message')}") from None
    def login(self, email, password):
        result = self.call('/auth/login', {'email': email, 'password': password})
        self.token = result['accessToken']
        return result

def seed(base, origin, prefix, password):
    if len(password) < 12:
        raise ValueError('DEMO_PASSWORD must contain at least 12 characters')
    owner = Client(base, origin)
    owner_email = prefix + '+owner@example.test'
    result = owner.call('/auth/register', {'email': owner_email, 'password': password, 'name': 'Mira Vale', 'workspaceName': 'Meridian coffee · SYNTHETIC DEMO', 'organizationName': 'Meridian Coffee Works'}, key='seed-register-' + prefix)
    owner.token = result['accessToken']
    membership = next(m for m in result['memberships'] if m['active'])
    w = membership['workspace_id']
    def command(path, body, name):
        return owner.call(path, body, key='seed-' + prefix + '-' + name)
    source = command('/workspace/sites', {'organizationId': membership['organization_id'], 'name': 'Meridian / packing floor'}, 'source')['id']
    product = command('/products', {'name': 'Coffee · 250 g packaged unit', 'baseUnit': 'unit'}, 'product')['id']
    partners = []
    for index, (name, site) in enumerate([('Northbank retail cooperative', 'Northbank / shelf stock'), ('Harbor warehouse collective', 'Harbor / receiving bay'), ('Third Street provisions', 'Third Street / stockroom'), ('Fieldhouse grocery', 'Fieldhouse / shelf stock')]):
        org = command('/workspace/organizations', {'name': name}, 'org-' + str(index))['id']
        site_id = command('/workspace/sites', {'organizationId': org, 'name': site}, 'site-' + str(index))['id']
        email = prefix + '+partner' + str(index + 1) + '@example.test'
        inv = command('/workspace/invitations', {'organizationId': org, 'email': email, 'role': 'PARTNER_OPERATOR', 'recallManager': False, 'siteIds': [site_id]}, 'invite-' + str(index))
        client = Client(base, origin)
        try:
            accepted = client.call('/auth/accept-invitation', {'token': inv['invitationPath'].split('token=')[1], 'email': email, 'password': password, 'name': ['Isha North', 'Theo Harbor', 'Jules Third', 'Remy Field'][index]})
            client.token = accepted['accessToken']
        except RuntimeError as error:
            if 'INVITATION_EXPIRED' not in str(error):
                raise
            client.login(email, password)
        partners.append({'client': client, 'email': email, 'site': site_id, 'organization': org})
    def commission(code, quantity, label):
        return command('/inventory/commission', {'productId': product, 'siteId': source, 'code': code, 'quantity': quantity, 'issuedOn': '2026-10-01', 'expiresOn': None}, label)['id']
    root = commission('CF-2401', '1200', 'root')
    split = command('/inventory/operations', {'kind': 'SPLIT', 'siteId': source, 'inputs': [{'lotId': root, 'quantity': '1200'}], 'outputs': [{'code': 'CF-2401-A', 'quantity': '700'}, {'code': 'CF-2401-B', 'quantity': '500'}], 'declaredLoss': '0'}, 'split-root')
    a, b = [lot['id'] for lot in split['outputs']]
    second = command('/inventory/operations', {'kind': 'SPLIT', 'siteId': source, 'inputs': [{'lotId': a, 'quantity': '700'}], 'outputs': [{'code': 'CF-2401-A1', 'quantity': '350'}, {'code': 'CF-2401-A2', 'quantity': '350'}], 'declaredLoss': '0'}, 'split-a')
    a1, a2 = [lot['id'] for lot in second['outputs']]
    unrelated = commission('CF-2402', '200', 'unrelated')
    transfers = []
    for index, (lot, quantity, received) in enumerate([(a1, '350', '350'), (a2, '350', '350'), (b, '500', '480'), (unrelated, '200', '200')]):
        t = command('/transfers', {'sourceSiteId': source, 'destinationSiteId': partners[index]['site'], 'reference': 'CF / packing slip ' + str(index + 1), 'lines': [{'lotId': lot, 'quantity': quantity}], 'reserve': False}, 'draft-' + str(index))['id']
        command('/transfers/' + t + '/dispatch', {'version': 0}, 'dispatch-' + str(index))
        partners[index]['client'].call('/transfers/' + t + '/receive', {'version': 1, 'lines': [{'lotId': lot, 'quantity': received, 'condition': 'GOOD', 'reason': 'Receiving count: 20 packages missing' if received != quantity else 'Counted at receiving bay'}]}, key='seed-' + prefix + '-receipt-' + str(index))
        transfers.append(t)
    preview = owner.call('/recalls/preview', {'roots': [root]})
    assert preview['totals']['recoverable'] == '1180', preview
    assert preview['totals']['unresolved'] == '20', preview
    assert preview['totals']['unaffected'] == '200', preview
    return owner, partners, {'workspaceId': w, 'ownerEmail': owner_email, 'partnerEmails': [p['email'] for p in partners], 'sourceSite': source, 'product': product, 'root': root, 'a1': a1, 'a2': a2, 'b': b, 'unrelated': unrelated, 'transfers': transfers, 'partnerSites': [p['site'] for p in partners], 'preview': preview, 'createdAt': datetime.now(timezone.utc).isoformat()}

def finish(owner, partners, fixture, output):
    preview = owner.call('/recalls/preview', {'roots': [fixture['root']]})
    recall = owner.call('/recalls', {'roots': [fixture['root']], 'title': 'Packaging seal defect / CF-2401', 'reason': 'Synthetic supplier seal fault; hold all descendants', 'previewSequence': preview['sequence'], 'confirmed': True})['id']
    for index, lot in enumerate([fixture['a1'], fixture['a2'], fixture['b']]):
        client = partners[index]['client']
        try:
            client.call('/transfers', {'sourceSiteId': partners[index]['site'], 'destinationSiteId': fixture['sourceSite'], 'reference': 'Blocked recall dispatch', 'lines': [{'lotId': lot, 'quantity': '1'}], 'reserve': False})
            raise AssertionError('Affected dispatch draft was accepted')
        except RuntimeError as error:
            assert 'RECALL_HOLD' in str(error), error
        client.call('/recalls/' + recall + '/acknowledge', {'note': 'Synthetic notice received; recovery follows separately'})
        client.call('/inventory/dispositions', {'lotId': lot, 'siteId': partners[index]['site'], 'kind': 'RECOVER', 'sourceStatus': 'HELD', 'quantity': ['350', '350', '480'][index], 'reason': 'Counted and segregated packaging', 'evidence': 'Synthetic recovery register ' + str(index + 1)})
    try:
        owner.call('/recalls/' + recall + '/close', {'reason': 'Attempt before resolving missing stock'})
        raise AssertionError('Closure succeeded with unresolved quantities')
    except RuntimeError as error:
        assert 'CLOSURE_BLOCKED' in str(error), error
    t = fixture['transfers'][2]
    d = owner.call('/transfers/' + t)['discrepancies'][0]
    owner.call('/transfers/' + t + '/resolve', {'discrepancyId': d['id'], 'disposition': 'ACCOUNTABLE_LOSS', 'quantity': '20', 'reason': 'Manager accepts missing sealed packages after investigation', 'evidence': 'Synthetic investigation report 82'})
    owner.call('/recalls/' + recall + '/close', {'reason': '1180 recovered, 20 accepted accountable loss; all target organizations responded'})
    safe = partners[3]['client'].call('/transfers', {'sourceSiteId': partners[3]['site'], 'destinationSiteId': fixture['sourceSite'], 'reference': 'Unaffected onward stock', 'lines': [{'lotId': fixture['unrelated'], 'quantity': '1'}], 'reserve': False})['id']
    partners[3]['client'].call('/transfers/' + safe + '/dispatch', {'version': 0})
    assert owner.call('/evidence/projection-check')['matches']
    output.mkdir(parents=True, exist_ok=True)
    (output / 'lotline-evidence.zip').write_bytes(owner.call('/evidence/export'))
    fixture['recallId'] = recall
    fixture['completed'] = True

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://localhost:8080')
    parser.add_argument('--origin', default='http://localhost:5173')
    parser.add_argument('--prefix', default=os.environ.get('DEMO_PREFIX', 'lotline-demo'))
    parser.add_argument('--complete', action='store_true')
    parser.add_argument('--output', default='artifacts/demo')
    args = parser.parse_args()
    password = os.environ.get('DEMO_PASSWORD')
    if not password:
        parser.error('Set DEMO_PASSWORD explicitly. Production is never seeded on startup.')
    owner, partners, fixture = seed(args.base, args.origin, args.prefix, password)
    output = pathlib.Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    if args.complete:
        finish(owner, partners, fixture, output)
    (output / 'fixture.json').write_text(json.dumps(fixture, indent=2), encoding='utf-8')
    print(json.dumps({k: fixture[k] for k in ['workspaceId', 'ownerEmail', 'partnerEmails']}))
    print('Synthetic fixture created through accepted commands. Password was read from DEMO_PASSWORD and is not written to the fixture.')
