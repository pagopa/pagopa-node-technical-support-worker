import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "nodoTechnicalSupport_openapi.sh"


class ExtractOpenApiTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.source = Path(self.temp.name) / "source.json"
        self.output = Path(self.temp.name) / "output.json"
        self.document = {
            "openapi": "3.0.3",
            "info": {"title": "worker", "version": "1"},
            "tags": [{"name": "Observability"}, {"name": "Info"}],
            "paths": {
                "/mixed": {
                    "parameters": [{"$ref": "#/components/parameters/Id"}],
                    "get": {
                        "tags": ["Info"],
                        "security": [{"Bearer": []}],
                        "responses": {"200": {"$ref": "#/components/responses/Ok"}},
                    },
                    "post": {"tags": ["Observability", "Info"]},
                },
                "/private": {"get": {"tags": ["Observability Resource"]}},
                "/untagged": {"get": {"responses": {}}},
            },
            "components": {
                "parameters": {"Id": {"name": "id", "in": "query"}},
                "responses": {"Ok": {"description": "OK", "content": {
                    "application/json": {"schema": {"$ref": "#/components/schemas/A"}}
                }}},
                "schemas": {
                    "A": {"properties": {"b": {"$ref": "#/components/schemas/B"}}},
                    "B": {"properties": {"a": {"$ref": "#/components/schemas/A"}}},
                    "Unused": {"type": "string"},
                },
                "securitySchemes": {
                    "Bearer": {"type": "http", "scheme": "bearer"},
                    "Unused": {"type": "http", "scheme": "basic"},
                },
            },
        }

    def run_script(self, source=None, output=None):
        return subprocess.run(
            ["bash", str(SCRIPT), str(source or self.source), str(output or self.output)],
            text=True, capture_output=True,
        )

    def write_source(self):
        self.source.write_text(json.dumps(self.document))

    def test_filters_operations_and_retains_reachable_components(self):
        self.write_source()
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        actual = json.loads(self.output.read_text())
        self.assertEqual(set(actual["paths"]), {"/mixed", "/untagged"})
        self.assertEqual(set(actual["paths"]["/mixed"]), {"parameters", "get"})
        self.assertEqual(actual["tags"], [{"name": "Info"}])
        self.assertEqual(set(actual["components"]["schemas"]), {"A", "B"})
        self.assertEqual(set(actual["components"]["securitySchemes"]), {"Bearer"})
        self.assertEqual(set(actual["components"]["responses"]), {"Ok"})
        self.assertEqual(set(actual["components"]["parameters"]), {"Id"})
        self.assertEqual(actual["info"], self.document["info"])

    def test_missing_component_fails_without_replacing_output(self):
        self.document["paths"]["/mixed"]["get"]["responses"]["200"]["$ref"] = (
            "#/components/responses/Missing"
        )
        self.write_source()
        self.output.write_text("unchanged")
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Missing", result.stderr)
        self.assertEqual(self.output.read_text(), "unchanged")

    def test_rejects_output_aliasing_source(self):
        self.write_source()
        original = self.source.read_text()
        result = self.run_script(output=self.source)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.source.read_text(), original)

    def test_global_security_keeps_its_scheme(self):
        self.document["security"] = [{"Unused": []}]
        self.write_source()
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        actual = json.loads(self.output.read_text())
        self.assertEqual(set(actual["components"]["securitySchemes"]), {"Bearer", "Unused"})

    def test_worker_document_keeps_exactly_the_non_observability_operations(self):
        source = ROOT / "openapi" / "openapi.json"
        result = self.run_script(source=source)
        self.assertEqual(result.returncode, 0, result.stderr)
        original = json.loads(source.read_text())
        actual = json.loads(self.output.read_text())
        methods = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}

        def operations(document):
            return {
                (path, method): operation
                for path, item in document["paths"].items()
                for method, operation in item.items()
                if method in methods
            }

        expected = {
            key: operation for key, operation in operations(original).items()
            if not {"Observability", "Observability Resource"}.intersection(operation.get("tags", []))
        }
        self.assertEqual(operations(actual), expected)


if __name__ == "__main__":
    unittest.main()
