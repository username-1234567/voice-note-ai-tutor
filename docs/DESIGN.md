# 随堂录 · 设计规范（现状提取）

> 本文档从现有代码中提取，记录**当前实际生效**的设计约定，作为后续 UI 改造的统一基准。
> 提取范围：`ui/theme/`、`ui/components/`、`ui/screens/**`（原生 Compose）与 `assets/render/index.html`。
> 不在范围：`assets/chat/`（chat-demo 构建产物，走 DeepSeek 官方视觉语言）。
>
> 提取日期：2026-11-21 · 对应版本 versionCode 15 / versionName 1.5.3 · 设计 token 单一来源：`app/src/main/java/com/suitanglu/app/ui/theme/Theme.kt`、`ui/theme/Motion.kt`

---

## 1. 设计基调

摘自 `Theme.kt` 头部注释，是所有用色/排版决策的上位原则：

- **克制的工具理性**：录音是后台任务，界面要安静。低饱和 + 白色卡片浮在浅灰页面（深色为暗蓝灰面板）。
- 不用渐变、不用玻璃拟态、不用高饱和强调色；**颜色只用来强化状态与操作层级**。
- **一屏只有一个高饱和块**：全页唯一主操作（首页「开始录音」FAB、弹窗破坏性确认键）用实心主色/error，其余一律降级（secondaryContainer、tonal、描边）。
- 层级靠**字重与颜色**表达，不靠一味放大字号；中文正文行高 1.5 倍，标题收紧。
- Material You 动态取色**默认关闭**（`Theme.kt:36 USE_DYNAMIC_COLOR = false`）：固定品牌色优先于随壁纸变色。
- 内容优先于卡片：首页时间轴刻意不用卡片（"一条连续的课堂记录"），播放页标记卡才用卡片。

---

## 2. 色板

### 2.1 主题色（Material 3 角色，浅/深成对）

定义于 `Theme.kt:38-126`。主色为靛蓝（品牌色），深浅两套完整 scheme。

| 角色 | Light | Dark | 实际用途 |
|---|---|---|---|
| primary | `#3B5BA5` | `#AEC0F5` | 录音 FAB、进度条已播放段、时间列、链接/强调、课程表入口图标 |
| onPrimary | `#FFFFFF` | `#10203F` | 主色实心块上的文字/图标 |
| primaryContainer | `#DCE4F7` | `#253866` | 录音中横幅、问题气泡（55% 透明）、多选行高亮（60%）、AI 测试成功横幅（50%） |
| onPrimaryContainer | `#0A1633` | `#D9E2FF` | 上述容器上的文字 |
| secondary | `#59627A` | `#B9C1D4` | 数据与存储入口图标 |
| secondaryContainer | `#E2E6F0` | `#394255` | 录音页暂停键、学术研讨 FAB |
| onSecondaryContainer | `#161C2A` | `#D6DCEA` | 同上 |
| tertiary | `#7A5A2E` | `#E0B87A` | 待复习星标 + 计数（琥珀调，与"待发送"区分） |
| tertiaryContainer | `#F7E7CB` | `#4A3510` | （与 pendingContainer 同值复用） |
| error / errorContainer | `#B3261E` / `#FBE3E1` | `#FFB4AB` / `#93000A` | 失败状态、破坏性确认、危险按钮 |
| background | `#F6F7FB` | `#0E1118` | 页面底色 |
| surface | `#FFFFFF` | `#141821` | 卡片、搜索框、迷你播放条 |
| surfaceVariant | `#E3E6EE` | `#3A4150` | 缩略图占位 |
| surfaceContainerHighest | `#E6EAF3` | `#2C3341` | 进度条轨道、思考过程块底（35%）、自测卡底（45%）、"待发送"chip 底 |
| surfaceContainerHigh | `#EFF2F8` | `#222835` | 倍速胶囊底、AI 对话气泡（对方） |
| outline | `#B4BAC8` | `#6E7688` | 无课程时间轴节点 |
| outlineVariant | `#D7DCE7` | `#3A4150` | 分隔线、未录音刻度、禁用态 |
| surfaceBright | `#FFFFFF` | `#2C3341` | 弹窗卡底（`AppAlertDialog`：深色"浮起感"来源） |
| inverseSurface / inverseOnSurface | `#2A303C` / `#EDEFF4` | `#E3E6EE` / `#2C3341` | 标记簇计数角标（黑底白字） |

