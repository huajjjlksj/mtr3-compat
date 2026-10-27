# MTR3 Compat

> **AI 生成声明**：本项目的全部源代码与本文档均由 AI（Trae / DeepSeek 模型）根据需求描述生成，未经人工逐行审查。代码仅经过实际游戏内测试验证部分场景，可能存在缺陷、兼容性问题或未覆盖的边界情况。请自行评估风险后使用；因使用本模组造成的任何问题（包括但不限于存档损坏、客户端崩溃、服务器不同步）由使用者自行承担。

让 **MTR3 时代（MTR 3.x + NTE）的资源包** 在 **MTR4** 环境下正常加载与渲染的 Fabric 客户端兼容层。

设计原则：**只改 mod，不改资源包**；mod 内不硬编码任何资源包名，哪些包按 MTR3 模式加载完全由配置或界面勾选决定。

---

## 功能

- **按包切换 MTR3 / MTR4 模式**：MTR 设置面板右下角「以MTR3加载」按钮，打开按包勾选界面；关闭界面时若勾选有变化会自动重载资源包。
- **无需进入存档即可切换**：原版「选项 → 资源包」界面右上角新增图标按钮，点击直接进入同一个按包勾选界面；两个界面之间会自动协调重载（谁有改动由谁触发，均无改动则不重载）。
- **MTR3 语义原生注册车型**：勾选的资源包里 `custom_trains` 条目按 MTR3 语义注册（一车一条目、无 MTR4 的四变体拆分），几何按 `(currentCar, totalCars)` 逐车过滤 `whitelisted_cars` / `blacklisted_cars`。
- **NTE / JCM 脚本兼容层**：
  - 脚本解析窗口内把 `Resources.getMTRVersion()` 伪装为 MTR3 版本，避免脚本因检测到 MTR4 直接画蓝屏退出；
  - 提供 `MTRClientData.DATA_CACHE` 替身、`route.platformIds`、`rail.getLength()/getPosition()`、`station.exits.entrySet()`、`RawModel.sourceLocation`、`Matrices.setIdentity()` 等 MTR3/NTE 旧 API；
  - 修正车辆选车脚本、LCD 脚本、贯通道脚本的取进度/取车厢空间行为。
- **MTR3 式自动编组**：车辆选择面板顶部开关，选中车型后按轨道长度自动铺满（默认开启）。
- **渲染修正**：声明了风挡连接但未提供风挡贴图的车型回退到内置风挡贴图；模型/贴图/属性文件在已启用资源包中缺失的车型会被自动隐藏，避免出现「只有风挡没有车身」（检查时会自动补全 `.obj` / `.bbmodel` / `.json` / `.png` 扩展名）。
- **配置热重载**：`config/mtr3-compat.json` 修改后 ≤0.5 秒自动生效，无需重启。

## 环境要求

> **注意：本分支版本仅支持 JCM 2.3.0-prerelease.1 与 2.3.0-prerelease.2。** 其他 JCM 版本（含 beta 系列与后续版本）未适配，可能导致启动崩溃。

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| 加载器 | Fabric Loader（≥ 0.14.21） |
| 前置 | Fabric API |
| 前置 | Minecraft Transit Railway **4.0.5+**（fabric） |
| 前置 | Joban Client Mod **2.3.0-prerelease.1 / 2.3.0-prerelease.2**（mod id `jsblock`，**仅这两个版本**） |
| Java | 17+ |
| 侧别 | **仅客户端**（服务器无需安装） |

## 安装

1. 将本模组 jar 放入 `.minecraft/mods/`；
2. 同时安装上表所列前置（MTR4、JCM、Fabric API）；
3. 把要使用的 MTR3 / NTE 资源包放入 `resourcepacks/`，并在游戏内「选项 → 资源包」中启用；
4. 启动游戏。

## 使用

### 1. 标记哪些资源包按 MTR3 加载

进入任意存档 → 打开 **MTR 设置面板** → 点击右下角 **「以MTR3加载」** → 在列表中点击包名，在 `MTR3:` / `MTR4:` 之间切换 → 关闭界面后自动重载。

> 未标记的包保持 MTR4 原生行为；标记的包会被本模组接管解析。

### 2. 手动编辑配置（可选）

配置文件：`.minecraft/config/mtr3-compat.json`

```json
{
  "mtr3_packs": [
    "示例资源包名"
  ],
  "script_car_offsets": {
    "示例资源包名": {
      "x": 0,
      "y": 0,
      "z": -2.5
    }
  }
}
```

- `mtr3_packs`：按 MTR3 模式加载的资源包名（**不含 `.zip`**，大小写不敏感）。
- `script_car_offsets`：按资源包给脚本「车厢空间」绘制加的平移量，单位是方块；三个轴可任意组合，缺省为 `0`。仅在脚本绘制位置整体偏移时使用。
- 文件修改后 ≤0.5 秒自动生效。

### 3. 选车与编组

在车辆选择面板中选择已注册的车型；面板顶部可切换「MTR3 自动编组」，开启后按轨道长度自动铺满整列车。

## 已知限制

- 仅客户端生效；服务器端不会同步本模组的任何逻辑。
- 不修改资源包：资源包本身缺失模型/贴图/属性文件的车型无法凭空渲染，会被隐藏。
- MTR4 与 JCM 均无「受电弓升弓」状态机，模型中的受电弓只能是静态几何。
- 部分依赖 NTE 特有 API 的脚本仍可能不兼容，异常可能被脚本引擎静默吞掉；调试时可将 `Mtr3CompatClient.DEBUG_INSTRUMENT_CATCH` 置为 `true` 查看。
- 勾选包内脚本的行为完全由脚本作者决定（例如 LCD 的进站动画开关写在资源包配置里），本模组不强行覆盖。

## 目录结构

```
src/client/java/com/xm/mtr3compat/client/
├── Mtr3CompatClient.java        # 客户端入口
├── Mtr3CompatConfig.java        # 配置读写与热重载
├── Mtr3CompatPacks.java         # 资源包扫描
├── Mtr3PackSelectScreen.java    # 按包勾选界面
├── NativeTrainLoader.java       # MTR3 语义车型注册
├── LegacyEntry.java             # MTR3 车型条目解析
├── ante/                        # NTE 脚本 API 兼容实现
├── jcm/                         # JCM 脚本兼容实现
└── mixin/                       # 对 MTR4 / JCM 的注入点
```

## 版权与致谢

- 感谢 Minecraft Transit Railway、Joban Client Mod 作者及各类资源包作者。
- 本项目不包含任何资源包内容，也不附带 MTR / JCM 的二进制文件。
- 模组元数据中声明的许可证为 MIT。
