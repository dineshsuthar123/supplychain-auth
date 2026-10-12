"""Generate the reviewed OpenAPI command contract and matching TypeScript DTOs."""
import argparse
import json
import pathlib
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--check',action='store_true')
args=parser.parse_args()
S={'type':'string'}
U={'type':'string','format':'uuid'}
Q={'type':'string','pattern':r'^[0-9]{1,17}(\.[0-9]{1,3})?$','description':'Exact decimal in the product base unit; maximum three decimal places; no rounding.','examples':['350','0.125']}
B={'type':'boolean'}
I={'type':'integer','minimum':0}
T={'type':'string','format':'date-time'}
D={'type':'string','format':'date'}
def ref(name):return {'$ref':'#/components/schemas/'+name}
def array(item,maximum=30):return {'type':'array','items':item,'maxItems':maximum}
def enum(*values):return {'type':'string','enum':list(values)}
def obj(properties,required=None):return {'type':'object','additionalProperties':False,'properties':properties,'required':list(properties) if required is None else required}
schemas={
 'RegisterRequest':obj({'email':{'type':'string','format':'email'},'password':{'type':'string','minLength':12,'maxLength':72},'name':S,'workspaceName':S,'organizationName':S}),
 'LoginRequest':obj({'email':{'type':'string','format':'email'},'password':S}),
 'InvitationAcceptanceRequest':obj({'token':S,'email':{'type':'string','format':'email'},'password':{'type':'string','minLength':12,'maxLength':72},'name':S}),
 'WorkspaceSelectionRequest':obj({'workspaceId':U}),
 'NameRequest':obj({'name':S}),
 'SiteRequest':obj({'organizationId':U,'name':S}),
 'InvitationRequest':obj({'organizationId':U,'email':{'type':'string','format':'email'},'role':enum('OPERATOR','PARTNER_OPERATOR','AUDITOR'),'recallManager':B,'networkRead':B,'siteIds':array(U)},['organizationId','email','role']),
 'PermissionRequest':obj({'membershipId':U,'role':enum('OPERATOR','PARTNER_OPERATOR','AUDITOR'),'recallManager':B,'networkRead':B,'siteIds':array(U)},['membershipId','role']),
 'ProductRequest':obj({'name':S,'baseUnit':enum('unit','kg','g','l','ml')}),
 'CommissionRequest':obj({'productId':U,'siteId':U,'code':{'type':'string','maxLength':120},'quantity':Q,'issuedOn':D,'expiresOn':{'type':['string','null'],'format':'date'},'occurredAt':T},['productId','siteId','code','quantity','issuedOn']),
 'InputLot':obj({'lotId':U,'quantity':Q}),
 'OutputLot':obj({'code':{'type':'string','maxLength':120},'quantity':Q}),
 'OperationRequest':obj({'kind':enum('SPLIT','MERGE','REPACKAGE'),'siteId':U,'inputs':array(ref('InputLot')),'outputs':array(ref('OutputLot')),'declaredLoss':Q,'occurredAt':T},['kind','siteId','inputs','outputs','declaredLoss']),
 'DispositionRequest':obj({'lotId':U,'siteId':U,'kind':enum('HOLD','RECOVER','DISPOSE','LOSS'),'sourceStatus':enum('AVAILABLE','HELD','RECOVERED'),'quantity':Q,'reason':S,'evidence':S,'occurredAt':T},['lotId','siteId','kind','sourceStatus','quantity','reason','evidence']),
 'CorrectionRequest':obj({'lotId':U,'siteId':U,'correctsEventId':U,'direction':enum('ADD','REMOVE'),'sourceStatus':enum('AVAILABLE','HELD'),'quantity':Q,'reason':S,'evidence':S,'occurredAt':T},['lotId','siteId','correctsEventId','direction','sourceStatus','quantity','reason','evidence']),
 'TransferDraftRequest':obj({'sourceSiteId':U,'destinationSiteId':U,'reference':S,'lines':array(ref('InputLot')),'reserve':B,'occurredAt':T},['sourceSiteId','destinationSiteId','reference','lines']),
 'DispatchRequest':obj({'version':I,'occurredAt':T},['version']),
 'ReceiptLine':obj({'lotId':U,'quantity':Q,'condition':enum('GOOD','DAMAGED','HELD'),'reason':S},['lotId','quantity','condition']),
 'ReceiptRequest':obj({'version':I,'lines':array(ref('ReceiptLine')),'occurredAt':T},['version','lines']),
 'DiscrepancyResolutionRequest':obj({'discrepancyId':U,'disposition':enum('LATER_RECEIVED','RETURNED','ACCOUNTABLE_LOSS'),'quantity':Q,'reason':S,'evidence':S,'occurredAt':T},['discrepancyId','disposition','quantity','reason','evidence']),
 'CancelRequest':obj({'reason':S,'version':I}),
 'RecallPreviewRequest':obj({'roots':array(U)}),
 'RecallActivationRequest':obj({'roots':array(U),'title':S,'reason':S,'previewSequence':I,'confirmed':B}),
 'AcknowledgmentRequest':obj({'note':S}),
 'DecisionRequest':obj({'reason':S}),
 'ObservationRequest':obj({'lotId':U,'quantity':Q,'reason':S,'evidence':S,'occurredAt':T},['lotId','quantity','reason','evidence']),
 'ObservationResolutionRequest':obj({'reason':S,'evidence':S}),
 'Error':obj({'code':S,'message':S,'traceId':S,'details':{'type':'object','additionalProperties':True}},['code','message']),
 'Accepted':obj({'id':U,'sequence':I,'commandId':U,'outputs':array(obj({'id':U,'code':S,'quantity':Q})),'invitationPath':S},['sequence','commandId']),
 'Membership':obj({'workspace_id':U,'workspace_name':S,'organization_id':U,'organization_name':S,'role':enum('OWNER','OPERATOR','PARTNER_OPERATOR','AUDITOR'),'recall_manager':B,'network_read':B,'active':B}),
 'Session':obj({'accessToken':S,'user':obj({'id':U,'email':S,'name':S}),'memberships':array(ref('Membership'),100)}),
 'Lot':obj({'id':U,'code':S,'product_id':U,'product_name':S,'base_unit':S,'issuer_id':U,'issuer_name':S,'issued_on':D,'expires_on':{'type':['string','null']},'created_sequence':I}),
 'Holding':obj({'lot_id':U,'code':S,'product_name':S,'base_unit':S,'site_id':U,'site_name':S,'organization_id':U,'organization_name':S,'status':enum('AVAILABLE','RESERVED','HELD','IN_TRANSIT','UNRESOLVED','RECOVERED','DISPOSED','LOST','CONSUMED'),'transfer_id':{'type':['string','null']},'quantity':Q}),
 'Lineage':obj({'input_lot_id':U,'output_lot_id':U,'operation_id':U,'sequence':I}),
 'TraceSnapshot':obj({'sequence':I,'historical':B,'complete':B,'scope':S,'lots':array(ref('Lot'),2000),'lineage':array(ref('Lineage'),10000),'holdings':array(ref('Holding'),5000),'events':array({'type':'object','additionalProperties':True},200),'transfers':array({'type':'object','additionalProperties':True},500)}),
 'RecallPreview':obj({'sequence':I,'complete':B,'baseUnit':S,'coverage':S,'roots':array(U),'affectedLots':array({'type':'object','additionalProperties':True},2000),'exposure':array({'type':'object','additionalProperties':True},10000),'unaffected':array({'type':'object','additionalProperties':True},10000),'totals':{'type':'object','additionalProperties':S},'gaps':array({'type':'object','additionalProperties':True},10000)})
}
paths={}
def route(path,method,description,request=None,response=None,auth=True,key=True,query=()):
    operation={'summary':description,'operationId':method+'_'+path.replace('/','_').replace('{','').replace('}','').replace('-','_'),'security':[{'bearer':[]}] if auth else [],'responses':{'200':{'description':'Accepted command or scoped read','content':{'application/json':{'schema':ref(response) if response else {'type':'object','additionalProperties':True}}}},'401':{'description':'Session required','content':{'application/json':{'schema':ref('Error')}}},'403':{'description':'Membership or origin denied'},'409':{'description':'Stale scope/entity or conflicting idempotency key'},'422':{'description':'Deterministic domain rule blocks this command'}}}
    parameters=[]
    if '{id}' in path:parameters.append({'in':'path','name':'id','required':True,'schema':U})
    if method=='post' and key:parameters.append({'in':'header','name':'Idempotency-Key','required':True,'schema':{'type':'string','minLength':8,'maxLength':128}})
    for name in query:parameters.append({'in':'query','name':name,'schema':I})
    if parameters:operation['parameters']=parameters
    if request:operation['requestBody']={'required':True,'content':{'application/json':{'schema':ref(request)}}}
    paths.setdefault(path,{})[method]=operation
