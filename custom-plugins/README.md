# 本地评论、歌手与登录扩展

`netease` 基于 https://github.com/TIMER-err/qplayer-netease-plugin，
`qqmusic` 基于 https://github.com/TIMER-err/qplayer-qqmusic-plugin。
`soda` 基于 https://github.com/MiaM1ku/qplayer-soda-plugin。
保留各项目许可证，本地增加只读 `comments` handler、QQ 歌手搜索与专辑分类 / 分页数据，修复汽水授权结果处理。
网络、登录和平台协议由插件持有；播放器仅校验、展示标准评论数据。

这些是本地修改版，非上游官方发布；保持 `netease` / `qq` / `soda` 插件 ID，
以便识别收藏及优先播放源，并保留同 ID 插件凭据。配套 QPlayer 1.9.10-custom 使用。

运行 `python scripts/package_custom_plugins.py` 生成 `.qplug`。
包有逐文件哈希清单，未使用上游发布者签名；在测试版的音源管理里导入本地文件，
按应用提示确认后启用；更新时直接导入覆盖同 ID 插件。请使用随本次修改交付的插件，否则旧插件缺少新增接口。

最新评论支持翻页；热评展示平台提供的热门列表。平台登录失效或接口受限时，
应用会显示错误并允许重试。此扩展只读取评论，不发送评论或点赞。

汽水修复保持 QR 设备标识一致、处理数字扫码状态、跟随允许域名的授权跳转并合并 Cookie。
账号请求失败时保留凭据并重试，绝不伪造登录成功。模拟测试覆盖重启后设备标识；真实扫码仍需手机验证。

插件测试：`node --test custom-plugins/*.test.cjs`。
