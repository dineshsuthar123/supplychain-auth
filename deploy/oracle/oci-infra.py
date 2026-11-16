"""Inspect OCI first; create only the fixed Always Free design after verified evidence.

No delete, account-upgrade, billing, paid fallback, load-balancer or NAT operation exists here.
State contains private tenancy metadata and belongs outside Git.
"""
import argparse, datetime as dt, hashlib, ipaddress, json, os, pathlib, sys, uuid

ROOT=pathlib.Path(__file__).resolve().parent
STATE=ROOT/'state'
SHAPE='VM.Standard.A1.Flex'
REGION='ap-hyderabad-1'

def save(name,value):
    STATE.mkdir(mode=0o700,parents=True,exist_ok=True)
    target=STATE/name
    target.write_text(json.dumps(value,indent=2,default=str)+'\n',encoding='utf-8')
    os.chmod(target,0o600)

def now():return dt.datetime.now(dt.timezone.utc)

def require(condition,message):
    if not condition:raise ValueError(message)

def check_console_evidence(path,tenancy):
    evidence=json.loads(pathlib.Path(path).read_text(encoding='utf-8'))
    require(evidence['tenancy_sha256']==hashlib.sha256(tenancy.encode()).hexdigest(), 'Evidence is for another tenancy')
    require(evidence['account_type']=='ALWAYS_FREE_NOT_PAYG', 'Paid/trial-only eligibility is not acceptable')
    require(evidence['home_region']==REGION and evidence['shape_always_free_eligible'] is True, 'Console eligibility not verified')
    age=(now()-dt.datetime.fromisoformat(evidence['verified_at'])).total_seconds()
    require(0<=age<=86400, 'Refresh the Console billing/eligibility evidence')

def enforce(snapshot):
    for key in ['existing_a1_ocpus','existing_a1_ram_gb','existing_storage_gb','quota_core_available','quota_ram_available']:
        value=snapshot.get(key)
        require(isinstance(value,(int,float)) and not isinstance(value,bool) and value>=0, 'Unavailable or invalid tenancy inventory/quota: '+key)
    require(snapshot['home_region']==REGION, 'Always Free must use the tenancy home region; do not provision Hyderabad in another tenancy')
    reuse=snapshot.get('reuse_existing',False)
    require(snapshot['existing_a1_ocpus']+(0 if reuse else 2)<=2, 'A1 OCPU allocation would exceed 2 total')
    require(snapshot['existing_a1_ram_gb']+(0 if reuse else 12)<=12, 'A1 memory allocation would exceed 12 GB total')
    require(snapshot['existing_storage_gb']+(0 if reuse else 50)<=200, 'Combined boot/block storage would exceed 200 GB')
    require(snapshot['quota_core_available']>=(0 if reuse else 2) and snapshot['quota_ram_available']>=(0 if reuse else 12), 'Insufficient verified A1 quota')
    require(snapshot['shape_billing_type'] in ['ALWAYS_FREE','LIMITED_FREE'], 'Shape is not reported as free eligible')
    require(snapshot['ubuntu_image_id'] and snapshot['image_a1_compatible'], 'An Oracle-provided Ubuntu ARM image is required')

def clients(args):
    try:import oci
    except ImportError:raise SystemExit('Install deploy/oracle/requirements-oci.txt in a private virtual environment.')
    config_path=pathlib.Path(args.config).expanduser()
    if not config_path.is_file():raise SystemExit('Missing OCI access: configure ~/.oci/config and keep its private key on this machine. No resources created.')
    cfg=oci.config.from_file(str(config_path),args.profile)
    kwargs={}
    if cfg.get('security_token_file'):
        token=pathlib.Path(cfg['security_token_file']).expanduser().read_text().strip()
        key=oci.signer.load_private_key_from_file(cfg['key_file'],cfg.get('pass_phrase'))
        kwargs['signer']=oci.auth.signers.SecurityTokenSigner(token,key)
    cfg['region']=REGION
    return oci,cfg,kwargs

