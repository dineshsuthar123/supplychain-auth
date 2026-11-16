"""Small disposable-stack acceptance run. Never targets a production stack or OCI account."""
import argparse,http.cookiejar,json,os,pathlib,platform,re,secrets,ssl,subprocess,sys,time,urllib.request,urllib.error,uuid

ROOT=pathlib.Path(__file__).resolve().parents[2]
REPORTS=ROOT/'reports/oracle'
ENVFILE=REPORTS/'local.env'
PROJECT='sp-oracle-check'
CONFIG=ROOT/'deploy/oracle/compose.yml'
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--start',action='store_true');p.add_argument('--skip-rollback-test',action='store_true')
a=p.parse_args()
REPORTS.mkdir(parents=True,exist_ok=True)
def command(args,body=None,env=None,check=True):
    r=subprocess.run(args,input=body,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env)
    if check and r.returncode:
        (REPORTS/'last-command-error.log').write_bytes(r.stdout+r.stderr)
        raise RuntimeError('Validation command failed; see private reports/oracle/last-command-error.log')
    return r
def compose(*args,**kwargs):return command(['docker','compose','-p',PROJECT,'--env-file',str(ENVFILE),'-f',str(CONFIG),*args],**kwargs)
if not ENVFILE.exists():
    subprocess.run([sys.executable,str(ROOT/'deploy/oracle/generate-env.py'),'--host','localhost','--origin','https://localhost:18443','--email','validation@example.test','--image','supplyprint-oracle:arm-validation','--output',str(ENVFILE)],check=True)
    with ENVFILE.open('a',encoding='utf-8') as f:f.write('PROXY_BIND_IP=127.0.0.1\nHTTP_PORT=18080\nHTTPS_PORT=18443\nDATABASE_NETWORK_NAME=sp-oracle-check-database\nEDGE_NETWORK_NAME=sp-oracle-check-edge\n')
settings=dict(x.split('=',1) for x in ENVFILE.read_text(encoding='utf-8').splitlines() if x and not x.startswith('#'))
if settings.get('API_HOST')!='localhost' or settings.get('PROXY_BIND_IP')!='127.0.0.1':raise SystemExit('This script only operates on a disposable localhost deployment.')
compose('config','--quiet')
if a.start:compose('up','--detach','--wait','--wait-timeout','600')
ids={service:compose('ps','--quiet',service).stdout.decode().strip() for service in ['api','db','proxy']}
if not all(ids.values()):raise SystemExit('Start the disposable Compose stack first.')
for service,identifier in ids.items():
    label=command(['docker','inspect',identifier,'--format','{{index .Config.Labels "com.docker.compose.project"}}']).stdout.decode().strip()
    if label!=PROJECT:raise SystemExit('Refusing to operate on another project.')
ca=REPORTS/'local-test-ca.crt'
for attempt in range(30):
    result=command(['docker','cp',ids['proxy']+':/data/caddy/pki/authorities/local/root.crt',str(ca)],check=False)
    if result.returncode==0:break
    time.sleep(1)
else:raise RuntimeError('The local test CA was not generated.')
context=ssl.create_default_context(cafile=str(ca))
BASE='https://localhost:18443';ORIGIN=BASE
class Client:
    def __init__(self):
        self.cookies=http.cookiejar.CookieJar();self.token=None
        self.http=urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),urllib.request.HTTPCookieProcessor(self.cookies))
    def call(self,path,body=None,content_type='application/json',authenticated=True):
        headers={'Origin':ORIGIN,'Accept':'application/json'}
        if self.token and authenticated:headers['Authorization']='Bearer '+self.token
        if body is not None:
            headers['Content-Type']=content_type
            if isinstance(body,dict):body=json.dumps(body).encode()
        try:
            with self.http.open(urllib.request.Request(BASE+path,data=body,headers=headers),timeout=90) as r:return r.status,json.loads(r.read())
        except urllib.error.HTTPError as e:
            raw=e.read()
            try:data=json.loads(raw)
            except ValueError:data={}
            return e.code,data
    def operator(self):
        suffix=uuid.uuid4().hex[:16]
        email='oracle-'+suffix+'@example.test';password=secrets.token_urlsafe(24)
        status,created=self.call('/auth/register',dict(email=email,username='oci'+suffix,password=password,role='ADMIN'))
        if status!=201 or created['user']['role']!='USER' or 'refreshToken' in created:raise RuntimeError('Safe registration did not pass.')
        cookie=next(iter(self.cookies))
        if not cookie.secure or cookie.domain_specified or not cookie.has_nonstandard_attr('HttpOnly'):raise RuntimeError('Cookie attributes incorrect.')
        # Only these newly created synthetic users, in this explicitly disposable local project.
        sql("UPDATE users SET role='MANUFACTURER' WHERE email='"+email+"';")
        status,logged=self.call('/auth/login',dict(emailOrUsername=email,password=password))
        if status!=200:raise RuntimeError('Real login failed.')
        self.token=logged['accessToken']
        return email
def sql(text):
    return compose('exec','-T','db','psql','-X','-q','-A','-t','-v','ON_ERROR_STOP=1','-U','postgres','-d','supplyprint',body=(text+'\n').encode()).stdout.decode().strip()
def ready():
    for attempt in range(150):
        try:
            with urllib.request.urlopen('http://127.0.0.1:18081/actuator/health/readiness',timeout=3) as r:
                if json.load(r)['status']=='UP':return
        except Exception:pass
        time.sleep(2)
    raise RuntimeError('API did not recover.')