### 2.2 语义扩展色（RecordColors，`Theme.kt:132-187`）

M3 色板没有的产品语义集中在这里，入口 `MaterialTheme.recordColors.*`：

| 语义 | Light | Dark | 用途 |
|---|---|---|---|
| success | `#16695A` | `#6FCFBC` | 讲解成功状态（StatusChip 前景、进度条标记点） |
| successContainer | `#D2EDE6` | `#10332C` | StatusChip 底 |
| pending | `#8A5A00` | `#E7B96B` | 待发送/发送中（进度条标记点、StatusChip） |
| pendingContainer | `#F7E7CB` | `#3D2E12` | StatusChip 底（= tertiaryContainer 同值） |
| pauseMark | `#C2410C` | `#FFB74D` | 进度条上的暂停/打断点（菱形）。Light 值经过实测校准：`#E08A00` 在轨道上仅 2.24:1，`#C2410C` 达 4.30:1（非文本 ≥3:1） |
| coursePalette | 8 色列表（首位绿 `#246B5A`，刻意不用主色靛蓝） | 8 色亮档 | 课程名 → 稳定分类色（`abs(hash) % 8`），用于筛选 chip 圆点、时间轴节点 |

### 2.3 实际存在的硬编码色（改造时的清理清单）

| 色值 | 位置 | 用途 | 状态 |
|---|---|---|---|
| `#3B5BA5` / `#AEC0F5` | `assets/render/index.html:19-20` | 渲染器链接色（与主色一致，硬编码双份） | 与主题同步维护 |
| `#3A3630` / `#E4E2DE` | `assets/render/index.html:16-17` | 渲染器正文色（暖灰，DeepSeek 观感） | **与原生 onSurface（冷灰 `#141A26`/`#E3E6EE`）不同调**，见 §8 |
| `Color.White` | `PlaybackScreen.kt:1370,1374,1387,1424` | 进度条标记点的"甜甜圈"中心 | 有意为之（配轨道色描边），两种外观下均可用 |
| `#1F8A73`（青绿）+ `#1B1B1B` | `screens/recording/UCropContract.kt:19-24` | uCrop 裁剪页 accent 与工具栏 | **旧品牌绿遗留**，未随靛蓝主题更新 |
| `#F5F7F6` / `#0E1312` | `res/values(-night)/themes.xml` windowBackground | 启动窗口底色（防白闪） | **与 Compose background 不同值**（绿灰 vs 蓝灰），旧调残留 |
| `Color.Black` + alpha | `AppDialogs.kt:127`（scrim 34%）、`PlaybackScreen.kt:1815`（看图蒙版 94%）、`RecordingScreen.kt:687`（删除钮衬底 50%）、`InAppCameraScreen.kt:149`（变焦读数衬底 45%） | 浮层蒙版/衬底 | 通用惯例，非品牌色 |
| `#25937B` 等四组渐变 | `components/IconPreview.kt:26-30` | 应用图标预览（对应四枚真实 launcher 图标） | 图标本体，非 UI 色 |

### 2.4 用色规则（代码注释中的既定口径）

