import unittest

from fastapi.testclient import TestClient
from main import app


class BaseAPITest(unittest.TestCase):
    """API 테스트에서 공통으로 사용하는 TestClient 기반 클래스."""

    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(app)