def upload(client,endpoint,product):
    boundary='oracle-'+uuid.uuid4().hex
    sample=(ROOT/'backend/product-service/src/test/resources/oracle/sample.png').read_bytes()
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="productId"\r\n\r\n{product}\r\n--{boundary}\r\nContent-Disposition: form-data; name="image"; filename="sample.png"\r\nContent-Type: image/png\r\n\r\n').encode()+sample+f'\r\n--{boundary}--\r\n'.encode()
    return client.call(endpoint,body,'multipart/form-data; boundary='+boundary)
report={'execution_host':platform.machine(),'oci_deployed':False,'checks':{}}
ready()
anonymous=Client()
status,health=anonymous.call('/actuator/health')
if status!=200 or health.get('status')!='UP' or 'components' in health:raise RuntimeError('HTTPS minimal health failed.')
for path in ['/api/dashboard','/auth/siwe/nonce','/swagger-ui/index.html','/actuator/prometheus']:
    if anonymous.call(path)[0] not in [401,403,404]:raise RuntimeError('Unexpected public access: '+path)
owner=Client();owner.operator();product='ARM-'+uuid.uuid4().hex[:16]
status,_=upload(owner,'/api/enroll/image',product)
if status!=202:raise RuntimeError('Image enrollment failed.')
status,result=upload(owner,'/api/verify/image',product)
if status!=200 or not result['verified'] or result['blockchainConfirmed']:raise RuntimeError('Real image verification failed.')
status,evidence=owner.call('/api/verify/'+product+'/log')
if status!=200:raise RuntimeError('Evidence lookup failed.')
other=Client();other.operator()
if other.call('/api/verify/'+product+'/log')[0]!=404:raise RuntimeError('Cross-tenant disclosure.')
status,result=upload(other,'/api/verify/image',product)
if status!=200 or result['verified']:raise RuntimeError('Cross-tenant match accepted.')
dimensions=sql("SELECT vector_dims(embedding),vector_norm(embedding) FROM product_fingerprints WHERE product_id='"+product+"';")
dim,norm=dimensions.split('|')
if int(dim)!=128 or abs(float(norm)-1)>0.0001:raise RuntimeError('Actual stored inference output failed dimension/norm checks.')
if owner.call('/auth/refresh',{})[0]!=200:raise RuntimeError('HTTPS refresh cookie failed.')
for service in ['api','db']:
    arch=command(['docker','exec',ids[service],'uname','-m']).stdout.decode().strip()
    if arch!='aarch64':raise RuntimeError('An actual ARM64 process was not used.')
    report['checks'][service+'_architecture']=arch
api_info=json.loads(command(['docker','inspect',ids['api']]).stdout)[0]
if api_info['Config']['User']!='10001:10001' or not api_info['HostConfig']['ReadonlyRootfs']:raise RuntimeError('Container privilege policy failed.')
ports={service:json.loads(command(['docker','inspect',identifier]).stdout)[0]['HostConfig']['PortBindings'] for service,identifier in ids.items()}
if ports['db']:raise RuntimeError('Database has a published port.')
if any(x['HostIp']!='127.0.0.1' for x in ports['api'].get('8080/tcp',[])):raise RuntimeError('API exposed outside loopback.')
report['checks'].update(https=True,actual_onnx_dimensions=int(dim),actual_vector_norm=float(norm),tenant_isolation=True,public_privilege_escalation_blocked=True,private_database=True)
print('Validated real HTTPS, authentication, ARM64 inference and tenant isolation.',flush=True)
compose('restart','db','api');ready()
if owner.call('/api/verify/'+product+'/log')[0]!=200:raise RuntimeError('Accepted data did not persist through restart.')
report['checks']['restart_persistence']=True
print('Accepted image record persisted after API and database restart.',flush=True)
env=os.environ.copy();env.update(ORACLE_ENV_FILE=str(ENVFILE),ORACLE_PROJECT_NAME=PROJECT)
bash='bash' if os.name!='nt' else r'C:\Program Files\Git\bin\bash.exe'
def operation(name,*args,check=True,override=None):
    selected=env.copy();selected.update(override or {})
    script=ROOT/'deploy/oracle'/name
    return command([bash,str(script),*args],env=selected,check=check)
backup=operation('backup.sh').stdout.decode().strip().splitlines()[-1]
operation('restore-check.sh',backup)
report['checks']['isolated_backup_restore']=True
print('Logical backup restored successfully in an isolated database.',flush=True)
if not a.skip_rollback_test:
    bad=REPORTS/'Bad.Dockerfile';bad.write_text('FROM supplyprint-oracle:arm-validation\nENTRYPOINT ["/bin/sh","-c","exit 1"]\n')
    command(['docker','build','--platform','linux/arm64','-t','supplyprint-oracle:deliberate-failure','-f',str(bad),str(ROOT)])
    bad_id=command(['docker','image','inspect','supplyprint-oracle:deliberate-failure','--format','{{.Id}}']).stdout.decode().strip()
    failed=operation('ops.sh','update',bad_id,check=False,override={'ORACLE_READY_ATTEMPTS':'5'})
    if failed.returncode==0 or b'Application update failed; previous image restored.' not in failed.stderr:
        raise RuntimeError('The controlled failure did not exercise the rollback path.')
    ready()
    if owner.call('/api/verify/'+product+'/log')[0]!=200:raise RuntimeError('Rollback lost accepted data.')
    restored_id=compose('ps','--quiet','api').stdout.decode().strip()
    restored=json.loads(command(['docker','inspect',restored_id]).stdout)[0]
    if restored['Image']!=api_info['Image']:raise RuntimeError('Previous application image was not restored.')
    report['checks']['failed_update_restored_previous_image']=True
report['limits']='Local ARM64 execution only; no Oracle VM, public domain, OCI firewall or VM reboot was verified.'
(REPORTS/'local-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
