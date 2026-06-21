#!/usr/bin/env python3
"""Evaluate ONNX embeddings against a labeled physical-capture dataset.

Dataset: datasets/eval/products/<product_id>/{reference,genuine,mismatch}.
The legacy genuine/{reference,verify} layout remains supported. Mismatch images
are evaluated against that product's separate reference set.
"""
import argparse, json, math, pathlib, sys
from datetime import datetime, timezone

def dependencies():
    try:
        import numpy, onnxruntime
        from PIL import Image
        return numpy, onnxruntime, Image
    except ImportError as e: sys.exit(f"Missing evaluation dependency: {e}. Install numpy, onnxruntime, and Pillow.")

def auc(labels, scores):
    ordered = sorted(zip(scores, labels), key=lambda x: x[0]); rank_sum = sum(i + 1 for i, (_, label) in enumerate(ordered) if label == 1); pos=sum(labels); neg=len(labels)-pos
    return None if not pos or not neg else (rank_sum - pos*(pos+1)/2)/(pos*neg)

def metrics(labels, scores):
    candidates = sorted(set(scores)); best = None; points=[]
    for threshold in candidates:
        tp=sum(1 for y,s in zip(labels,scores) if y and s>=threshold); fn=sum(1 for y,s in zip(labels,scores) if y and s<threshold)
        fp=sum(1 for y,s in zip(labels,scores) if not y and s>=threshold); tn=sum(1 for y,s in zip(labels,scores) if not y and s<threshold)
        far=fp/(fp+tn) if fp+tn else 0; frr=fn/(fn+tp) if fn+tp else 0; point={"threshold":threshold,"far":far,"frr":frr}
        points.append(point)
        if best is None or abs(far-frr)<abs(best["far"]-best["frr"]): best=point
    return best, points

def main():
    p=argparse.ArgumentParser(); p.add_argument("--dataset", default="datasets/eval/products"); p.add_argument("--model", default="backend/product-service/src/main/resources/models/fingerprint.onnx"); p.add_argument("--out-json", default="reports/model-evaluation/latest.json"); p.add_argument("--out-md", default="reports/model-evaluation/latest.md"); args=p.parse_args()
    root=pathlib.Path(args.dataset); model=pathlib.Path(args.model)
    report={"generated_at":datetime.now(timezone.utc).isoformat(),"dataset":str(root),"model":str(model),"warning":None}
    products=[] if not root.exists() else [x for x in root.iterdir() if x.is_dir()]
    valid={".jpg",".jpeg",".png"}
    def images(folder): return [p for p in pathlib.Path(folder).glob('*') if p.suffix.lower() in valid]
    references, genuine, mismatch = {}, {}, {}
    for product in products:
        root = product / "genuine"
        if (product / "reference").exists():
            reference_files, genuine_files = images(product / "reference"), images(root)
        else:
            separated = (root / "reference").exists() or (root / "verify").exists()
            reference_files = images(root / "reference") if separated else images(root)[:1]
            genuine_files = images(root / "verify") if separated else images(root)[1:]
        references[product.name], genuine[product.name], mismatch[product.name] = reference_files, genuine_files, images(product / "mismatch")
    if len(products)<2 or sum(map(len,references.values()))<2 or sum(map(len,genuine.values()))<4 or sum(map(len,mismatch.values()))<2:
        report.update({"warning":"Insufficient labeled real-capture data. No field-accuracy claim is valid.","products":len(products),"reference_images":sum(map(len,references.values())),"genuine_images":sum(map(len,genuine.values())),"mismatch_images":sum(map(len,mismatch.values()))})
    else:
        np, ort, Image=dependencies()
        session=ort.InferenceSession(str(model),providers=["CPUExecutionProvider"]); input_name=session.get_inputs()[0].name
        def embed(path):
            image=Image.open(path).convert("L").resize((256,256),Image.Resampling.BILINEAR); value=np.asarray(image,dtype=np.float32)/255.0; out=session.run(None,{input_name:value[None,None,:,:]})[0][0]; return out/np.linalg.norm(out)
        labels=[]; scores=[]; gs=[]; ms=[]
        for product, files in genuine.items():
            if not references[product] or not files: continue
            reference_embeddings=[embed(path) for path in references[product]]
            ref=np.mean(reference_embeddings, axis=0); ref=ref/np.linalg.norm(ref)
            for path in files:
                score=float(np.dot(ref,embed(path))); labels.append(1);scores.append(score);gs.append(score)
            for path in mismatch.get(product,[]):
                score=float(np.dot(ref,embed(path))); labels.append(0);scores.append(score);ms.append(score)
        best, points=metrics(labels,scores); report.update({"products":len(products),"genuine_pairs":len(gs),"mismatch_pairs":len(ms),"roc_auc":auc(labels,scores),"recommended_threshold":best["threshold"],"far":best["far"],"frr":best["frr"],"eer":(best["far"]+best["frr"])/2,"threshold_sweep":points,"genuine_similarity":{"min":min(gs),"max":max(gs),"mean":sum(gs)/len(gs)},"mismatch_similarity":{"min":min(ms),"max":max(ms),"mean":sum(ms)/len(ms)},"warning":"Prototype evaluation only unless dataset provenance, size, and capture protocol are independently documented."})
    for path in [pathlib.Path(args.out_json), pathlib.Path(args.out_md)]: path.parent.mkdir(parents=True,exist_ok=True)
    pathlib.Path(args.out_json).write_text(json.dumps(report,indent=2))
    lines=["# SupplyPrint model evaluation","",f"Generated: {report['generated_at']}","",f"Dataset: `{report['dataset']}`",""]
    if report.get("warning"): lines += [f"> Warning: {report['warning']}",""]
    lines += ["| Metric | Value |","|---|---:|"] + [f"| {k} | {v} |" for k,v in report.items() if k in {"products","genuine_pairs","mismatch_pairs","roc_auc","recommended_threshold","far","frr","eer"}]
    pathlib.Path(args.out_md).write_text("\n".join(lines)+"\n")
    print(json.dumps(report,indent=2))
if __name__ == "__main__": main()
