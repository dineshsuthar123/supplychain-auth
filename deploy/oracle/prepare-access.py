"""Generate a deployment-only SSH key locally; never upload its private half."""
import os,pathlib,subprocess
state=pathlib.Path(__file__).resolve().parent/'state'
state.mkdir(parents=True,exist_ok=True,mode=0o700)
for name in ['operator_ed25519','deployment_ed25519']:
    key=state/name
    if key.exists():continue
    subprocess.run(['ssh-keygen','-q','-t','ed25519','-N','','-C','supplyprint-oracle-'+name,'-f',str(key)],check=True)
    os.chmod(key,0o600)
    if os.name=='nt':
        user=subprocess.check_output(['whoami'],text=True).strip()
        subprocess.run(['icacls',str(key),'/inheritance:r','/grant:r',user+':(F)','/grant:r','SYSTEM:(F)'],check=True,stdout=subprocess.DEVNULL)
print('Independent administrator and deployment SSH keys are ready in ignored state/. Existing keys were preserved; upload only public halves.')
