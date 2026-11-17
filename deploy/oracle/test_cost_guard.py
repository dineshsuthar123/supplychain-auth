"""Cost/access checks must fail closed, including when Python assertions are disabled."""
import importlib.util,pathlib,unittest
from types import SimpleNamespace as NS
from unittest import mock
p=pathlib.Path(__file__).with_name('oci-infra.py')
spec=importlib.util.spec_from_file_location('oracle_infra',p)
infra=importlib.util.module_from_spec(spec);spec.loader.exec_module(infra)
class CostGuard(unittest.TestCase):
    def baseline(self):
        return dict(home_region='ap-hyderabad-1',existing_a1_ocpus=0,existing_a1_ram_gb=0,
            existing_storage_gb=150,quota_core_available=2,quota_ram_available=12,
            shape_billing_type='LIMITED_FREE',ubuntu_image_id='official-platform-image',image_a1_compatible=True)
    def test_exact_free_limits_pass(self):infra.enforce(self.baseline())
    def test_existing_resources_are_counted(self):
        for key,value in [('existing_a1_ocpus',1),('existing_a1_ram_gb',1),('existing_storage_gb',151)]:
            with self.subTest(key=key),self.assertRaises(ValueError):infra.enforce(self.baseline()|{key:value})
    def test_paid_wrong_region_or_wrong_image_fails(self):
        for patch in [dict(shape_billing_type='PAID'),dict(home_region='ap-mumbai-1'),dict(image_a1_compatible=False)]:
            with self.subTest(patch=patch),self.assertRaises(ValueError):infra.enforce(self.baseline()|patch)
    def test_unreadable_quota_or_inventory_is_not_zero(self):
        for key in ['existing_storage_gb','quota_core_available','quota_ram_available']:
            with self.subTest(key=key),self.assertRaises(ValueError):infra.enforce(self.baseline()|{key:None})
    def test_insufficient_quota_fails(self):
        with self.assertRaises(ValueError):infra.enforce(self.baseline()|{'quota_core_available':1})
    def test_existing_instance_can_be_reused_without_adding_allocation(self):
        infra.enforce(self.baseline()|dict(reuse_existing=True,existing_a1_ocpus=2,existing_a1_ram_gb=12,existing_storage_gb=200,quota_core_available=0,quota_ram_available=0))
    def resume_state(self):
        return dict(region='ap-hyderabad-1',shape='VM.Standard.A1.Flex',ocpus=2,ram_gb=12,boot_gb=50,account_upgraded=False,
            tenancy_sha256='owner',compartment_id='approved',deployment='BLOCKED_NO_A1_HOST_CAPACITY',
            resources=[dict(type=kind,id='saved-'+kind) for kind in infra.NETWORK_TYPES])
    def resume_snapshot(self):return self.baseline()|dict(tenancy_sha256='owner',compartment_id='approved')
    def test_failed_capacity_launch_reuses_only_complete_saved_network(self):
        infra.enforce_resume(self.resume_state(),self.resume_snapshot())
    def test_resume_rejects_vm_or_uncertain_launch_outcome(self):
        for patch in [dict(deployment='VM_LAUNCH_PENDING'),dict(resources=self.resume_state()['resources']+[dict(type='instance',id='vm')])]:
            with self.subTest(patch=patch),self.assertRaises(ValueError):infra.enforce_resume(self.resume_state()|patch,self.resume_snapshot())
    def test_resume_rejects_changed_owner_region_or_allocation(self):
        for patch in [dict(tenancy_sha256='other'),dict(compartment_id='other'),dict(region='ap-mumbai-1'),dict(ocpus=4),dict(account_upgraded=True)]:
            with self.subTest(patch=patch),self.assertRaises(ValueError):infra.enforce_resume(self.resume_state()|patch,self.resume_snapshot())
    def test_smaller_available_shape_cannot_open_approved_launch_gate(self):
        models=NS(CreateCapacityReportShapeAvailabilityDetails=lambda **k:NS(**k),CapacityReportInstanceShapeConfig=lambda **k:NS(**k),CreateComputeCapacityReportDetails=lambda **k:NS(**k))
        sdk=NS(core=NS(models=models),util=NS(to_dict=lambda x:{}),retry=NS(NoneRetryStrategy=lambda:None))
        for cpu,ram,expected in [(2,12,True),(2,8,False),(2,6,False),(1,6,False)]:
            item=NS(instance_shape='VM.Standard.A1.Flex',instance_shape_config=NS(ocpus=cpu,memory_in_gbs=ram),availability_status='AVAILABLE',available_count=1)
            core=mock.Mock();core.create_compute_capacity_report.return_value.data=NS(shape_availabilities=[item])
            with self.subTest(cpu=cpu,ram=ram),mock.patch.object(infra,'save'),mock.patch('builtins.print'):
                self.assertIs(infra.capacity_report(sdk,{'tenancy':'approved'},dict(availability_domain='home-ad'),core),expected)
if __name__=='__main__':unittest.main()
