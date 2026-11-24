#!/usr/bin/env python3
"""Extract the technical support operations from the worker OpenAPI document."""

import argparse
import json
import os
import sys
import tempfile
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parent.parent
METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}
EXCLUDED_TAGS = {"Observability", "Observability Resource"}


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def extract(document):
    if not isinstance(document, dict) or not isinstance(document.get("paths"), dict):
        raise ValueError("the source must contain a paths object")
    components = document.get("components", {})
    if not isinstance(components, dict) or any(
        not isinstance(category, dict) for category in components.values()
    ):
        raise ValueError("components and their categories must be objects")

    paths = {}
    for path, item in document["paths"].items():
        if not isinstance(item, dict):
            raise ValueError(f"path {path} must be an object")
        kept = {}
        for key, value in item.items():
            if key.lower() in METHODS:
                if not isinstance(value, dict) or not isinstance(value.get("tags", []), list):
                    raise ValueError(f"invalid operation {key} {path}")
                if not EXCLUDED_TAGS.intersection(value.get("tags", [])):
                    kept[key] = value
            else:
                kept[key] = value
        if any(key.lower() in METHODS for key in kept):
            paths[path] = kept

    result = dict(document)
    result["paths"] = paths
    if "tags" in result:
        result["tags"] = [tag for tag in result["tags"] if tag.get("name") not in EXCLUDED_TAGS]

    # Scan retained metadata and operations, then follow component references to a fixpoint.
    result.pop("components", None)
    pending = [result]
    used = set()
    visited_refs = set()
    while pending:
        for obj in walk(pending.pop()):
            refs = []
            if isinstance(obj.get("$ref"), str):
                refs.append(obj["$ref"])
            if isinstance(obj.get("security"), list):
                for requirement in obj["security"]:
                    if not isinstance(requirement, dict):
                        raise ValueError("security requirements must be objects")
                    for name in requirement:
                        escaped = name.replace("~", "~0").replace("/", "~1")
                        refs.append(f"#/components/securitySchemes/{escaped}")
            for ref in refs:
                if not ref.startswith("#/") or ref in visited_refs:
                    continue
                visited_refs.add(ref)
                parts = [part.replace("~1", "/").replace("~0", "~")
                         for part in unquote(ref[2:]).split("/")]
                target = document if parts[0] == "components" else result
                try:
                    for part in parts:
                        target = target[part] if isinstance(target, dict) else target[int(part)]
                except (KeyError, IndexError, ValueError, TypeError):
                    raise ValueError(f"unresolved local reference: {ref}") from None
                if len(parts) >= 3 and parts[0] == "components":
                    component = (parts[1], parts[2])
                    if component not in used:
                        used.add(component)
                        pending.append(components[parts[1]][parts[2]])
                else:
                    pending.append(target)

    if "components" in document:
        result["components"] = {
            category: {name: value for name, value in entries.items() if (category, name) in used}
            for category, entries in components.items()
        }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", type=Path, default=ROOT / "openapi/openapi.json")
    parser.add_argument("output", nargs="?", type=Path,
                        default=ROOT / "openapi/nodoTechnicalSupport.openapi.json")
    args = parser.parse_args()
    try:
        if args.source.resolve() == args.output.resolve() or (
            args.output.exists() and os.path.samefile(args.source, args.output)
        ):
            raise ValueError("output aliases the input source")
        with args.source.open(encoding="utf-8") as source:
            result = extract(json.load(source))
        temp_path = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w", encoding="utf-8", dir=args.output.parent,
                prefix=args.output.name + ".tmp.", delete=False
            ) as output:
                temp_path = Path(output.name)
                json.dump(result, output, indent=2, ensure_ascii=False)
                output.write("\n")
            os.chmod(temp_path, 0o644)
            os.replace(temp_path, args.output)
        finally:
            if temp_path is not None and temp_path.exists():
                temp_path.unlink()
    except (OSError, ValueError, RecursionError) as error:
        print(f"nodoTechnicalSupport_openapi: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