1. 状态传达走**双通道**：颜色 + 文字/图标（StatusChip = 色点 + 文字；失败另有 Warning 图标），不裸靠色相（`PlaybackScreen.kt:2280`）。
2. 破坏性操作**不整屏撒红**：图标容器用 errorContainer 浅底，只有确认键升格 error 实心——"整屏唯一的高饱和块就该是它"（`AppDialogs.kt:250-257`）。
3. 半透明降级链：`primaryContainer.copy(alpha=0.55)` 问题气泡 > `0.5` 测试横幅 > `0.6` 多选行高亮；`onSurfaceVariant.copy(alpha=0.7/0.8)` 占位/图例（后者对比度实测 3.29:1 / 3.92:1，低于 AA，见审计报告）。
4. 图标块的色相编码：设置入口图标块 = tint 12% 透明度底 + 同色 19dp 图标（`SettingsComponents.kt:87`）。

---

## 3. 字体与字号阶梯

### 3.1 AppTypography（`Theme.kt:214-248`）

中文界面定制阶梯，全部 sp（随系统字体缩放）：

| Style | 字号/行高 | 字重 | 实际用途 |
|---|---|---|---|
| headlineLarge | 30/38 | SemiBold，letterSpacing −0.4 | 录音计时（覆写为 34sp，见 3.2） |
| headlineMedium | 25/33 | SemiBold，letterSpacing −0.2 | 首页/设置首页大标题（覆写为 23sp）、引导页欢迎语（原值） |
| headlineSmall | 22/30（M3 默认） | — | 引导页步骤标题（未在 Theme 中定义，用 M3 默认） |
| titleLarge | 19/26 | SemiBold | 二级页顶栏标题、弹窗标题、空态标题 |
| titleMedium | 15/21 | SemiBold | 列表行标题、卡片节标题、录音页时间列（覆写为 16sp） |
| titleSmall | 13/19 | Medium | 时间轴日期分组头、标记卡时间、卡片小标题 |
| bodyLarge | 16/25 | Regular | 设置行标题、引导页导语 |
| bodyMedium | 14/21 | Regular | 正文、备注、气泡 |
| bodySmall | 12.5/18 | Regular | 副标题、说明文字 |
| labelLarge | 14/20 | Medium | 按钮、多选操作 |
| labelMedium | 12/16 | Regular | 状态 chip、辅助说明、开销标签 |
| labelSmall | 11/15 | Regular | 时间轴副行、图例、最小说明 |

### 3.2 实际字号覆写（阶梯之外的"第 12-17 号字"）

| 实际值 | 位置 | 来源 | 说明 |
|---|---|---|---|
| 23sp | `HomeScreen.kt:691`、`SettingsScreen.kt:105` | `headlineMedium.copy` | 首页/设置首页大标题。`SecondaryPage.kt:122-124` 注释明确三级标题口径：**首页 23sp、二级页 19sp、录音页 19sp** |
| 19sp | `SecondaryPage.kt:143` | `titleLarge.copy` | 与 titleLarge 本体同值（防御性覆写，等于没改） |
| 16sp | `HomeScreen.kt:883` | `titleMedium.copy` | 时间轴时间列 |
| 34sp | `RecordingScreen.kt:843` | `headlineLarge.copy` | 声纹环中心计时 |
| 18sp | `DataSettingsScreen.kt:198` | `titleLarge.copy` | 存储统计大数字 |
| 10sp | `PlaybackScreen.kt:1396` | 独立 TextStyle | 进度条标记簇计数角标 |

**结论**：23/19/16/34/18 这五档是事实上的扩展阶梯，但都以 `.copy(fontSize=…)` 散落各页，没有命名 style——改造时应提升为命名样式（如 `appTitle` / `recTimer` / `statNumber`），或直接把阶梯值改齐。

### 3.3 数字排版约定

- 所有**计时/计数/进度**文本一律 `.tabularNumbers()`（`Theme.kt:200`，font-feature `tnum`），避免每 200ms 刷新时左右跳动。涉及：录音计时、播放时间、时长、"N 条"、费用、token 用量、存储统计。

---

## 4. 间距体系

无 spacing token 文件，实际收敛为以下隐含刻度（按出现频次）：

