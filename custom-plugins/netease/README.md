# QPlayer 网易云音源插件

<p><b>简体中文</b> · <a href="README.en.md">English</a></p>

QPlayer 的独立音源插件，由用户自行安装。它实现公开的 QPlayer JavaScript 插件 ABI，
把网易云相关的接口地址、请求变换、易盾浏览器探针、登录处理与凭据全部留在 QPlayer
核心之外；主程序只提供受 `webAuth` 权限约束的通用系统 WebView 执行环境。

本项目与网易云音乐没有隶属、合作或背书关系，不分发音频、账号凭据或受版权保护的媒体
内容。使用者需自行遵守服务条款与当地法律。

## 功能

通过 QPlayer 基于能力声明的 ABI 提供：

- 搜索（歌曲、专辑、歌手、歌单）与热搜关键词；歌单搜索结果提供封面，并可打开详情播放
- 首页推荐：每日推荐歌曲、推荐歌单，以及按官方分组返回的歌单分区（雷达歌单、
  专属场景歌单等）
- 歌曲、歌单、专辑、歌手详情
- 播放地址解析与歌词（含 AMLL 提供的 TTML 逐字歌词）
- 登录、账号信息、最近播放、红心与歌单增删改、更换歌单封面（非方形图片居中裁切）、听歌打卡、心动模式、分享

歌词并行请求 AMLL TTML 与网易云 YRC/LRC，等待两个来源完成后，向宿主提供质量更高的一组歌词：

- 排除空内容、无有效时间信息和结构损坏的 TTML；正文量不足最长有效候选 80% 的片段不参与优选。
- 先比较逐字时序覆盖率，再比较背景声/对唱信息、翻译覆盖率、音译覆盖率和正文量，不按文件格式固定排名。
- 同等质量保留 AMLL；所选来源的原文与附加信息整组返回，不跨来源拼接时间轴。
- 单个来源失败仍使用另一个来源；两个来源都请求失败时向宿主报告错误。

AMLL 旧站已重定向到 `api.beta.amllhub.bikonoo.com`，插件从 0.5.7 起精确声明该域名，
避免重定向被宿主的逐跳域名检查拒绝。无需修改 QPlayer 或放宽网络权限检查。

两个插件自有的入口，由插件声明、QPlayer 渲染：

- **一起听**（播放页入口）——房间协议、同步策略、主控权与提示全部实现在本插件内，
  QPlayer 只提供通用的播放、队列、剪贴板与提示服务。
- **音源解锁**（设置页开关）——官方无版权或仅试听时换源。右键「缓存歌曲」走同一条解锁直链，灰歌/试听也能下完整音轨。

登录凭据只通过 QPlayer 按插件隔离的加密凭据库保存。

完整的 ABI 与包格式见
[QPlayer 插件模板](https://github.com/TIMER-err/qplayer-plugin-template/blob/main/docs/ABI.md)。

插件 0.5.8 起需要 QPlayer 1.9.7 或更新版本，插件 ABI 仍为 `1.0`。
旧版宿主不识别新增的 `searchPlaylists` 能力，请先更新 QPlayer 再安装插件。

## 构建

生成未签名的开发包：

```bash
chmod +x scripts/package.sh
./scripts/package.sh
python3 scripts/verify-package.py dist/*.qplug
```

本地验证歌词优选规则：

```bash
bun test scripts/lyrics.test.js
```

手动导入未受信任的包时，QPlayer 会显示代码执行警告。

正式版本使用发布者的 P-256 私钥签名，私钥保存在仓库之外，通常位于发布工作流的
Secret 中：

```bash
QPLAYER_PLUGIN_SIGNING_KEY=/secure/path/publisher-private.pem ./scripts/package.sh
```

`publisher-key.pub` 是对应的公钥。QPlayer 将其固定在程序内，因此不能更换：更换发布者
密钥会使所有已发布版本的 QPlayer 无法安装本插件。发布工作流会拒绝使用与该文件不匹配
的私钥签名。

## 发布

推送与 `plugin.json` 版本一致的 `v<version>` tag 即触发发布工作流，自动签名、校验并
创建 GitHub Release。QPlayer 从本仓库的 latest release 读取可安装版本，因此 Release
中必须恰好挂载一个 `.qplug`，且不能是草稿或预发布。

本插件独立分发，不由 QPlayer 捆绑或托管。网易云音乐为其权利人的商标。
