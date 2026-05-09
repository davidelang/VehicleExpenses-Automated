import paddle
import numpy as np
import os

def convert_to_monochrome(model_dir, output_dir):
    paddle.enable_static()
    exe = paddle.static.Executor(paddle.CPUPlace())
    
    # Load model
    [prog, feed_names, fetch_targets] = paddle.static.load_inference_model(model_dir, exe)
    block = prog.block(0)
    
    # 1. Update Input Variable
    input_vars = [block.var(name) for name in feed_names]
    fetch_vars = [block.var(name) for name in [t.name if not isinstance(t, str) else t for t in fetch_targets]]
    
    input_name = feed_names[0]
    input_var = input_vars[0]
    print(f"Modifying input '{input_name}': {input_var.shape} -> [None, 1, None, None]")
    input_var.desc.set_shape([-1, 1, -1, -1])
    
    # 2. Find and modify first Conv2D
    for op in block.ops:
        if op.type == 'conv2d':
            weight_name = op.input('Filter')[0]
            weight_var = block.var(weight_name)
            
            # Get current weights from scope
            weights = np.array(paddle.static.global_scope().find_var(weight_name).get_tensor())
            print(f"Modifying weight '{weight_name}': {weights.shape} -> ({weights.shape[0]}, 1, {weights.shape[2]}, {weights.shape[3]})")
            
            # Average weights across the 3 input channels (axis 1)
            new_weights = np.mean(weights, axis=1, keepdims=True)
            
            # Set new weights back
            paddle.static.global_scope().find_var(weight_name).get_tensor().set(new_weights, paddle.CPUPlace())
            
            # Update variable description in program
            weight_var.desc.set_shape(new_weights.shape)
            break
            
    # 3. Save modified model
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
    
    paddle.static.save_inference_model(
        os.path.join(output_dir, "inference"),
        input_vars,
        fetch_vars,
        exe,
        program=prog
    )
    print(f"Monochrome model saved to: {output_dir}")

# Models to convert
MODELS = {
    "det": "models/det/inference",
    "rec_v3": "models/rec_v3/inference",
    "rec_numeric": "models/en_number/en_number_mobile_v2.0_rec_infer/inference"
}

for key, path in MODELS.items():
    print(f"\n--- Converting {key} ---")
    convert_to_monochrome(path, f"models/{key}_mono")
