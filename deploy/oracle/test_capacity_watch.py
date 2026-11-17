"""Notification decisions must never launch a VM or consume an old availability result."""
import datetime as dt
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock

spec=importlib.util.spec_from_file_location('watch',pathlib.Path(__file__).with_name('capacity-watch.py'))
watch=importlib.util.module_from_spec(spec);spec.loader.exec_module(watch)

class CapacityWatch(unittest.TestCase):
    def setUp(self):self.now=dt.datetime.now(dt.timezone.utc)
    def summary(self,cpu=2,ram=12,status='AVAILABLE',count=None):
        return dict(schema=1,region='ap-hyderabad-1',shape='VM.Standard.A1.Flex',free_tier_preflight_passed=True,
            approved_configuration=[2,12],checked_at=self.now.isoformat(),
            configurations=[dict(ocpus=cpu,ram_gb=ram,status=status,available_count=count)])
    def test_approved_and_reviewed_candidate_are_distinguished(self):
        self.assertEqual(watch.assess(self.summary(),self.now),[(2,12)])
        self.assertEqual(watch.assess(self.summary(1,6),self.now),[(1,6)])
        self.assertNotIn(watch.APPROVED,watch.assess(self.summary(1,6),self.now))
    def test_unavailable_unknown_and_zero_count_do_not_alert(self):
        for summary in [self.summary(status='OUT_OF_HOST_CAPACITY'),self.summary(status='UNKNOWN'),self.summary(count=0),self.summary(count=True)]:
            self.assertEqual(watch.assess(summary,self.now),[])
    def test_stale_wrong_region_unverified_or_unreviewed_configuration_fails(self):
        for patch in [dict(checked_at=(self.now-dt.timedelta(hours=3)).isoformat()),dict(region='ap-mumbai-1'),
                dict(free_tier_preflight_passed=False),dict(configurations=[dict(ocpus=4,ram_gb=24,status='AVAILABLE')])]:
            with self.subTest(patch=patch),self.assertRaises(ValueError):watch.assess(self.summary()|patch,self.now)
    def test_check_command_is_fixed_to_capacity(self):
        command=watch.command(pathlib.Path('local-config'),'DEFAULT',pathlib.Path('private-summary'))
        self.assertEqual(command[3],'capacity')
        self.assertTrue({'provision','retry-launch','--ssh-public-key','--console-evidence'}.isdisjoint(command))
    def test_dedup_notifies_on_new_capacity_or_daily_reminder(self):
        previous=dict(last_available=[[2,12]],notified_at=self.now.isoformat())
        self.assertFalse(watch.should_notify([(2,12)],previous,self.now))
        self.assertTrue(watch.should_notify([(2,8)],previous,self.now))
        self.assertTrue(watch.should_notify([(2,12)],previous,self.now+dt.timedelta(days=1)))
        self.assertFalse(watch.should_notify([],previous,self.now))
    def test_failed_command_never_notifies_from_stale_available_file(self):
        with tempfile.TemporaryDirectory() as temp:
            root=pathlib.Path(temp);(root/'latest-capacity.json').write_text(json.dumps(self.summary()))
            with mock.patch.object(watch.subprocess,'run',return_value=subprocess.CompletedProcess([],1,b'authentication failed')),mock.patch.object(watch,'notify') as notify:
                with self.assertRaises(RuntimeError):watch.check_once(pathlib.Path('config'),'DEFAULT',root)
                notify.assert_not_called()
            self.assertEqual(json.loads((root/'monitor-state.json').read_text())['status'],'CHECK_FAILED')
    def test_successful_empty_capacity_check_has_no_notification_or_provisioning(self):
        with tempfile.TemporaryDirectory() as temp:
            root=pathlib.Path(temp);(root/'latest-capacity.json').write_text(json.dumps(self.summary(status='OUT_OF_HOST_CAPACITY')))
            with mock.patch.object(watch.subprocess,'run',return_value=subprocess.CompletedProcess([],0,b'checked')),mock.patch.object(watch,'notify') as notify:
                state=watch.check_once(pathlib.Path('config'),'DEFAULT',root);notify.assert_not_called()
            self.assertEqual(state['status'],'NO_CAPACITY');self.assertIs(state['provisioning_performed'],False)
    def test_candidate_alert_requests_approval_and_deduplicates(self):
        with tempfile.TemporaryDirectory() as temp:
            root=pathlib.Path(temp);(root/'latest-capacity.json').write_text(json.dumps(self.summary(2,8)))
            with mock.patch.object(watch.subprocess,'run',return_value=subprocess.CompletedProcess([],0,b'checked')),mock.patch.object(watch,'notify') as notify:
                state=watch.check_once(pathlib.Path('config'),'DEFAULT',root)
                notify.assert_called_once();self.assertIn('need your approval',notify.call_args.args[0])
                self.assertIs(state['provisioning_performed'],False)
                watch.check_once(pathlib.Path('config'),'DEFAULT',root)
                notify.assert_called_once()

if __name__=='__main__':unittest.main()
