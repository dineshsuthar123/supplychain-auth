#!/usr/bin/env python3
import argparse, os, requests, sys
NAMES=["image_decode_duration","onnx_inference_duration","embedding_normalization_duration","verification_db_lookup_duration","verification_audit_write_duration","verification_total_duration","enrollment_total_duration","dashboard_query_duration","verification_result_count","blockchain_outbox_pending_count"]
def main():
 p=argparse.ArgumentParser();p.add_argument("--base-url",default=os.getenv("SUPPLYPRINT_BASE_URL","http://localhost:10000"));p.add_argument("--token",default=os.getenv("METRICS_BEARER_TOKEN"));args=p.parse_args();h={"Authorization":f"Bearer {args.token}"} if args.token else {};r=requests.get(f"{args.base_url}/actuator/prometheus",headers=h,timeout=30)
 if r.status_code!=200: sys.exit(f"Prometheus request failed HTTP {r.status_code}; provide an admin METRICS_BEARER_TOKEN.")
 missing=[name for name in NAMES if name not in r.text];print("metrics present:",", ".join(set(NAMES)-set(missing)));print("metrics missing:",", ".join(missing) or "none");sys.exit(1 if missing else 0)
if __name__=="__main__":main()
