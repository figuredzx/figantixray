一个我的世界fabric1.20.1仅服务器依靠记录挖掘方块数据查找矿透作弊者的查询mod 该模组由deepseek生成

需要Fabric API作为前置

FigantiXray反矿透模组 - 完整使用指南

⚠️ 重要使用说明
🔥 关键提示：方块ID必须使用引号包裹
由于Minecraft命令系统对冒号字符的特殊处理，所有包含冒号的方块ID都必须用引号包裹，否则会导致命令解析错误。

✅ 正确用法（必须使用引号）：

/figantixray addblock "minecraft:diamond_ore"
/figantixray setblockname "minecraft:diamond_ore" "珍贵钻石矿"
/figantixray blockthreshold 32 "minecraft:diamond_ore"

❌ 错误用法（会导致问题）：
/figantixray addblock minecraft:diamond_ore
/figantixray setblockname minecraft:diamond_ore 珍贵钻石矿

🚨 违规自动记录系统
双重存储架构：同时在玩家文件和违规目录保存记录
时间戳记录：自动记录Unix时间戳，方便服务器回放
位置追踪：记录违规发生的坐标和维度信息
智能检测：超过阈值立即自动保存详细记录

📅 服务器回放支持
精确时间定位：使用时间戳在回放系统中精确定位
批量记录：自动记录每次违规行为
历史追溯：保留最近100条违规记录

🎯 精细化阈值控制
全局阈值：设置统一的检测标准
独立阈值：为每个方块设置不同的敏感度

🔒 多重安全保护
密码验证：删除敏感数据需要密码确认
权限控制：所有命令需要OP权限（等级2）
操作审计：完整记录所有管理操作

📋 完整命令列表
🔍 状态监控
/figantixray status	查看模组运行状态
/figantixray check	检查所有玩家数据
/figantixray check <玩家名>	检查特定玩家

⚙️ 阈值设置
/figantixray threshold <数量>	设置全局阈值
/figantixray blockthreshold <数量> "<方块ID>"	设置特定方块阈值

🧱 方块管理
/figantixray addblock "<方块ID>"	添加监控方块
/figantixray setblockname "<方块ID>" "<自定义名称>"	设置自定义名称
/figantixray removeblock "<方块ID>"	移除监控方块
/figantixray listblocks	查看所有监控方块

⚖️ 奖励管理
/figantixray reduceblock <玩家名> <方块ID> <数量> <原因>	减少玩家方块数量
/figantixray reductionhistory <玩家名>	查看减少记录

🚨 违规审查系统
/figantixray violationhistory <玩家名>	查看详细违规记录
/figantixray violationtimestamps <玩家名>	查看违规时间戳记录

🔐 数据安全
/figantixray deleteplayer <玩家名> <密码>	删除指定玩家数据
/figantixray deleteblockdata "<方块ID>" <密码>	删除方块历史数据
/figantixray changepassword <旧密码> <新密码>	修改删除密码
/figantixray oprecord <on/off>	开关OP数据记录

🚨 违规自动记录系统
双重存储架构
text
config/figantixray/data/
├── players/
│   └── Steve/
│       ├── violation_timestamps.json  # 时间戳记录
│       └── summary.json               # 玩家汇总
└── violations/                        # 违规记录目录
    └── Steve/
        └── violation_20241201_143022.json
        
记录内容包含：
精确时间戳：Unix时间戳，用于服务器回放
位置信息：违规发生的坐标和维度
阈值详情：全局和方块特定阈值信息
方块数据：详细的挖掘数量和对比信息

🎨 自定义方块名称系统

✅ 添加方块（必须使用引号）：
/figantixray addblock "minecraft:diamond_ore"

✅ 设置自定义名称（必须使用引号）：
/figantixray setblockname "minecraft:diamond_ore" "珍贵钻石矿"

✅ 移除自定义名称：
/figantixray setblockname "minecraft:diamond_ore" ""

名称优先级：
🥇 自定义名称（最高优先级）
🥈 默认中文映射
🥉 原始方块ID

🎯 智能阈值系统

🌍 全局设置（适用于所有方块）：
/figantixray threshold 64

🎯 特定方块设置（必须使用引号）：
/figantixray blockthreshold 32 "minecraft:diamond_ore"    # 钻石矿更敏感
/figantixray blockthreshold 8 "minecraft:ancient_debris"  # 下界残骸极敏感
/figantixray blockthreshold 0 "minecraft:diamond_ore"     # 移除特殊设置

🔔 智能预警系统
OP自动提醒：管理员上线时自动显示可疑玩家
实时监控：24/7不间断监控玩家行为
详细报告：提供完整的挖掘数据和分析

📊 数据展示示例

状态报告
=== FigantiXray反矿透状态 ===
全局警告阈值: 64 个方块
特殊方块阈值:
  珍贵钻石矿: 32 个
  稀有下界残骸: 8 个
监控方块数量: 7 种
超过阈值的玩家: 2 名

