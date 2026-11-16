"""Controlled HTTPS acceptance checks using a supplied, already authorized operator.
No role provisioning, direct database inserts or unencrypted public credentials.
"""
import argparse,http.cookiejar,json,pathlib,ssl,time,urllib.request,urllib.error,uuid
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--base',required=True);p.add_argument('--origin',required=True)
p.add_argument('--ca');p.add_argument('--token-file',type=pathlib.Path)
p.add_argument('--image',type=pathlib.Path);p.add_argument('--output',type=pathlib.Path,default=pathlib.Path('reports/oracle/https-verification.json'))
a=p.parse_args()
if not a.base.startswith('https://') or not a.origin.startswith('https://'):p.error('Use HTTPS, including a trusted local CA for secure-tunnel validation.')
ctx=ssl.create_default_context(cafile=a.ca)
opener=urllib.request.build_opener(urllib.request.HTTPSHandler(context=ctx),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
token=a.token_file.read_text().strip() if a.token_file else None
def call(path,data=None,content_type='application/json',authorized=True):
    headers={'Origin':a.origin,'Accept':'application/json'}
    if data is not None:headers['Content-Type']=content_type
    if token and authorized:headers['Authorization']='Bearer '+token
    started=time.monotonic()
    try:
        with opener.open(urllib.request.Request(a.base.rstrip('/')+path,data=data,headers=headers),timeout=90) as r:
            return r.status,json.loads(r.read()),round(time.monotonic()-started,3)
    except urllib.error.HTTPError as e:return e.code,None,round(time.monotonic()-started,3)
report={'target':a.base,'checks':{}}
status,health,elapsed=call('/actuator/health',authorized=False)
if status!=200 or health.get('status')!='UP' or 'components' in health:raise SystemExit('Public minimal health did not pass.')
report['checks']['https_health']={'status':status,'seconds':elapsed}
for path in ['/api/dashboard','/auth/siwe/nonce','/swagger-ui/index.html','/actuator/prometheus']:
    status,_,_=call(path,authorized=False)
    if status not in [401,403,404]:raise SystemExit('Public route exposure check failed: '+path)
    report['checks'][path]=status
if token:
    if not a.image or not a.image.is_file():p.error('An authorized image fixture is required with an operator token.')
    product='OCI-CHECK-'+uuid.uuid4().hex[:12]
    def upload(endpoint):
        boundary='supplyprint-'+uuid.uuid4().hex
        content=(f'--{boundary}\r\nContent-Disposition: form-data; name="productId"\r\n\r\n{product}\r\n--{boundary}\r\nContent-Disposition: form-data; name="image"; filename="capture.png"\r\nContent-Type: image/png\r\n\r\n').encode()+a.image.read_bytes()+f'\r\n--{boundary}--\r\n'.encode()
        return call(endpoint,content,'multipart/form-data; boundary='+boundary)
    enrolled,_,_=upload('/api/enroll/image')
    verified,body,_=upload('/api/verify/image')
    logged,_,_=call('/api/verify/'+product+'/log')
    if enrolled!=202 or verified!=200 or not body['verified'] or logged!=200:raise SystemExit('Actual image enrollment/verification failed.')
    report['checks']['image_flow']={'enroll':enrolled,'verify':verified,'evidence':logged,'product_id':product,'accuracy_claim':False}
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n')
print('HTTPS acceptance checks passed. Authenticated image flow was '+('executed.' if token else 'not requested; provide an operator token to complete it.'))
