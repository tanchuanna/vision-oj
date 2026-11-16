import unittest
from PIL import Image

from app.main import assess_quality


class ImageQualityTest(unittest.TestCase):
    def test_dark_image_is_advisory(self):
        result = assess_quality(Image.new("RGB", (512, 512), "black"))
        self.assertEqual("opencv-quality-v1", result["version"])
        self.assertIn("图片过暗", result["warnings"])

    def test_tiny_image_has_resolution_warning(self):
        result = assess_quality(Image.new("RGB", (64, 64), "gray"))
        self.assertIn("图片尺寸较小，识别结果可能不稳定", result["warnings"])


if __name__ == "__main__":
    unittest.main()