| 刻度 | 用途 |
|---|---|
| 4dp | 卡片纵向间距、小元素间隙、chip 行内隙 |
| 6–8dp | 紧凑间隙（chips 行 6dp、图标-文字 8dp、图片横滚 8dp） |
| 10–12dp | 组件内标准间隙（行内 12dp、弹窗正文区 12dp、图片间 8dp） |
| **16dp** | **页面左右主边距**（横滚卡片/列表统一 `padding(horizontal = 16.dp)`，出现 18 次）、卡片内边距、弹窗正文边距 |
| 20dp | 首页/录音页主边距、底栏表单边距 |
| 22–24dp | 弹窗边缘距屏 28dp、空态内边距 32dp、引导页 24/28dp |

- 列表底留白：首页 `contentPadding bottom = 96dp`（给 FAB 让位）；播放页 `32dp`（迷你条出现时 `88dp`）。
- 纵向节奏：二级页内容 `Arrangement.spacedBy(12.dp)`（`SecondaryPage.kt:83`）；设置卡片节标题上下 8dp。
- 键盘处理：表单页一律 `Scaffold(Modifier.imePadding())` + 内容 `verticalScroll`（底部保存键不被输入法盖住）。

## 4b. 圆角

**既定约定（Theme.kt:202-211）：只用两档**——`10dp = 控件`（输入框、缩略图、图标块、行高亮、chip）、`16dp = 容器`（卡片、横幅）。Shapes 表全部映射到这两档（extraSmall/small=10，medium/large/extraLarge=16）。

实际屏幕中越档的值（改造时收敛对象）：

| 值 | 位置 |
|---|---|
| 6dp ×2 | `PlaybackScreen.kt:1937,1955` 版本切换 ‹ › 的点击 clip |
| 8dp ×4 | `RecordingScreen.kt:233,239,682` 待发图片缩略图、`InAppCameraScreen.kt:148` 变焦读数 |
| 2dp | `OnboardingScreen.kt:74` 进度条（线性元素，合理例外） |

## 4c. 阴影 / 高度

以**色调分层为主，阴影为辅**（深色模式浮起感来自 `surfaceBright`，不靠加深阴影）：

| 值 | 用途 |
|---|---|
| 0dp | 首页录音横幅（靠 primaryContainer 色块本身） |
| **1dp** | 所有内容卡（播放页标记卡、设置节卡、回收站卡）——事实标准 |
| 8dp | 播放页合并模式底栏 Surface |
| 10dp | `AppAlertDialog` 弹窗卡 |

---

## 5. 动效

单一来源 `ui/theme/Motion.kt`：

| 常量 | 值 | 语义 |
|---|---|---|
| PRESS_IN / PRESS_OUT | 90 / 160ms | 按下到位快、松手回弹慢（手感"弹"不"顶"） |
| PRESS_SCALE / _WIDE / _ICON | 0.96 / 0.98 / 0.90 | 面积越大缩放越小 |
| POP_IN / POP_OUT_SCALE | 0.95 / 0.97 | 弹窗缩放起止（不从 0 开始） |
| DIALOG_ENTER / EXIT | 200 / 120ms | 退出 ≈ 进入 60% |
| PAGE_ENTER / PAGE_EXIT | 360 / 240ms | 页面切换（NavHost 全局：淡入 + 1/12 屏宽横移，pop 方向反向，退出带 40ms 延迟） |
| FOLLOW_MS | 40ms | 配角动作的起步延迟 |

- `Modifier.pressFeedback(interactionSource, scale)`：按下即缩的统一触觉反馈；**同一个 InteractionSource 必须同时交给 clickable/IconButton**。
- `rememberReducedMotion()`（读系统动画时长开关）：装饰性动效全部直接到位；**数据本身（音量条、计时、进度）不受影响**。
- 已接入处：导航过渡、AppAlertDialog 进出场、看图蒙版、播放器首现、标记卡展开（tween 240 非 spring，避免高度跳变叠加回弹）、播放/暂停图标交叉淡入、倍速数字 AnimatedContent + SizeTransform。
- **已知例外（未接入 reducedMotion 的装饰动画）**：首页脉冲点 `HomeScreen.kt:1121-1134`、首页横幅呼吸点 `HomeScreen.kt:1149-1155`、录音页 RecordingDot `RecordingScreen.kt:887-903`。

