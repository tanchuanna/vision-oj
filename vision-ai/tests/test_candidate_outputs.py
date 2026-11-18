import unittest
from PIL import Image
from unittest.mock import Mock, patch

from app.main import infer


class CandidateOutputTest(unittest.TestCase):
    def test_analysis_has_fixed_fields_and_deterministic_values(self):
        response = infer(
            Image.new("RGB", (4, 3), (100, 100, 100)),
            confidence=0.25,
            iou=0.7,
            task_type="IMAGE_ANALYSIS",
            model_key="OPENCV_ANALYSIS",
        )

        self.assertEqual("pillow-numpy", response.model)
        self.assertEqual(
            {"width", "height", "mode", "mean_gray", "edge_strength"},
            set(response.image_analysis or {}),
        )
        self.assertEqual(
            {
                "width": 4,
                "height": 3,
                "mode": "RGB",
                "mean_gray": 100.0,
                "edge_strength": 0.0,
            },
            response.image_analysis,
        )

    @patch("app.main.get_ocr_reader")
    def test_ocr_candidate_is_plain_text_in_reading_order(self, get_reader):
        reader = Mock()
        reader.readtext.return_value = ["Hello", "世界"]
        get_reader.return_value = reader

        response = infer(
            Image.new("RGB", (32, 16), "white"),
            confidence=0.25,
            iou=0.7,
            task_type="IMAGE_OCR",
            model_key="EASYOCR_ZH_EN",
        )

        self.assertEqual("easyocr-zh-en", response.model)
        self.assertEqual("Hello 世界", response.ocr_text)
        self.assertEqual({}, response.counts)
        self.assertEqual([], response.boxes)
        reader.readtext.assert_called_once()


if __name__ == "__main__":
    unittest.main()