玩家检查
=== Steve 的挖掘数据 ===
总计稀有方块: 45 个
珍贵钻石矿: 25 个 (阈值: 32)
稀有下界残骸: 20 个 (超过阈值 8) 🔴

🚨 违规时间戳记录

=== Steve 的违规时间戳记录 ===
总计记录: 5 条
💡 提示: 使用这些时间戳可以方便地在服务器回放中定位

--- 时间戳记录 #1 ---
可读时间: 2024-01-15 14:30:22
Unix时间戳: 1705321822000
位置: (125, 45, -320)
维度: minecraft:overworld
总方块数: 68
方块详情:
  - 珍贵钻石矿: 25/32
  - 稀有下界残骸: 20/8

方块列表
监控方块列表 (7 种):
  珍贵钻石矿 [自定义] (minecraft:diamond_ore) - 阈值: 32
  稀有下界残骸 [自定义] (minecraft:ancient_debris) - 阈值: 8
  金矿 (minecraft:gold_ore) - 阈值: 64
  
⚡ 快速入门指南

第一步：基础配置
# 修改默认密码
/figantixray changepassword default_password_123 你的安全密码

# 设置全局阈值
/figantixray threshold 50

第二步：添加监控方块（必须使用引号）
# 添加常见稀有方块（使用引号包裹）
/figantixray addblock "minecraft:diamond_ore"
/figantixray addblock "minecraft:ancient_debris"
/figantixray addblock "minecraft:emerald_ore"

第三步：设置自定义名称（必须使用引号）
# 为方块设置易读的自定义名称（使用引号包裹）
/figantixray setblockname "minecraft:diamond_ore" "珍贵钻石矿"
/figantixray setblockname "minecraft:ancient_debris" "稀有下界残骸"
/figantixray setblockname "minecraft:emerald_ore" "绿宝石矿"

第四步：设置精确阈值（必须使用引号）
# 为特别稀有的方块设置更低阈值（使用引号包裹）
/figantixray blockthreshold 20 "minecraft:diamond_ore"
/figantixray blockthreshold 5 "minecraft:ancient_debris"

第五步：日常监控
# 查看状态
/figantixray status

# 检查可疑玩家
/figantixray check

# 查看所有监控方块
/figantixray listblocks

🚨 第六步：违规审查
# 查看玩家的违规记录
/figantixray violationhistory Steve

# 查看时间戳记录（用于服务器回放）
/figantixray violationtimestamps Steve

🛡️ 安全特性

🔐 权限管理
OP专属：所有命令需要权限等级2
密码保护：敏感操作需要二次验证
操作日志：完整记录所有管理操作

💾 数据安全
自动备份：定期保存玩家数据
安全删除：删除操作需要密码验证
防止误删：明确的警告和确认流程

🎮 服务器阈值设置

初始配置：根据服务器类型调整全局阈值
生存服务器：40-60
空岛服务器：20-40
原版服务器：50-80

精细调整：为不同稀有度设置不同阈值
极其稀有：5-15
比较稀有：20-40
一般稀有：50-80

🎯 服务器回放最佳实践
时间戳使用：违规时间戳可用于回放系统定位
位置验证：结合坐标信息快速找到违规地点
批量审查：一次查看多个违规记录的时间段
证据保存：违规记录自动保存，便于后续审查

🔍 监控策略
定期检查：每天使用 /figantixray check 查看数据
状态监控：使用 /figantixray status 了解整体情况
阈值优化：根据实际情况调整各方块阈值
违规审查：定期使用违规记录功能审查可疑玩家

🛠️ 维护建议
定期备份：重要操作前备份数据
日志审查：定期检查操作日志
数据清理：定期清理不需要的历史数据

🆘 常见问题
Q：为什么命令执行失败？
A：请检查是否用引号包裹了方块ID，例如：/figantixray addblock "minecraft:diamond_ore"

Q：如何重置忘记的密码？
A：手动编辑配置文件 config/figantixray/config.json 中的 deletePassword 字段

Q：模组不监控某些方块？
A：确保方块ID格式正确，并使用引号包裹：/figantixray addblock "方块ID"

Q：数据文件在哪里？
A：所有数据保存在 config/figantixray/data/ 目录下

Q：如何查看玩家的违规记录？
A：使用 /figantixray violationhistory <玩家名> 查看详细违规记录

Q：时间戳有什么用？
A：时间戳可用于服务器回放系统精确定位违规行为发生的时间点

⚠️ 重要提醒
记住这几点：
所有包含冒号的方块ID必须用引号包裹
自定义名称也建议使用引号包裹
密码不能包含空格
定期备份重要数据
违规记录自动保存，无需手动操作

成功的关键：
✅ 使用引号："minecraft:diamond_ore"
✅ 正确格式：/figantixray addblock "方块ID"
✅ 权限检查：确保有OP权限（等级2）
✅ 利用违规记录功能进行深度审查

💡 建议搭配 Server Replay 模组使用
