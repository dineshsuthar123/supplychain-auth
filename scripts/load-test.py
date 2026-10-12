"""Modest local real-command benchmark; no synthetic read-throughput claims."""
import argparse
import concurrent.futures
import importlib.util
import json
import os
import pathlib
import platform
import statistics
import time
import uuid
spec=importlib.util.spec_from_file_location('seed',pathlib.Path(__file__).with_name('seed-demo.py'))
seed=importlib.util.module_from_spec(spec)
spec.loader.exec_module(seed)
parser=argparse.ArgumentParser()
parser.add_argument('--base',default='http://localhost:8080')
parser.add_argument('--origin',default='http://localhost:4173')
parser.add_argument('--output',default='artifacts/benchmark.json')
args=parser.parse_args()
password=os.environ.get('DEMO_PASSWORD')
if not password:parser.error('Set DEMO_PASSWORD for the isolated benchmark account')
client=seed.Client(args.base,args.origin)
prefix='bench-'+uuid.uuid4().hex[:10]
session=client.call('/auth/register',{'email':prefix+'@example.test','password':password,'name':'Benchmark operator','workspaceName':'SYNTHETIC benchmark workspace','organizationName':'Bench manufacturer'})
client.token=session['accessToken']
org=session['memberships'][0]['organization_id']
site=client.call('/workspace/sites',{'organizationId':org,'name':'Bench floor'})['id']
product=client.call('/products',{'name':'Coffee packaged unit','baseUnit':'unit'})['id']
def commission(index):return client.call('/inventory/commission',{'productId':product,'siteId':site,'code':f'BENCH-{index:04d}','quantity':'10','issuedOn':'2026-10-01','expiresOn':None})['id']
lots=[commission(i) for i in range(120)]
for _ in range(5):client.call('/trace')
def measured(name,count,concurrency,action):
    durations=[];failures=[];start=time.perf_counter()
    def one(index):
        worker=seed.Client(args.base,args.origin);worker.token=client.token;before=time.perf_counter()
        try:action(worker,index)
        except Exception as error:failures.append(str(error))
        finally:durations.append((time.perf_counter()-before)*1000)
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:list(pool.map(one,range(count)))
    elapsed=time.perf_counter()-start;values=sorted(durations)
    def percentile(n):return round(values[min(len(values)-1,int((len(values)-1)*n))],2)
    return {'name':name,'samples':count,'concurrency':concurrency,'elapsedSeconds':round(elapsed,3),'throughputPerSecond':round(count/elapsed,2),'p50Ms':percentile(.5),'p95Ms':percentile(.95),'p99Ms':percentile(.99),'failures':len(failures),'failureDetails':failures[:5]}
results=[]
results.append(measured('Trace read / 120 lots',200,4,lambda c,i:c.call('/trace')))
results.append(measured('Inventory commissioning command',80,4,lambda c,i:c.call('/inventory/commission',{'productId':product,'siteId':site,'code':f'MEASURED-{i:04d}','quantity':'1','issuedOn':'2026-10-01','expiresOn':None})))
def lifecycle(c,index):
    lot=lots[index];p=c.call('/recalls/preview',{'roots':[lot]})
    recall=c.call('/recalls',{'roots':[lot],'title':f'Measured incident {index}','reason':'Synthetic containment benchmark','previewSequence':p['sequence'],'confirmed':True})['id']
    c.call('/recalls/'+recall+'/acknowledge',{'note':'Counted locally'})
    c.call('/inventory/dispositions',{'lotId':lot,'siteId':site,'kind':'RECOVER','sourceStatus':'HELD','quantity':'10','reason':'Counted and segregated','evidence':'Synthetic benchmark register'})
    c.call('/recalls/'+recall+'/close',{'reason':'All units recovered and response recorded'})
results.append(measured('Recall preview + activate + acknowledge + recover + close (5 requests)',10,1,lifecycle))
trace=client.call('/trace')
report={'recordedAtUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'environment':{'os':platform.platform(),'python':platform.python_version(),'api':args.base,'database':'PostgreSQL 17.9 / Docker Desktop','java':'21.0.9','poolSize':5,'contention':'Inventory and recall commands serialize on one workspace guard; four commissioning clients contend.'},'fixture':{'isolatedWorkspace':True,'lotsAtEnd':len(trace['lots']),'lineageEdges':len(trace['lineage']),'holdingRows':len(trace['holdings']),'sequence':trace['sequence']},'results':results,'limitations':'Loopback, warm backend, small bounded fixture. These figures do not predict Vercel/Render latency, cold starts or physical operations.'}
output=pathlib.Path(args.output);output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
if any(r['failures'] for r in results):raise SystemExit(1)
