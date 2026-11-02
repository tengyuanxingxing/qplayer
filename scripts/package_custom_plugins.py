"""Build local .qplug archives, optionally signed by this fork's publisher key."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import zipfile
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--signing-key", type=Path, help="External PEM EC publisher key; never store it in the repository")
parser.add_argument("--openssl", default="openssl", help="OpenSSL executable")
args = parser.parse_args()
if args.signing_key and not args.signing_key.is_file():
    parser.error("signing key does not exist")

root = Path(__file__).resolve().parents[1]
output = root / "deliverables"
output.mkdir(exist_ok=True)
for name in ("netease", "qqmusic", "soda"):
    source = root / "custom-plugins" / name
    manifest = json.loads((source / "plugin.json").read_text(encoding="utf-8"))
    files = {"plugin.json": (source / "plugin.json").read_bytes()}
    for notice in ("LICENSE", "LICENSE.md", "LICENSE.txt", "NOTICE", "COPYING"):
        if (source / notice).is_file():
            files[notice] = (source / notice).read_bytes()
    for folder in ("src", "ui", "assets"):
        for path in sorted((source / folder).rglob("*")):
            if path.is_file():
                files[path.relative_to(source).as_posix()] = path.read_bytes()
    hashes = {path: hashlib.sha256(data).hexdigest() for path, data in sorted(files.items())}
    files["META-INF/qplayer-files.json"] = (json.dumps(hashes, ensure_ascii=False, indent=2) + "\n").encode()
    if args.signing_key:
        signed = subprocess.run([args.openssl, "dgst", "-sha256", "-sign", str(args.signing_key)],
                                input=files["META-INF/qplayer-files.json"], capture_output=True, check=True)
        files["META-INF/qplayer.sig"] = base64.b64encode(signed.stdout)
    archive = output / f'{manifest["id"]}-{manifest["version"]}-custom.qplug'
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as package:
        for path, data in sorted(files.items()):
            package.writestr(path, data)
    print(archive)
