"""Generate a private deployment file without printing credentials."""
import argparse, os, pathlib, re, secrets
p=argparse.ArgumentParser()
p.add_argument('--host',required=True)
p.add_argument('--origin',required=True)
p.add_argument('--email',required=True)
p.add_argument('--image',required=True)
p.add_argument('--output',default=str(pathlib.Path(__file__).with_name('.env')))
a=p.parse_args()
if not re.fullmatch(r'[a-zA-Z0-9.-]+',a.host):p.error('Use a hostname without a URL or port.')
if not a.origin.startswith('https://') or any(c in a.origin for c in ['\n','\r','*']):p.error('Use an exact HTTPS frontend origin.')
if any('\n' in x or '\r' in x for x in [a.email,a.image]):p.error('Invalid multiline input.')
values={'APP_IMAGE':a.image,'PLATFORM':'linux/arm64','API_HOST':a.host,'ACME_EMAIL':a.email,'TRUSTED_ORIGINS':a.origin,'BLOCKCHAIN_ENABLED':'false','API_LOCAL_PORT':'18081','BACKUP_RETENTION_DAYS':'7','BACKUP_MAX_MIB':'2048'}
for key in ['DB_ADMIN_PASSWORD','DB_APP_PASSWORD','DB_MIGRATION_PASSWORD','JWT_SECRET']:values[key]=secrets.token_urlsafe(48)
target=pathlib.Path(a.output)
target.parent.mkdir(parents=True,exist_ok=True)
fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w',encoding='utf-8',newline='\n') as output:output.write(''.join(k+'='+v+'\n' for k,v in values.items()))
print('Created private environment file; no existing secrets or databases were replaced.')
