# QPlayer Soda Music source plugin

<p><a href="README.md">简体中文</a> · <b>English</b></p>

Independent QPlayer source plugin (ABI 1.0). Protocol mapping follows
[go-music-dl](https://github.com/guohuiyuan/go-music-dl) / `music-lib`.
Not affiliated with Soda Music / Douyin.

`resolveStream` only returns **plaintext** URLs. Encrypted streams that require
`play_auth` AES-CTR decryption cannot be fed to the host as raw bytes.

## Build

```bash
./scripts/package.sh
python3 scripts/verify-package.py dist/*.qplug
```
