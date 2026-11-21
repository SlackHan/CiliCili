# CiliCili 规则同步与测活报告

- 更新时间 (UTC): `2026-11-21 10:45:09`
- 候选总数: `44`
- 通过测活 / 保留输出: `22` / `22`

## 同步日志

- 拉取 Prowlarr 规则失败 [bt4g]: HTTP 404
- 已转换 Prowlarr 公开规则 [uindex] -> #10201 Uindex
- 拉取 Prowlarr 规则失败 [knaben]: HTTP 404
- 跳过不兼容的 Cardigann 规则 [limetorrents]
- 拉取 Prowlarr 规则失败 [torlock]: HTTP 404
- 拉取 Prowlarr 规则失败 [solidtorrents]: HTTP 404
- 拉取 Prowlarr 规则失败 [bitsearch]: HTTP 404

## 节点状态明细

| ID | 名称 | 类型 | 状态 | 耗时 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 10001 | SOBT | HTML | 剔除 | 1226ms | HTTP 404 |
| 10002 | 磁力引擎搜索 | HTML | 可用 | 477ms | HTTP 200 可达 |
| 10003 | 磁力宝 | HTML | 剔除 | 191ms | HTTP 403 |
| 10004 | 磁力猫 | HTML | 剔除 | 1226ms | HTTP 404 |
| 10005 | 磁力狗 | HTML | 剔除 | 1237ms | HTTP 404 |
| 10006 | btfox | HTML | 剔除 | 26ms | 网络异常: <urlopen error [Errno -2] Name or service not known> |
| 10007 | ØMagnet | HTML | 可用 | 570ms | HTTP 200 可达 |
| 10008 | 种子吧 | HTML | 剔除 | 14ms | 网络异常: <urlopen error [Errno -2] Name or service not known> |
| 10009 | 1024BT | HTML | 剔除 | 32ms | HTTP 403 Cloudflare 盾 |
| 10010 | 吃力网 | HTML | 剔除 | 36ms | HTTP 403 Cloudflare 盾 |
| 10011 | 磁力树 | HTML | 剔除 | 39ms | HTTP 403 Cloudflare 盾 |
| 10012 | 小草磁力 | HTML | 剔除 | 754ms | HTTP 403 Cloudflare 盾 |
| 10013 | BT目录 | HTML | 剔除 | 177ms | HTTP 403 Cloudflare 盾 |
| 10014 | 简单动漫 | HTML | 剔除 | 360ms | HTTP 404 |
| 10016 | torrentkitty | HTML | 剔除 | 260ms | HTTP 403 Cloudflare 盾 |
| 10017 | 吴签磁力 | HTML | 可用 | 1755ms | HTTP 200 可达 |
| 10018 | 种子搜 | HTML | 剔除 | 1339ms | HTTP 404 |
| 10019 | 磁力爬 | HTML | 可用 | 376ms | HTTP 200 可达 |
| 10020 | BTMirror | HTML | 剔除 | 666ms | HTTP 401 |
| 10022 | 磁力海 | HTML | 可用 | 112ms | HTTP 200 可达 |
| 10023 | 飞鱼磁力 | HTML | 剔除 | 710ms | HTTP 403 Cloudflare 盾 |
| 10024 | 动漫花园 | HTML | 可用 | 596ms | HTTP 200 可达 |
| 10025 | 磁力天堂 | HTML | 剔除 | 45ms | HTTP 403 Cloudflare 盾 |
| 10026 | 海盗船 | HTML | 可用 | 132ms | HTTP 200 可达 |
| 10027 | 磁力多 | HTML | 可用 | 650ms | HTTP 200 可达 |
| 10028 | 磁狐搜索 | HTML | 可用 | 647ms | HTTP 200 可达 |
| 10029 | SKrBT | HTML | 可用 | 1451ms | HTTP 200 可达 |
| 10030 | SoKitty | HTML | 可用 | 197ms | HTTP 200 可达 |
| 10031 | 52BT | HTML | 剔除 | 196ms | HTTP 403 Cloudflare 盾 |
| 10032 | 天天磁力 | HTML | 可用 | 635ms | HTTP 200 可达 |
| 10033 | BT联盟 | HTML | 可用 | 666ms | HTTP 200 可达 |
| 10034 | 磁力狐 | HTML | 可用 | 816ms | HTTP 200 可达 |
| 10035 | 磁力搜索 | HTML | 可用 | 364ms | HTTP 200 可达 |
| 10036 | BTSOW | HTML | 可用 | 623ms | HTTP 200 可达 |
| 10037 | 磁力星球 | HTML | 可用 | 623ms | HTTP 200 可达 |
| 10038 | 吃力网分享 | HTML | 可用 | 378ms | HTTP 200 可达 |
| 10039 | 高清族 | HTML | 剔除 | 1425ms | HTTP 403 |
| 10040 | Btkitty | HTML | 剔除 | 1601ms | HTTP 404 |
| 10041 | 磁力片 | HTML | 可用 | 205ms | HTTP 200 可达 |
| 10101 | 海盗湾 API | JSON_API | 可用 | 422ms | 命中 100 条 |
| 10102 | Nyaa 动漫 RSS | RSS | 可用 | 300ms | 命中 1 条 |
| 10103 | 动漫花园 RSS | RSS | 可用 | 464ms | 命中 2 条 |
| 10104 | AnimeTosho API | JSON_API | 剔除 | 313ms | 网络异常: <urlopen error [Errno -2] Name or service not known> |
| 10201 | Uindex | HTML | 剔除 | 198ms | HTTP 403 Cloudflare 盾 |
