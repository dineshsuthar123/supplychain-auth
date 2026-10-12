"""Validate local deployment contracts and optionally the official Render schema."""
import argparse
import json
import pathlib
import urllib.request
import sys
parser=argparse.ArgumentParser()
parser.add_argument('--allow-unconfigured',action='store_true')
parser.add_argument('--official-schema',action='store_true')
args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
vercel=json.loads((root/'frontend/vercel.json').read_text(encoding='utf-8'))
assert not (root/'vercel.json').exists(), 'Only frontend/vercel.json is authoritative'
assert vercel['outputDirectory']=='dist' and vercel['installCommand']=='npm ci'
assert vercel['rewrites'][0]['source']=='/api/:path*'
upstream=vercel['rewrites'][0]['destination']
assert upstream.startswith('https://') and upstream.endswith('/api/:path*')
if '.invalid' in upstream and not args.allow_unconfigured:
    sys.exit('Deployment blocked: run node scripts/configure-vercel.mjs https://YOUR-ACTUAL-API.onrender.com before Vercel import.')
headers={h['key']:h['value'] for h in vercel['headers'][0]['headers']}
assert headers['Cache-Control']=='private, no-store' and headers['CDN-Cache-Control']=='no-store' and headers['Vercel-CDN-Cache-Control']=='no-store'
assert all(r['source'].startswith('/app/') or r['source'] in ['/api/:path*','/login','/create','/invite'] for r in vercel['rewrites'])
blueprint=(root/'render.yaml').read_text(encoding='utf-8')
for required in ['rootDir: backend','dockerfilePath: ./Dockerfile','dockerContext: .','region: singapore','property: connectionString','healthCheckPath: /actuator/health/readiness','generateValue: true']:
    assert required in blueprint,required
assert 'starter' not in blueprint and 'basic-' not in blueprint and 'redis' not in blueprint
assert 'localhost' not in upstream
if args.official_schema:
    import yaml
    import jsonschema
    schema=json.load(urllib.request.urlopen('https://render.com/schema/render.yaml.json',timeout=30))
    jsonschema.validate(yaml.safe_load(blueprint),schema)
    print('Render YAML passed the current official JSON Schema.')
print('Deployment paths, API rewrite precedence, no-store headers, secret references and plan selectors checked.')
if '.invalid' in upstream:print('Vercel upstream is intentionally unconfigured; no live deployment is claimed.')
