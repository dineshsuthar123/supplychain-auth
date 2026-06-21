import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("evaluate_model", pathlib.Path(__file__).with_name("evaluate_model.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class EvaluationMetricsTest(unittest.TestCase):
    def test_known_scores_produce_expected_threshold_metrics(self):
        best, sweep = module.metrics([1, 1, 0, 0], [0.95, 0.85, 0.20, 0.10])
        self.assertEqual(best["far"], 0)
        self.assertEqual(best["frr"], 0)
        self.assertIn(best, sweep)
        self.assertEqual(module.auc([1, 1, 0, 0], [0.95, 0.85, 0.20, 0.10]), 1.0)

if __name__ == "__main__": unittest.main()