---

## 6. 组件清单（可复用）

### 全局组件（`ui/components/`）

| 组件 | 参数约定 | 说明 |
|---|---|---|
| `AppAlertDialog`（AppDialogs.kt:77） | `title, message?, confirmLabel, onConfirm, onDismissRequest, destructive=false, showIcon=false, dismissLabel="取消", showDismiss=true, confirmEnabled=true, content?` | 全应用唯一确认弹窗。破坏性 = 确认键 error 实心 + 图标容器 errorContainer；按钮统一 44dp 高 + pressFeedback；蒙版 34% 黑；带 120ms 退场（先动画后回调）；`showDismiss=false` 用于阻断式提示 |
| `SecondaryPage`（SecondaryPage.kt:49） | `title, onBack, bottomBar?, unsavedChanges=false, content` | 二级页统一容器：19sp 顶栏 + 可滚动内容 + 底部常驻操作条；`unsavedChanges=true` 时返回（含系统返回）先弹放弃确认 |
| `SecondaryTopBar`（SecondaryPage.kt:126） | `title, onBack, actions?` | 自绘顶栏：statusBarsPadding + 返回 + 19sp 标题 |
| `BottomActionRow`（SecondaryPage.kt:106） | `content: RowScope` | 底部操作条：主操作占满、次操作描边平分，10dp 间距 |
| `ZoomableImage`（ZoomableImage.kt:34） | `model, contentDescription, background=Black` | 双指缩放(1–6×)+拖动；刻意不做双击复位（与拖动手势冲突） |
| `IconPreview`（IconPreview.kt:24） | `alias` | 应用图标 Canvas 重绘预览（adaptive-icon 不能直接加载） |
| `UpdatePromptHost`（UpdatePromptHost.kt:51） | 无参 | 进程级更新弹窗（挂在 MainActivity），复用 AppAlertDialog |

### 播放页组件（`ui/screens/playback/PlaybackScreen.kt`，页面私有）

| 组件 | 说明 |
|---|---|
| `MarkerProgressBar` | 60dp 高 Canvas：轨道 + 已播放 + 分钟刻度 + 标记簇（2% 聚合、计数角标）+ 合并区间条 + 暂停菱形 + 可缩放播放头；拖动先于 tap 挂载 |
| `MarkerCard` | 标记卡：折叠摘要（备注优先，取首个非空行去 Markdown 记号）/ 展开全量；展开时高度 `animateContentSize(tween 240)` |
| `TurnView` | 单轮问答：问题气泡（首轮显示备注）→ 版本切换 1/N → ReasoningBlock → MarkdownView 回答 → 追问输入 |
| `StatusChip` | 状态 chip：色点 + 文字双通道，色对见 §2.2 |
| `ReasoningBlock` | 思考过程：260dp 窗口 + Compose `verticalScroll`（WebView 必须 `touchable=false`）；思考中自动跟最新行 |
| `FollowUpSuggestions` / `QuizCard` | 追问建议/自测题拼成 markdown 走 `#ask:i` / `#opt:X` 链接回调；**点击只填入，不直发**（防计费误触） |
| `SpeedButton` / `SpeedMenu` | 倍速胶囊（≥40×46dp）+ 菜单必须锚在触发按钮自身（LazyColumn 回收坑） |
| `MiniPlayer` / `MiniProgressBar` | 滚出播放器后的迷你条：16dp 触控高进度线（视觉 3dp），整行点击回顶部 |

### 内容渲染（`ui/screens/playback/MarkdownView.kt` + `assets/render/index.html`）

