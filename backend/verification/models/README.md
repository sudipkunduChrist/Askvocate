The bundled face models are copied from [OpenCV Zoo](https://github.com/opencv/opencv_zoo):

- `face_detection_yunet_2023mar.onnx` — [YuNet source](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet), MIT license in `YUNET_LICENSE.txt`.
- `face_recognition_sface_2021dec.onnx` — [SFace source](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface), Apache 2.0 license in `SFACE_LICENSE.txt`.

The SFace cosine baseline is 0.363 in OpenCV Zoo's example. It is a starting point,
not a calibrated threshold for low-resolution Aadhaar QR photos. Before production use,
validate false-match and false-reject rates on consented representative samples.

`minifasnet_v2.onnx` is the MiniFASNetV2 presentation-attack detector exported to ONNX
from [Silent-Face-Anti-Spoofing](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing)
(`resources/anti_spoof_models/2.7_80x80_MiniFASNetV2.pth`). Apache 2.0 license in
`MINIFASNET_LICENSE.txt`. It is run with OpenCV DNN (no PyTorch at verification time).
Class 1 is the live class; `PAD_THRESHOLD` in `verification.py` is a starting point and
should be calibrated on real live/spoof samples before production use.
