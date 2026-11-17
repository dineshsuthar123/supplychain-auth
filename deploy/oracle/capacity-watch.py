"""One read-only capacity check and an optional local Windows notification.

Task Scheduler repeats this entry point. It has no provisioning mode, SSH command,
credentials in arguments, email service or automatic change to the approved VM.
"""
import argparse
import datetime as dt
import json
import os
import pathlib
import re
import subprocess
import sys

HERE=pathlib.Path(__file__).resolve().parent
APPROVED=(2,12)
CANDIDATES={(2,8),(2,6),(1,6)}

def command(config,profile,summary):
    python=pathlib.Path(sys.executable)
    if python.name.lower()=='pythonw.exe':python=python.with_name('python.exe')
    return [str(python),'-u',str(HERE/'oci-infra.py'),'capacity',
        '--config',str(config),'--profile',profile,'--summary-file',str(summary)]

def assess(summary,started):
    if (summary.get('schema')!=1 or summary.get('region')!='ap-hyderabad-1'
            or summary.get('shape')!='VM.Standard.A1.Flex'
            or summary.get('free_tier_preflight_passed') is not True
            or summary.get('approved_configuration')!=list(APPROVED)):
        raise ValueError('Capacity preflight or approved configuration changed.')
    checked=dt.datetime.fromisoformat(summary['checked_at'])
    if checked.tzinfo is None or not -5<=(checked-started).total_seconds()<=300:
        raise ValueError('Capacity result is stale or has an invalid timestamp.')
    available=set()
    for item in summary['configurations']:
        if any(not isinstance(item[k],(int,float)) or isinstance(item[k],bool) for k in ['ocpus','ram_gb']):
            raise ValueError('Invalid numeric shape configuration.')
        pair=(item['ocpus'],item['ram_gb'])
        if pair not in {APPROVED,*CANDIDATES}:
            raise ValueError('Unexpected configuration; do not issue an availability alert.')
        count=item.get('available_count')
        if item['status']=='AVAILABLE' and (count is None or (isinstance(count,(int,float)) and not isinstance(count,bool) and count>=1)):
            available.add(pair)
    return sorted(available,reverse=True)

def should_notify(available,previous,now):
    if not available:return False
    signature=[list(x) for x in available]
    if previous.get('last_available')!=signature:return True
    last=previous.get('notified_at')
    return not last or (now-dt.datetime.fromisoformat(last)).total_seconds()>=86400

def notify(message):
    if os.name!='nt':raise RuntimeError('Desktop notifications require Windows.')
    powershell=pathlib.Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'
    result=subprocess.run([str(powershell),'-NoProfile','-NonInteractive','-WindowStyle','Hidden',
        '-File',str(HERE/'notify-capacity.ps1'),'-Message',message],
        stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=30,
        creationflags=subprocess.CREATE_NO_WINDOW)
    if result.returncode:raise RuntimeError('Windows notification dispatch failed.')

def private_write(path,data):
    temporary=path.with_name(path.name+'.tmp')
    temporary.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    os.chmod(temporary,0o600);os.replace(temporary,path)

def check_once(config,profile,state_dir):
    started=dt.datetime.now(dt.timezone.utc)
    state_file=state_dir/'monitor-state.json'
    previous=json.loads(state_file.read_text(encoding='utf-8')) if state_file.exists() else {}
    summary_file=state_dir/'latest-capacity.json'
    try:
        result=subprocess.run(command(config,profile,summary_file),stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,timeout=180,
            **({'creationflags':subprocess.CREATE_NO_WINDOW} if os.name=='nt' else {}))
        log=state_dir/'last-check.log'
        log.write_bytes(result.stdout[-262144:]);os.chmod(log,0o600)
        if result.returncode:raise RuntimeError('Authenticated read-only preflight failed; inspect the private last-check.log.')
        summary=json.loads(summary_file.read_text(encoding='utf-8'))
        available=assess(summary,started)
        sent=False
        if should_notify(available,previous,started):
            sizes=', '.join(f'{cpu:g} OCPU/{ram:g} GB' for cpu,ram in available)
            approved=APPROVED in available
            message=('Hyderabad A1 capacity reported: '+sizes+'. '
                +('Approved 2/12 may be retried with the guarded workflow. ' if approved else 'Smaller candidates need your approval and VM validation. ')
                +'No resources were provisioned. Open deploy/oracle/CAPACITY.md.')
            # Persistent alert remains available if Windows suppresses the balloon.
            private_write(state_dir/'last-alert.json',{'at':started.isoformat(),'message':message})
            notify(message);sent=True
        state={'checked_at':started.isoformat(),'status':'AVAILABLE' if available else 'NO_CAPACITY',
            'last_available':[list(x) for x in available],
            'notified_at':started.isoformat() if sent else previous.get('notified_at'),
            'notification_dispatched':sent,'provisioning_performed':False}
        private_write(state_file,state)
        return state
    except Exception:
        # Never treat an old AVAILABLE file as a new successful check.
        private_write(state_file,{'checked_at':started.isoformat(),'status':'CHECK_FAILED',
            'last_available':previous.get('last_available',[]),'notified_at':previous.get('notified_at'),
            'provisioning_performed':False})
        raise

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=pathlib.Path,default=pathlib.Path.home()/'.oci/config')
    parser.add_argument('--profile',default='DEFAULT')
    parser.add_argument('--state-dir',type=pathlib.Path,default=pathlib.Path(os.environ.get('LOCALAPPDATA',str(pathlib.Path.home())))/'SupplyPrint/OracleCapacity')
    parser.add_argument('--test-notification',action='store_true')
    args=parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}',args.profile):parser.error('Invalid OCI profile name.')
    args.state_dir.mkdir(mode=0o700,parents=True,exist_ok=True)
    if args.test_notification:
        notify('Notification test only. This does not mean Oracle A1 capacity is available. No resources were provisioned.')
        return
    try:
        state=check_once(args.config.expanduser().resolve(),args.profile,args.state_dir.resolve())
        if sys.stdout:print(json.dumps(state,indent=2))
    except Exception:
        if sys.stderr:print('Capacity check failed; inspect private monitor-state.json and last-check.log.',file=sys.stderr)
        raise SystemExit(1)

if __name__=='__main__':main()