| 参数 | 约定 |
|---|---|
| `text` | 唯一渲染入口：离线 markdown-it + KaTeX |
| `fold` | 按二级标题分节折叠（前两节展开，第三节起收起）；**流式内容不折叠**（高度会追不上） |
| `animate` | 高度 `animateContentSize` 开关；流式时关（300ms 长一截，动画追不上反而像卡） |
| `touchable` | false 时 WebView 不消费触摸（`onTouchEvent` 返回 false），外层 Compose 滚动接管——固定高度窗口内必须 false |
| `onOption` / `onSuggestion` | `#opt:X` / `#ask:i` 链接点击回调（JS 桥回传） |

- 高度自适应三重保障：reportHeight → rAF 后补报 → `document.fonts.ready` 再报；`ResizeObserver` 兜底（`index.html:114-120,191-204`）。
- 流式与最终回答**共用同一个 MarkdownView 实例**（拆两个分支会在生成结束时整块重建、WebView 闪白）。
- 渲染器排版常量：正文 14px/1.75、代码 13px、表格 13px、`pre` 强制折行（`pre-wrap` + `width:100%`，防长行撑爆 WebView）、KaTeX 块级 `overflow-x:auto`、折叠箭头为 `⌄` 字符 0.4 透明度。

### 设置组组件（`ui/screens/settings/`）

| 组件 | 参数 | 说明 |
|---|---|---|
| `SettingsSection`（SettingsComponents.kt:36） | `title, compact=false, content` | 分区：标题在卡片外（onSurfaceVariant 13sp），Card 1dp 阴影，内边距 16dp（compact 模式 4dp，给跳转列表） |
| `SettingsEntry`（SettingsComponents.kt:68） | `title, subtitle, icon, tint, onClick` | 跳转行：34dp 图标块（tint 12% 底 + 19dp 图标）+ 标题(bodyLarge) + 摘要(labelMedium) + 右箭头 |
| `SwitchRow`（AiSettingsScreen.kt:354，定义在 AiSettingsScreen.kt 而非 SettingsComponents.kt） | `title, subtitle?, checked, onCheckedChange` | 开关行：整行标题+说明+右侧 Switch |

---

## 7. 文案与交互口径（踩坑换来的既定约定）

1. **费用敏感处必须说出来**：已生成的讲解删除不退费（弹窗文案）、追问误触即计费（建议点击只填入）、"累计"是全部版本之和（`PlaybackScreen.kt:658-667`）、token 用量逐轮展示（UsageHint）。
2. **空态要有出路**：每种空态都配一个恢复动作按钮（清除搜索 / 看全部 / 查看全部 / 生成提纲）；筛选入口在计数归零时保留，防止用户卡在空列表。
3. **危险动作分级**：可恢复（移入回收站）→ 主色确认键；不可恢复（彻底删除/仅删音频）→ `destructive=true` + 警示图标 + error 实心键。
4. **图标即语义**：回收站用还原图标（RestoreFromTrash）不用删除图标；倍速用真乘号 `×` 不用字母 x；播放三角视觉居中右移 1.5dp。
5. **状态文案**：生成中文案要区分"正文讲完了，正在整理追问与自测…"（防以为卡死）；打断要写全原因与后续行为（"音频焦点恢复后自动继续"）。
6. **长度兜底**：所有列表行/标题 `maxLines + Ellipsis`；唯一例外是展开态（展开还要省略号就是 bug，`PlaybackScreen.kt:1527-1537`）。

---

## 8. 现状不一致点（同一语义，不同数值/做法）

改造时按此表收敛，优先级从上到下：

