# QPlayer 汽水音乐音源插件

<p><b>简体中文</b> · <a href="README.en.md">English</a></p>

QPlayer 的独立音源插件，实现公开的 JavaScript 插件 ABI（apiVersion 1.0）。
解析逻辑参考 [go-music-dl](https://github.com/guohuiyuan/go-music-dl) / `music-lib` 的 soda 实现。

本项目与汽水音乐 / 抖音没有隶属或合作关系，不分发音频或账号凭据。使用者需自行遵守服务条款与当地法律。

## 功能

| 能力 | 说明 |
|---|---|
| `searchSongs` / `searchAlbums` | Android 搜索接口 |
| `songDetails` | SEO / web track_v2 |
| `playlistDetails` / `albumDetails` | 歌单分页、分享页专辑 |
| `home` | 以「流行」搜索推荐歌单 |
| `userPlaylists` | 登录后的个人歌单 |
| `resolveStream` | 仅返回**明文**播放地址 |
| `lyrics` | 将汽水逐字歌词转为 LRC |
| `login` / `account` | 扫码；若要短信验证，切到 Cookie 栏只填验证码 |

汽水部分音轨带 `play_auth`，需要本地 AES-CTR 解密后才能播放。
QPlayer 插件只能向宿主返回 URL，无法注入解密后的字节，因此加密流会提示无法播放。
免费曲目的 SEO `url_player_info` 通常是明文 m4a。

## 构建

```bash
./scripts/package.sh
python3 scripts/verify-package.py dist/*.qplug
```

完整 ABI 见 [插件模板](https://github.com/TIMER-err/qplayer-plugin-template/blob/main/docs/ABI.md)。
