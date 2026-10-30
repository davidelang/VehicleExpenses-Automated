#!/usr/bin/env python3
"""Convert a 3-channel PaddleOCR inference model into a 1-channel (greyscale) model.

The original model expects, per channel c:   x_c = (u/255 - mean_c) / std_c
for an RGB pixel. For a grey pixel (R = G = B = u) the first conv sees
    sum_c W[o,c] * x_c = sum_c W[o,c] * (a_c * x1 + b_c)
where the 1-channel model's input is   x1 = (u - mono_mean) / mono_std
(mono_mean / mono_std in 0..255 pixel units, i.e. what opt
--analytic_input_mean / --analytic_input_std get), and
    a_c = mono_std / (255 * std_c),   b_c = (mono_mean/255 - mean_c) / std_c.
So the exact 1-channel weight is   W1[o] = sum_c a_c * W[o,c]
(for rec models, mean = std = 0.5 and mono 127.5/127.5, a_c = 1: a plain SUM),
and the constant  d[o] = sum_c b_c * sum_k W[o,c,k]  is folded into the
first conv's bias (following batch_norm Mean, or elementwise_add bias).
d = 0 for rec (mono_mean = 127.5). For det (ImageNet means) d != 0; that is
exact everywhere except the first conv's zero-padded border taps, where a
1-channel model cannot reproduce three different per-channel pad values.

The old behaviour (np.mean over channels, no normalisation fold) is kept
behind --legacy-mean only to reproduce the pre-2026-10 models for A/B.

Writes <out_dir>/inference.{pdmodel,pdiparams} and <out_dir>/mono_norm.json
({"mean": mono_mean, "std": mono_std}); the opt scripts pass those values to
--analytic_input_mean / --analytic_input_std.

Needs the legacy (non-PIR) static program API: on Paddle >= 3.0 run with
FLAGS_enable_pir_api=0 (set below before importing paddle).
"""
import argparse
import json
import os
import sys

os.environ.setdefault("FLAGS_enable_pir_api", "0")
import numpy as np  # noqa: E402
import paddle  # noqa: E402

# Per-model preprocessing of the ORIGINAL 3-channel model (PaddleOCR configs,
# 0..1 units, RGB order) and the chosen 1-channel input normalisation (pixel units).
PRESETS = {
    # PP-OCR rec models: NormalizeImage / resize_norm_img -> (x/255 - 0.5) / 0.5
    "rec": dict(mean=(0.5, 0.5, 0.5), std=(0.5, 0.5, 0.5), mono_mean=127.5, mono_std=127.5),
    # PP-OCR det models: ImageNet NormalizeImage. The per-channel means/stds are
    # folded here (weights + bias); the 1-channel input is centred at 128 so the
    # opt pass adds no further offset. mono_std = 255*0.229 (any value is exact,
    # the weights absorb it).
    "det": dict(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225), mono_mean=128.0, mono_std=58.395),
}


def _tensor(scope, name):
    return np.array(scope.find_var(name).get_tensor())


def _set(scope, name, arr):
    scope.find_var(name).get_tensor().set(np.ascontiguousarray(arr), paddle.CPUPlace())