route('/auth/register','post','Register owner of a new workspace','RegisterRequest','Session',False)
route('/auth/login','post','Authenticate a separate device/session','LoginRequest','Session',False,False)
route('/auth/accept-invitation','post','Accept a single-use email-bound invitation','InvitationAcceptanceRequest','Session',False,False)
for name in ['refresh','logout']:route('/auth/'+name,'post','Rotate or revoke HttpOnly session cookie',None,'Session' if name=='refresh' else None,False,False)
route('/auth/me','get','Load current authenticated membership',response='Session')
route('/auth/workspace','post','Select an existing granted workspace','WorkspaceSelectionRequest','Session',True,False)
route('/workspace','get','Read permitted workspace directory')
for endpoint,request in [('organizations','NameRequest'),('sites','SiteRequest'),('invitations','InvitationRequest'),('permissions','PermissionRequest')]:route('/workspace/'+endpoint,'post','Owner workspace administration',request,'Accepted')
route('/products','get','Read the workspace product catalog')
route('/products','post','Create product with an explicit base unit','ProductRequest','Accepted')
for endpoint,request in [('commission','CommissionRequest'),('operations','OperationRequest'),('dispositions','DispositionRequest'),('corrections','CorrectionRequest')]:route('/inventory/'+endpoint,'post','Record guarded exact-quantity inventory command',request,'Accepted')
route('/inventory/holdings','get','Scoped journal-derived holdings',query=['sequence'])
route('/trace','get','Bounded genealogy, custody and event snapshot',response='TraceSnapshot',query=['sequence'])
route('/transfers','get','Incoming/outgoing transfers within granted site scope')
route('/transfers/{id}','get','Sender and receiver claims, discrepancies and resolutions')
route('/transfers','post','Prepare / reserve an authorized handoff','TransferDraftRequest','Accepted')
for endpoint,request in [('dispatch','DispatchRequest'),('receive','ReceiptRequest'),('resolve','DiscrepancyResolutionRequest'),('cancel','CancelRequest')]:route('/transfers/{id}/'+endpoint,'post','Guarded two-party custody transition',request,'Accepted')
route('/recalls','get','List authorized incidents')
route('/recalls/{id}','get','Incident, preserved revisions, filtered targets and closure policy')
route('/recalls/preview','post','Calculate complete known affected descendants','RecallPreviewRequest','RecallPreview',True,False)
route('/recalls','post','Confirm current preview and synchronously hold stock','RecallActivationRequest','Accepted')
for endpoint,request in [('acknowledge','AcknowledgmentRequest'),('close','DecisionRequest'),('reopen','DecisionRequest')]:route('/recalls/{id}/'+endpoint,'post','Record independent response or authorized incident decision',request,'Accepted')
route('/events','get','Cursor-paginated accepted sequence',query=['after','limit','sequence'])
route('/evidence/export','get','Bounded ZIP snapshot with verifiable manifest',query=['sequence'])
paths['/evidence/export']['get']['responses']['200']={'description':'ZIP of scoped snapshot evidence','content':{'application/zip':{'schema':{'type':'string','format':'binary'}}}}
route('/evidence/projection-check','get','Compare journal-derived balances with current projection')
route('/evidence/rebuild','post','Rebuild derived workspace balances from journal',response='Accepted')
route('/observations','get','Manager evidence gaps, separate from inventory')
route('/observations','post','Report unknown custody without commissioning stock','ObservationRequest','Accepted')
route('/observations/{id}/resolve','post','Append accountable resolution of an evidence observation','ObservationResolutionRequest','Accepted')
route('/inbox','get','Current user inbox, bounded to 100 messages')
route('/inbox/{id}/read','post','Record personal inbox read',response='Accepted')
route('/health','get','Minimal application liveness',auth=False)
contract={'openapi':'3.1.0','info':{'title':'Lotline API','version':'1.0.0','description':'Server-resolved session membership. No body/header tenant override. Quantity JSON fields are decimal strings. Accepted commands are idempotent; failed commands roll back and may be retried. Cookie auth endpoints require an exact trusted Origin.'},'servers':[{'url':'/api/v1'}],'paths':paths,'components':{'securitySchemes':{'bearer':{'type':'http','scheme':'bearer','bearerFormat':'JWT'}},'schemas':schemas}}
def ts_type(schema):
    if '$ref' in schema:return schema['$ref'].split('/')[-1]
    if 'enum' in schema:return ' | '.join(json.dumps(v) for v in schema['enum'])
    kind=schema.get('type')
    if isinstance(kind,list):return ' | '.join(ts_type({**schema,'type':k}) for k in kind)
    if kind=='string':return 'string'
    if kind in ['integer','number']:return 'number'
    if kind=='boolean':return 'boolean'
    if kind=='null':return 'null'
    if kind=='array':return '('+ts_type(schema['items'])+')[]'
    if kind=='object' and 'properties' in schema:
        return '{ '+ '; '.join(f'{k}{"" if k in schema.get("required",[]) else "?"}: {ts_type(v)}' for k,v in schema['properties'].items())+' }'
    if kind=='object':return 'Record<string, '+(ts_type(schema['additionalProperties']) if isinstance(schema.get('additionalProperties'),dict) else 'unknown')+'>'
    return 'unknown'
typescript='// Generated from scripts/generate-api.py. Run with --check in CI.\n'+''.join(f'export type {name} = {ts_type(schema)};\n' for name,schema in schemas.items())
for path,text in [(root/'docs/openapi.json',json.dumps(contract,indent=2)+'\n'),(root/'frontend/src/lib/api.generated.ts',typescript)]:
    if args.check:
        if not path.exists() or path.read_text(encoding='utf-8')!=text:raise SystemExit(f'Contract differs: regenerate {path.name}')
    else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8')
print('OpenAPI command schemas and TypeScript DTOs '+('checked.' if args.check else 'generated.'))
