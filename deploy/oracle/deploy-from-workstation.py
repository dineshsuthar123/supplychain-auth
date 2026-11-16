"""Deploy a CI-validated commit over pinned SSH from the approved workstation IP.

The image streams over SSH; no registry storage, paid runner or publicly exposed Docker API.
"""
import argparse,gzip,json,os,pathlib,re,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
def run(args,**kwargs):return subprocess.run(args,check=True,**kwargs)
def output(args):return subprocess.check_output(args,text=True).strip()
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--host',required=True);p.add_argument('--key',required=True);p.add_argument('--known-hosts',required=True)
p.add_argument('--initial',action='store_true');p.add_argument('--backup-download',type=pathlib.Path)
a=p.parse_args()
if not re.fullmatch(r'[a-zA-Z0-9.-]+',a.host):p.error('Invalid SSH host.')
for file in [a.key,a.known_hosts]:
    if not pathlib.Path(file).is_file():p.error('SSH key and independently verified known_hosts must exist.')
ssh=['ssh','-i',a.key,'-o','IdentitiesOnly=yes','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UserKnownHostsFile='+a.known_hosts,'-o','ConnectTimeout=15','supplyprint-deploy@'+a.host]
if a.backup_download:
    a.backup_download.mkdir(mode=0o700,parents=True,exist_ok=True)
    remote=output(ssh+['sudo /usr/local/sbin/supplyprint-ops backup-path'])
    if not re.fullmatch(r'[a-zA-Z0-9/_.-]+\.dump',remote):raise SystemExit('Unexpected backup path.')
    for suffix in ['', '.sha256']:
        target=a.backup_download/(pathlib.PurePosixPath(remote).name+suffix)
        fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'wb') as file:run(ssh+['sudo /usr/local/sbin/supplyprint-ops read-backup '+pathlib.PurePosixPath(remote).name+suffix],stdout=file)
    print('Private off-VM backup downloaded. Test its restore before claiming disaster recovery.')
    raise SystemExit(0)
if output(['git','-C',str(ROOT),'status','--porcelain']):raise SystemExit('Commit reviewed changes before deployment.')
sha=output(['git','-C',str(ROOT),'rev-parse','HEAD'])
checks=json.loads(output(['gh','api','repos/dineshsuthar123/supplychain-auth/commits/'+sha+'/check-runs']))
if not any(x['name']=='oracle-arm64' and x['conclusion']=='success' for x in checks['check_runs']):raise SystemExit('The exact commit must pass oracle-arm64 CI first.')
tag='supplyprint-oracle:'+sha
exists=subprocess.run(['docker','image','inspect',tag],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
if not exists:run(['docker','buildx','build','--platform','linux/arm64','--load','--tag',tag,'--file',str(ROOT/'deploy/oracle/Dockerfile'),str(ROOT)])
sender=subprocess.Popen(['docker','image','save',tag],stdout=subprocess.PIPE)
receiver=subprocess.Popen(ssh+['sudo /usr/local/sbin/supplyprint-ops load-image'],stdin=subprocess.PIPE)
try:
    with gzip.GzipFile(fileobj=receiver.stdin,mode='wb',compresslevel=1) as compressed:
        while block:=sender.stdout.read(1024*1024):compressed.write(block)
finally:
    sender.stdout.close();receiver.stdin.close()
if sender.wait() or receiver.wait():raise SystemExit('Image transfer failed; no application update requested.')
image_id=output(ssh+['sudo /usr/local/sbin/supplyprint-ops image-id '+tag])
if not re.fullmatch(r'sha256:[a-f0-9]{64}',image_id):raise SystemExit('Unexpected loaded image ID.')
run(ssh+['sudo /usr/local/sbin/supplyprint-ops deploy' if a.initial else 'sudo /usr/local/sbin/supplyprint-ops update '+image_id])
run(ssh+['sudo /usr/local/sbin/supplyprint-ops health'])
print('Remote application passed readiness. Run verify.py over public HTTPS or the secure tunnel for complete acceptance.')
