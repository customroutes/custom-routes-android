# Third-Party Notices

Custom Routes includes ONNX Runtime and can optionally download EfficientSAM-Ti
model files. The corresponding license and notice texts are packaged in the app
and are always available from **Privacy & Data > AI & runtime notices**. These
app-specific notices supplement license metadata supplied by other Maven
dependencies and preserved by Android packaging.

## ONNX Runtime Android 1.29.0

- Project: <https://github.com/microsoft/onnxruntime>
- Release: <https://github.com/microsoft/onnxruntime/releases/tag/v1.29.0>
- License: MIT
- Packaged license: `app/src/main/res/raw/onnxruntime_license.txt`
- Complete version-matched notices:
  `app/src/main/res/raw/onnxruntime_third_party_notices.txt`

The packaged texts are exact copies from Microsoft's immutable `v1.29.0` tag.

## EfficientSAM-Ti

- Source: <https://github.com/yformer/EfficientSAM>
- Official ONNX Space:
  <https://huggingface.co/spaces/yunyangx/EfficientSAM>
- License: Apache License 2.0, as declared by the Space repository
- Packaged license: `app/src/main/res/raw/efficientsam_license.txt`
- Packaged model details:
  `app/src/main/res/raw/efficientsam_model_details.txt`

The optional downloads are pinned to these files:

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `efficientsam_ti_encoder.onnx` | 24,799,761 bytes | `84ed466ffcc5c1f8d08409bc34a23bb364ab2c15e402cb12d4335a42be0e0951` |
| `efficientsam_ti_decoder.onnx` | 16,565,728 bytes | `a62f8fa5ea080447c0689418d69e58f1e83e0b7adf9c142e2bd9bcc8045c0b11` |

The model artifacts, license evidence, provenance links, and training-provenance
qualification are recorded in the packaged model-details document.
