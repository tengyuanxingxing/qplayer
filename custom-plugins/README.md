# 本地评论、歌手与登录扩展

`netease` 基于 https://github.com/TIMER-err/qplayer-netease-plugin，
`qqmusic` 基于 https://github.com/TIMER-err/qplayer-qqmusic-plugin。
`soda` 基于 https://github.com/MiaM1ku/qplayer-soda-plugin。
保留各项目许可证，本地增加只读 `comments` handler、QQ 歌手搜索与专辑分类 / 分页数据，修复汽水授权结果处理。
网络、登录和平台协议由插件持有；播放器仅校验、展示标准评论数据。

这些是本地修改版，非上游官方发布；保持 `netease` / `qq` / `soda` 插件 ID，
以便识别收藏及优先播放源，并保留同 ID 插件凭据。配套 QPlayer 1.9.11-custom 使用。

在“设置 → 插件”刷新列表，在已安装插件的“设置”里选择“更新”；未安装的插件从“可安装”入口进入。
三个仓库分别为 `tengyuanxingxing/qplayer-netease-plugin`、`tengyuanxingxing/qplayer-qqmusic-plugin`、`tengyuanxingxing/qplayer-soda-plugin`。
所有在线包使用本定制分支独立的 P-256 发布签名，宿主内固定对应公钥；未使用上游作者的签名。
版本为网易云 0.5.8.3、QQ 0.7.3.3、汽水 0.1.2。

`python scripts/package_custom_plugins.py --signing-key <外部PEM路径>` 可生成签名 `.qplug`，需要 OpenSSL；可用 `--openssl` 指定程序。
省略签名参数时仅生成供手动导入的未签名包，在线目录不会接受未签名包。私钥存放在仓库外，不能提交到 GitHub。
包仍包含逐文件哈希清单与原项目许可证；应用内安装需要用户确认权限。

最新评论支持翻页；热评展示平台提供的热门列表。平台登录失效或接口受限时，
应用会显示错误并允许重试。此扩展只读取评论，不发送评论或点赞。

汽水修复保持 QR 设备标识一致、处理数字扫码状态、跟随允许域名的授权跳转并合并 Cookie。
账号请求失败时保留凭据并重试，绝不伪造登录成功。模拟测试覆盖重启后设备标识；真实扫码仍需手机验证。

插件测试：`node --test custom-plugins/*.test.cjs`。