| # | 语义 | 现状 A | 现状 B | 位置 |
|---|---|---|---|---|
| 1 | **正文灰（渲染器 vs 原生）** | 渲染器 `#3A3630`/`#E4E2DE`（暖灰，DeepSeek 调） | 原生 onSurface `#141A26`/`#E3E6EE`（冷灰） | `render/index.html:16-17` vs `Theme.kt:63,108` |
| 2 | **启动窗口底色** | XML `#F5F7F6` / `#0E1312`（绿灰，旧调） | Compose background `#F6F7FB` / `#0E1118`（蓝灰） | `res/values(-night)/themes.xml` vs `Theme.kt:60,105` |
| 3 | **裁剪页 accent** | uCrop `#1F8A73`（旧品牌绿）+ 黑工具栏 | 全局靛蓝 `#3B5BA5` | `UCropContract.kt:19-24` |
| 4 | **大标题字号** | 23sp（首页/设置首页，两处覆写） | headlineMedium 本值 25sp | `HomeScreen.kt:691`、`SettingsScreen.kt:105` vs `Theme.kt:221` |
| 5 | **大数字字号** | 34sp（录音计时）/ 18sp（存储统计） | headlineLarge 30sp / titleLarge 19sp | `RecordingScreen.kt:843`、`DataSettingsScreen.kt:198` |
| 6 | **成功语义底色** | successContainer `#D2EDE6`（StatusChip） | primaryContainer@0.5（AI 测试成功横幅） | `PlaybackScreen.kt:2261` vs `AiSettingsScreen.kt:196` |
| 7 | **圆角两档约定** | 10/16（token） | 6dp ×2、8dp ×4 散落 | `PlaybackScreen.kt:1937,1955`、`RecordingScreen.kt:233,239,682`、`InAppCameraScreen.kt:148` |
| 8 | **触达目标** | 48dp（M3 默认 IconButton/Button） | 40dp（首页搜索两键、更多菜单、倍速、迷你播放）、30dp（星标/删除）、24dp（图片移除）、~16-24dp（版本 ‹ ›、思考过程标题行、迷你进度线） | `HomeScreen.kt:649,658,997`、`PlaybackScreen.kt:1544,1569,1929-1958,2105,2311,2418,2529`、`RecordingScreen.kt:686` |
| 9 | **二级页顶栏** | 自绘 SecondaryTopBar（19sp） | M3 TopAppBar（22sp，渲染预览页）；播放页自研滚动响应顶栏 | `SecondaryPage.kt:126`、`RenderScreen.kt:62`、`PlaybackScreen.kt:1036` |
| 10 | **图标风格** | Outlined（回收/对话/星标边） | Filled（设置齿轮、菜单项、播放控制）混用于同一行 | `HomeScreen.kt:701-720` 等 |
| 11 | **"待复习 N" 文案** | `待复习 $totalReview`（首页 chip） | `待复习（N）`（播放页标题，字符串拼接） | `HomeScreen.kt:378` vs `PlaybackScreen.kt:651` |
| 12 | **注释与实现漂移** | NavHost 注释写 320/210ms | MotionSpec 实际 360/240ms | `RecordNavHost.kt:72-74` vs `Motion.kt:61-66` |
| 13 | **占位文字对比度** | onSurfaceVariant@0.7 → 实测 3.29:1 | AA 要求 4.5:1 | `HomeScreen.kt:634`；录音页图例 @0.8 → 3.92:1（`RecordingScreen.kt:426`） |
| 14 | **无课程节点可见性** | outline `#B4BAC8` 作 4dp 实心节点 | 对页面底色仅 1.82:1（图形要求 3:1） | `HomeScreen.kt:856,895-908` |
| 15 | **装饰脉冲 vs reducedMotion** | 已建 `rememberReducedMotion` 且普遍接入 | 3 处呼吸点未接入 | §5 已知例外 |
| 16 | **SwitchRow 定义位置** | 复用组件写在 AiSettingsScreen.kt | 同级组件集中在 SettingsComponents.kt | `AiSettingsScreen.kt:354` |
| 17 | **渲染器字体栈** | `-apple-system, MiSans, HarmonyOS Sans, Noto Sans CJK SC`（含 iOS 专属项） | 原生用系统默认（Roboto/MiSans） | `render/index.html:12` |

> 改造基准决议（建议）：#1/#2/#3 统一到靛蓝冷灰体系；#4/#5 提升为命名 style；#6 统一用语义 success；#7 收敛回 10/16；#8 以 48dp 为底线、40dp 仅限空间受限处并外扩命中区。
