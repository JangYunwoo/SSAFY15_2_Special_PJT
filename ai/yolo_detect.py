"""YOLO로 이미지 속 객체를 검출하고 결과를 JSON으로 출력한다."""

import argparse
import json
from pathlib import Path

from ultralytics import YOLO


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="YOLO 이미지 객체 검출")
    parser.add_argument("image", type=Path, help="검출할 이미지 경로")
    parser.add_argument("--model", default="yolo26n.pt", help="사용할 YOLO 모델")
    parser.add_argument("--confidence", type=float, default=0.25, help="최소 신뢰도")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    image_path = args.image.expanduser().resolve()

    if not image_path.is_file():
        raise SystemExit(f"이미지 파일을 찾을 수 없습니다: {args.image}")

    model = YOLO(args.model)
    result = model.predict(
        source=str(image_path),
        conf=args.confidence,
        verbose=False,
    )[0]

    detections = []
    if result.boxes is not None:
        for box in result.boxes:
            class_id = int(box.cls.item())
            detections.append(
                {
                    "class_id": class_id,
                    "class_name": result.names[class_id],
                    "confidence": round(float(box.conf.item()), 4),
                    "box_xyxy": [round(value, 2) for value in box.xyxy[0].tolist()],
                }
            )

    print(
        json.dumps(
            {
                "image": str(image_path),
                "model": args.model,
                "detections": detections,
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
