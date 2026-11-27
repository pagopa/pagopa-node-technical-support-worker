import json
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


POLICY = Path(__file__).resolve().parents[1] / "infra/policy/nodo_observability/api_group_base_policy.xml"


def render_policy(local_url):
    variables = {
        "frontendUrl": "https://frontend.example.com",
        "localUrl": local_url,
        "hostname": "backend.example.com",
        "entraTenantId": "tenant-id",
        "entraClientId": "client-id",
    }
    expression = f"jsonencode(templatefile({json.dumps(str(POLICY))}, {json.dumps(variables)}))"
    with tempfile.TemporaryDirectory() as directory:
        result = subprocess.run(
            ["terraform", f"-chdir={directory}", "console"],
            input=expression,
            text=True,
            capture_output=True,
            check=True,
        )
    return ET.fromstring(json.loads(json.loads(result.stdout)))


class ObservabilityPolicyTest(unittest.TestCase):
    def test_development_includes_localhost_origin(self):
        policy = render_policy("http://localhost:4201")
        origins = [origin.text for origin in policy.findall("./inbound/cors/allowed-origins/origin")]
        self.assertEqual(origins, ["https://frontend.example.com", "http://localhost:4201"])

    def test_other_environments_do_not_include_empty_origin(self):
        policy = render_policy("")
        origins = [origin.text for origin in policy.findall("./inbound/cors/allowed-origins/origin")]
        self.assertEqual(origins, ["https://frontend.example.com"])


if __name__ == "__main__":
    unittest.main()
