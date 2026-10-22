"""Require a new numeric Frontier version before creating a release."""
import json
import re
import sys


def require_new_version(version, pages):
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", version):
        raise ValueError("Frontier releases require a plain major.minor.patch version; changing a dev suffix is not a bump")
    current = tuple(map(int, version.split(".")))
    for page in pages:
        for release in page:
            tag = release.get("tag_name", "")
            if release.get("draft") or not tag.startswith("frontier-v"):
                continue
            # Existing historical -dev releases still reserve their numeric version.
            match = re.fullmatch(r"frontier-v([0-9]+)\.([0-9]+)\.([0-9]+)(?:-[a-zA-Z0-9.-]+)?", tag)
            if not match:
                raise ValueError("Cannot establish version order for published tag " + tag)
            if current <= tuple(map(int, match.groups())):
                raise ValueError(version + " must be numerically newer than published " + tag)


if __name__ == "__main__":
    try:
        with open(sys.argv[2], encoding="utf-8") as source:
            require_new_version(sys.argv[1], json.load(source))
    except ValueError as error:
        sys.exit(str(error))
    print("PASS: Frontier " + sys.argv[1] + " increases the published numeric version")
