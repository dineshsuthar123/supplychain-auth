#!/usr/bin/env python3
"""Smoke-test real image API flow; never inserts database fingerprints directly."""
import argparse, json, os, pathlib, time, uuid, requests, sys
from datetime import datetime, timezone

VALID={".jpg", ".jpeg", ".png"}
def first(folder):
    images=[p for p in pathlib.Path(folder).glob("*") if p.suffix.lower() in VALID]
    if not images: raise RuntimeError(f"No supported images in {folder}")
    return images[0]
def call_upload(base, token, path, product_id, endpoint):
    started=time.perf_counter()
    with path.open("rb") as image:
        response=requests.post(f"{base}{endpoint}", headers={"Authorization":f"Bearer {token}"}, data={"productId":product_id}, files={"image":(path.name,image,"image/png" if path.suffix.lower()=='.png' else "image/jpeg")}, timeout=90)
    return response, round((time.perf_counter()-started)*1000, 2)
def main():
    p=argparse.ArgumentParser(); p.add_argument("--dataset",default="datasets/eval/products"); p.add_argument("--base-url",default=os.getenv("SUPPLYPRINT_BASE_URL","http://localhost:10000")); p.add_argument("--out-json",default="reports/smoke/latest.json"); p.add_argument("--out-md",default="reports/smoke/latest.md"); args=p.parse_args()
    root=pathlib.Path(args.dataset); candidates=[d for d in root.iterdir() if d.is_dir()] if root.exists() else []
    def image_list(folder): return sorted(x for x in pathlib.Path(folder).glob('*') if x.suffix.lower() in VALID)
    def captures(product):
        genuine_root = product / "genuine"
        if (product / "reference").exists():
            references, genuine = image_list(product / "reference"), image_list(genuine_root)
        else:
            separated = (genuine_root / "reference").exists() or (genuine_root / "verify").exists()
            references = image_list(genuine_root / "reference") if separated else image_list(genuine_root)[:1]
            genuine = image_list(genuine_root / "verify") if separated else image_list(genuine_root)[1:]
        return references, genuine, image_list(product / "mismatch")
    product=next((d for d in candidates if len(captures(d)[0]) >= 1 and len(captures(d)[1]) >= 1 and len(captures(d)[2]) >= 1),None)
    if not product: sys.exit("Dataset needs one product with reference, held-out genuine, and mismatch JPEG/PNG captures. Run scripts/validate_dataset.py first.")
    references, genuine, mismatches = captures(product); mismatch=mismatches[0]
    suffix=uuid.uuid4().hex[:10]; credentials={"email":f"smoke-{suffix}@example.invalid","username":f"smoke{suffix}","password":"SmokePass123!","role":"MANUFACTURER"}
    created=requests.post(f"{args.base_url}/auth/register",json=credentials,timeout=30); created.raise_for_status(); body=created.json(); token=body["accessToken"]
    headers={"Authorization":f"Bearer {token}"}; before=requests.get(f"{args.base_url}/api/dashboard",headers=headers,timeout=30); before.raise_for_status(); before=before.json()
    product_id=f"SMOKE-{suffix}"; enrolled,enroll_ms=call_upload(args.base_url,token,references[0],product_id,"/api/enroll/image")
    genuine_response,genuine_ms=call_upload(args.base_url,token,genuine[0],product_id,"/api/verify/image")
    mismatch_response,mismatch_ms=call_upload(args.base_url,token,mismatch,product_id,"/api/verify/image")
    evidence=requests.get(f"{args.base_url}/api/verify/{product_id}/log",headers=headers,timeout=30)
    after=requests.get(f"{args.base_url}/api/dashboard",headers=headers,timeout=30); after.raise_for_status(); after=after.json()
    report={"generated_at":datetime.now(timezone.utc).isoformat(),"dataset_product":product.name,"product_id":product_id,"status_codes":{"enroll":enrolled.status_code,"genuine_verify":genuine_response.status_code,"mismatch_verify":mismatch_response.status_code,"evidence":evidence.status_code},"latency_ms":{"enroll":enroll_ms,"genuine_verify":genuine_ms,"mismatch_verify":mismatch_ms},"verification_events_before":before.get("verificationsToday"),"verification_events_after":after.get("verificationsToday"),"verification_events_delta":after.get("verificationsToday",0)-before.get("verificationsToday",0),"genuine_response":genuine_response.json() if genuine_response.headers.get("content-type","").startswith("application/json") else genuine_response.text[:200],"mismatch_response":mismatch_response.json() if mismatch_response.headers.get("content-type","").startswith("application/json") else mismatch_response.text[:200],"classification":"prototype smoke test; verification decisions are not model-accuracy evidence"}
    for path in (pathlib.Path(args.out_json),pathlib.Path(args.out_md)): path.parent.mkdir(parents=True,exist_ok=True)
    pathlib.Path(args.out_json).write_text(json.dumps(report,indent=2)); pathlib.Path(args.out_md).write_text("# Real image smoke test\n\n```json\n"+json.dumps(report,indent=2)+"\n```\n")
    print(json.dumps(report,indent=2))
    if not(enrolled.status_code==202 and genuine_response.status_code==200 and mismatch_response.status_code==200 and evidence.status_code==200 and report["verification_events_delta"]>=2): sys.exit(1)
if __name__=="__main__": main()
