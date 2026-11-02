"""Build locally modified, unsigned .qplug archives with the host's hash manifest."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
output = root / "deliverables"
output.mkdir(exist_ok=True)
for name in ("netease", "qqmusic", "soda"):
    source = root / "custom-plugins" / name
    manifest = json.loads((source / "plugin.json").read_text(encoding="utf-8"))
    files = {"plugin.json": (source / "plugin.json").read_bytes()}
    for name in ("LICENSE", "LICENSE.md", "LICENSE.txt", "NOTICE", "COPYING"):
        if (source / name).is_file():
            files[name] = (source / name).read_bytes()
    for folder in ("src", "ui", "assets"):
        for path in sorted((source / folder).rglob("*")):
            if path.is_file():
                files[path.relative_to(source).as_posix()] = path.read_bytes()
    hashes = {path: hashlib.sha256(data).hexdigest() for path, data in sorted(files.items())}
    files["META-INF/qplayer-files.json"] = (json.dumps(hashes, ensure_ascii=False, indent=2) + "\n").encode()
    archive = output / f'{manifest["id"]}-{manifest["version"]}-custom.qplug'
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as package:
        for path, data in sorted(files.items()):
            package.writestr(path, data)
    print(archive)