def inspect(args):
    oci,cfg,kwargs=clients(args)
    iam=oci.identity.IdentityClient(cfg,**kwargs)
    core=oci.core.ComputeClient(cfg,**kwargs)
    network=oci.core.VirtualNetworkClient(cfg,**kwargs)
    storage=oci.core.BlockstorageClient(cfg,**kwargs)
    limits=oci.limits.LimitsClient(cfg,**kwargs)
    tenancy=cfg['tenancy']
    subscriptions=iam.list_region_subscriptions(tenancy).data
    home=next(x.region_name for x in subscriptions if x.is_home_region)
    require(home==REGION, 'The account home region is not Hyderabad; no resources may be created there under this design')
    compartments=[tenancy]+[x.id for x in oci.pagination.list_call_get_all_results(iam.list_compartments,
        tenancy,compartment_id_in_subtree=True,access_level='ANY',lifecycle_state='ACTIVE').data]
    domains=iam.list_availability_domains(tenancy).data
    require(domains, 'No availability domains returned')
    allocations=[];volumes=[]
    for compartment in compartments:
        allocations += [x for x in oci.pagination.list_call_get_all_results(core.list_instances,compartment).data if x.lifecycle_state not in ['TERMINATED','TERMINATING']]
        volumes += [x for x in oci.pagination.list_call_get_all_results(storage.list_volumes,compartment_id=compartment).data if x.lifecycle_state not in ['TERMINATED','TERMINATING']]
        for domain in domains:
            volumes += [x for x in oci.pagination.list_call_get_all_results(storage.list_boot_volumes,availability_domain=domain.name,compartment_id=compartment).data if x.lifecycle_state not in ['TERMINATED','TERMINATING']]
    target=args.compartment or tenancy
    shapes=[s for s in oci.pagination.list_call_get_all_results(core.list_shapes,target,availability_domain=domains[0].name).data if s.shape==SHAPE]
    require(shapes, 'A1 shape unavailable in the selected availability domain')
    images=oci.pagination.list_call_get_all_results(core.list_images,target,operating_system='Canonical Ubuntu',operating_system_version='24.04',shape=SHAPE,sort_by='TIMECREATED',sort_order='DESC').data
    images=[x for x in images if x.compartment_id is None and x.lifecycle_state=='AVAILABLE']
    require(images, 'No Oracle-provided Ubuntu 24.04 A1 image returned')
    compatible=core.get_image_shape_compatibility_entry(images[0].id,SHAPE).data
    a1=[x for x in allocations if x.shape==SHAPE]
    if args.reuse_instance:
        require(any(x.id==args.reuse_instance for x in a1),'The selected existing instance is not an active A1 allocation in the inspected tenancy')
    def available(name):return limits.get_resource_availability('compute',name,tenancy).data.available
    result={'verified_at':now().isoformat(),'home_region':home,'compartment_id':target,'availability_domain':domains[0].name,
        'existing_a1_ocpus':sum(x.shape_config.ocpus for x in a1),'existing_a1_ram_gb':sum(x.shape_config.memory_in_gbs for x in a1),
        'existing_storage_gb':sum(x.size_in_gbs for x in volumes),'quota_core_available':available('standard-a1-core-count'),
        'quota_ram_available':available('standard-a1-memory-count'),'shape_billing_type':shapes[0].billing_type,
        'ubuntu_image_id':images[0].id,'image_a1_compatible':compatible.shape==SHAPE,
        'account_type':'NOT_RETURNED_BY_IAM_REQUIRES_CONSOLE_VERIFICATION',
        'tenancy_sha256':hashlib.sha256(tenancy.encode()).hexdigest(),'resources_created':[],
        'reuse_existing':bool(args.reuse_instance),'existing_instance_id':args.reuse_instance}
    save('preflight.json',result)
    enforce(result)
    print('OCI API inventory fits the 2 OCPU / 12 GB / 200 GB total ceilings'+(' without new allocation.' if args.reuse_instance else ', including the planned 50 GB boot volume.')+' Console billing and Always Free labels must also be verified.')
    return oci,cfg,kwargs,result,network,core

