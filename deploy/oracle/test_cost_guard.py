"""Cost/access checks must fail closed, including when Python assertions are disabled."""
import importlib.util,pathlib,unittest
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
if __name__=='__main__':unittest.main()