def convert_to_monochrome(model_dir, output_dir, preset, legacy_mean=False,
                          mono_mean=None, mono_std=None):
    paddle.enable_static()
    exe = paddle.static.Executor(paddle.CPUPlace())
    scope = paddle.static.global_scope()
    prefix = model_dir if model_dir.endswith("inference") else os.path.join(model_dir, "inference")
    prog, feed_names, fetch_targets = paddle.static.load_inference_model(prefix, exe)
    block = prog.block(0)

    input_vars = [block.var(n) for n in feed_names]
    fetch_vars = [block.var(t if isinstance(t, str) else t.name) for t in fetch_targets]
    in_var = input_vars[0]
    print(f"input '{in_var.name}': {in_var.shape} -> [-1, 1, -1, -1]")
    in_var.desc.set_shape([-1, 1, -1, -1])

    conv = next((op for op in block.ops
                 if op.type in ("conv2d", "depthwise_conv2d") and in_var.name in op.input("Input")), None)
    if conv is None:
        sys.exit("first conv does not read the feed directly; refusing to guess")
    wname = conv.input("Filter")[0]
    w = _tensor(scope, wname).astype(np.float64)
    if w.shape[1] != 3 or conv.attr("groups") != 1:
        sys.exit(f"unexpected first conv {wname} {w.shape} groups={conv.attr('groups')}")

    P = dict(PRESETS[preset])
    mean, std = np.asarray(P["mean"]), np.asarray(P["std"])
    if legacy_mean:
        w1 = w.mean(axis=1, keepdims=True)
        d = np.zeros(w.shape[0])
        mono_mean, mono_std = 128.0, 1.0 / 0.017124753445386887  # what the old opt guessed
        print("LEGACY: mean over channels, no normalisation fold (pre-2026-10 behaviour)")
    else:
        mono_std = float(mono_std if mono_std is not None else P["mono_std"])
        mono_mean = float(mono_mean if mono_mean is not None else P["mono_mean"])
        a = mono_std / (255.0 * std)                 # [C]
        b = (mono_mean / 255.0 - mean) / std         # [C]
        w1 = (w * a[None, :, None, None]).sum(axis=1, keepdims=True)
        d = (w.sum(axis=(2, 3)) * b[None, :]).sum(1)  # [O]
        print(f"preset={preset} mono_mean={mono_mean} mono_std={mono_std} a={np.round(a, 5)} "
              f"b={np.round(b, 5)} |d|max={np.abs(d).max():.4g}")

    print(f"weight '{wname}': {w.shape} -> {w1.shape}")
    _set(scope, wname, w1.astype(np.float32))
    block.var(wname).desc.set_shape(list(w1.shape))

    if np.abs(d).max() > 0:
        out = conv.output("Output")[0]
        nxt = [op for op in block.ops if out in op.input_arg_names]
        if len(nxt) != 1:
            sys.exit(f"conv output feeds {len(nxt)} ops; cannot fold offset")
        op = nxt[0]
        if op.type == "batch_norm":
            mname = op.input("Mean")[0]
            _set(scope, mname, (_tensor(scope, mname).astype(np.float64) - d).astype(np.float32))
            print(f"folded offset into batch_norm Mean '{mname}'")
        elif op.type == "elementwise_add" and block.var(op.input("Y")[0]).persistable:
            yname = op.input("Y")[0]
            y = _tensor(scope, yname).astype(np.float64)
            if y.size != d.size:
                sys.exit(f"bias '{yname}' shape {y.shape} does not match {d.shape}")
            _set(scope, yname, (y + d.reshape(y.shape)).astype(np.float32))
            print(f"folded offset into bias '{yname}'")
        else:
            sys.exit(f"cannot fold offset into {op.type}")

    os.makedirs(output_dir, exist_ok=True)
    paddle.static.save_inference_model(os.path.join(output_dir, "inference"), input_vars, fetch_vars,
                                       exe, program=prog)
    norm = {"mean": mono_mean, "std": mono_std, "preset": preset, "legacy_mean": bool(legacy_mean),
            "source": os.path.abspath(prefix)}
    with open(os.path.join(output_dir, "mono_norm.json"), "w") as f:
        json.dump(norm, f, indent=1)
    print(f"mono model saved to {output_dir}  (opt: --analytic_input_mean={mono_mean} "
          f"--analytic_input_std={mono_std})")
    return norm


# Batch mode (no args): same layout the opt scripts expect under MODEL_ROOT.
MODELS = {
    "det": ("models/det/inference", "det"),
    "rec_v3": ("models/rec_v3/inference", "rec"),
    "rec_numeric": ("models/en_number/en_number_mobile_v2.0_rec_infer/inference", "rec"),
}

if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model-dir")
    ap.add_argument("--out-dir")
    ap.add_argument("--preset", choices=sorted(PRESETS))
    ap.add_argument("--mono-mean", type=float)
    ap.add_argument("--mono-std", type=float)
    ap.add_argument("--legacy-mean", action="store_true",
                    help="reproduce the old np.mean conversion (A/B only; wrong scale)")
    ap.add_argument("--models-root", default=".", help="batch mode: directory containing models/")
    args = ap.parse_args()
    if args.model_dir:
        if not (args.out_dir and args.preset):
            ap.error("--model-dir needs --out-dir and --preset")
        convert_to_monochrome(args.model_dir, args.out_dir, args.preset, args.legacy_mean,
                              args.mono_mean, args.mono_std)
    else:
        for key, (path, preset) in MODELS.items():
            print(f"\n--- Converting {key} ({preset}) ---")
            convert_to_monochrome(os.path.join(args.models_root, path),
                                  os.path.join(args.models_root, f"models/{key}_mono"), preset,
                                  args.legacy_mean)