def provision(args):
    require(args.console_evidence and args.ssh_public_key and args.ssh_cidr, 'Provide Console evidence, an SSH public key and the approved source CIDR')
    state_path=STATE/'provisioned.json'
    require(not state_path.exists(), 'Existing provisioning state found. Inspect it; never create duplicate instances or delete resources automatically.')
    oci,cfg,kwargs,snapshot,network,core=inspect(args)
    check_console_evidence(args.console_evidence,cfg['tenancy'])
    if args.reuse_instance:
        print('Existing eligible A1 instance selected. No resource was created or changed; use its approved SSH access and review existing workloads before configuration.')
        return
    source=ipaddress.ip_network(args.ssh_cidr,strict=False)
    require(source.version==4 and source.prefixlen>=24, 'Use an approved narrow IPv4 source range')
    public_key=pathlib.Path(args.ssh_public_key).expanduser().read_text().strip()
    require(public_key.startswith(('ssh-ed25519 ','ssh-rsa ')) and '\n' not in public_key, 'Provide a single valid SSH public key')
    compartment=snapshot['compartment_id']
    state={'verified_at':now().isoformat(),'region':REGION,'shape':SHAPE,'ocpus':2,'ram_gb':12,'boot_gb':50,
        'account_upgraded':False,'resources':[],'public_https':'PENDING_DNS_AND_CERTIFICATE','deployment':'VM_ONLY_NOT_YET_APP_VALIDATED'}
    def record(kind,value):
        state['resources'].append({'type':kind,'id':value.id})
        save('provisioned.json',state)
        print('Created',kind)
        return value
    retry=oci.retry.NoneRetryStrategy()
    vcn=record('vcn',network.create_vcn(oci.core.models.CreateVcnDetails(compartment_id=compartment,display_name='supplyprint-free-vcn',cidr_block='10.71.0.0/16',dns_label='supplyprint'),retry_strategy=retry,opc_retry_token=str(uuid.uuid4())).data)
    for kind,identifier in [('default-route-table',vcn.default_route_table_id),('default-security-list-unused',vcn.default_security_list_id),('default-dhcp-options',vcn.default_dhcp_options_id)]:
        state['resources'].append({'type':kind,'id':identifier})
    gateway=record('internet-gateway',network.create_internet_gateway(oci.core.models.CreateInternetGatewayDetails(compartment_id=compartment,vcn_id=vcn.id,is_enabled=True,display_name='supplyprint-free-igw'),retry_strategy=retry).data)
    routes=record('route-table',network.create_route_table(oci.core.models.CreateRouteTableDetails(compartment_id=compartment,vcn_id=vcn.id,display_name='supplyprint-public-routes',route_rules=[oci.core.models.RouteRule(network_entity_id=gateway.id,destination='0.0.0.0/0',destination_type='CIDR_BLOCK')]),retry_strategy=retry).data)
    ingress=[]
    for port,cidr in [(22,str(source)),(80,'0.0.0.0/0'),(443,'0.0.0.0/0')]:
        ingress.append(oci.core.models.IngressSecurityRule(protocol='6',source=cidr,is_stateless=False,tcp_options=oci.core.models.TcpOptions(destination_port_range=oci.core.models.PortRange(min=port,max=port))))
    ingress.append(oci.core.models.IngressSecurityRule(protocol='1',source='0.0.0.0/0',is_stateless=False,icmp_options=oci.core.models.IcmpOptions(type=3,code=4)))
    # Egress is not a paid gateway. Do not generate synthetic traffic to keep a VM alive.
    rules=record('security-list',network.create_security_list(oci.core.models.CreateSecurityListDetails(compartment_id=compartment,vcn_id=vcn.id,display_name='supplyprint-22-approved-80-443',ingress_security_rules=ingress,egress_security_rules=[oci.core.models.EgressSecurityRule(protocol='all',destination='0.0.0.0/0',is_stateless=False)]),retry_strategy=retry).data)
    subnet=record('public-subnet',network.create_subnet(oci.core.models.CreateSubnetDetails(compartment_id=compartment,vcn_id=vcn.id,cidr_block='10.71.1.0/24',display_name='supplyprint-free-public',dns_label='public',route_table_id=routes.id,security_list_ids=[rules.id],prohibit_public_ip_on_vnic=False),retry_strategy=retry).data)
    instance=record('instance',core.launch_instance(oci.core.models.LaunchInstanceDetails(compartment_id=compartment,availability_domain=snapshot['availability_domain'],display_name='supplyprint-always-free',shape=SHAPE,
        shape_config=oci.core.models.LaunchInstanceShapeConfigDetails(ocpus=2,memory_in_gbs=12),
        source_details=oci.core.models.InstanceSourceViaImageDetails(image_id=snapshot['ubuntu_image_id'],boot_volume_size_in_gbs=50,boot_volume_vpus_per_gb=0),
        create_vnic_details=oci.core.models.CreateVnicDetails(subnet_id=subnet.id,assign_public_ip=True),metadata={'ssh_authorized_keys':public_key}),retry_strategy=retry,opc_retry_token=str(uuid.uuid4())).data)
    live=oci.wait_until(core,core.get_instance(instance.id),'lifecycle_state','RUNNING',max_wait_seconds=600).data
    attachments=core.list_vnic_attachments(compartment,instance_id=live.id).data
    primary=network.get_vnic(attachments[0].vnic_id).data
    state['resources'].append({'type':'vnic','id':primary.id})
    boot=core.list_boot_volume_attachments(availability_domain=snapshot['availability_domain'],compartment_id=compartment,instance_id=live.id).data
    for attachment in boot:state['resources'].append({'type':'boot-volume','id':attachment.boot_volume_id})
    for private_ip in network.list_private_ips(vnic_id=primary.id).data:
        if private_ip.is_primary:
            public=network.get_public_ip_by_private_ip_id(oci.core.models.GetPublicIpByPrivateIpIdDetails(private_ip_id=private_ip.id)).data
            state['resources'].append({'type':'ephemeral-public-ip','id':public.id})
    state['public_ip']=primary.public_ip
    state['instance_state']=live.lifecycle_state
    state['ssh_source_cidr']=str(source)
    save('provisioned.json',state)
    print('VM is running. Install and validate the application over SSH; HTTPS is not yet claimed.')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=['inspect','provision'])
    p.add_argument('--config',default='~/.oci/config');p.add_argument('--profile',default='DEFAULT')
    p.add_argument('--compartment');p.add_argument('--console-evidence');p.add_argument('--ssh-public-key');p.add_argument('--ssh-cidr')
    p.add_argument('--reuse-instance',help='Inspect and reuse an existing eligible A1 instance; never allocate a duplicate')
    args=p.parse_args()
    try: provision(args) if args.action=='provision' else inspect(args)
    except (AssertionError,KeyError,ValueError) as error:raise SystemExit(str(error))
if __name__=='__main__':main()
