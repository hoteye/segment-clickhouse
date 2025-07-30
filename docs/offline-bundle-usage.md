# 离线打包工具使用指南

## 1. 工具安装

### 1.1 依赖安装

```bash
# Python 依赖
pip install pyyaml cryptography requests

# 系统工具
# CentOS/RHEL
yum install -y git docker python3 python3-pip

# Ubuntu/Debian  
apt-get install -y git docker.io python3 python3-pip
```

### 1.2 配置环境变量

```bash
# 设置加密密码
export BUNDLE_PASSWORD="your-strong-password-here"

# 设置 CI/CD Token（测试环境使用）
export JENKINS_TOKEN="your-jenkins-token"
```

## 2. 开发环境使用（打包导出）

### 2.1 准备配置文件

1. 编辑 `offline-bundle-config.yaml`，配置开发环境参数
2. 编辑 `offline-bundle-projects.json`，指定要打包的项目

### 2.2 执行打包

```bash
# 基本用法
python3 offline-bundle-tool.py export \
  -c offline-bundle-config.yaml \
  -n release-v1.0.5 \
  -p offline-bundle-projects.json

# 输出示例
2024-01-25 14:30:22 - INFO - 开始创建离线包: release-v1.0.5
2024-01-25 14:30:23 - INFO - 导出项目: segment-alarm-clickhouse
2024-01-25 14:30:25 - INFO - 导出项目: segment-alarm-flink
2024-01-25 14:30:27 - INFO - 导出 Docker 镜像: segment-alarm-app
2024-01-25 14:30:45 - INFO - 导出 Maven 依赖...
2024-01-25 14:31:02 - INFO - 离线包创建完成: /data/offline-bundle/release-v1.0.5.enc
导出完成: /data/offline-bundle/release-v1.0.5.enc
```

### 2.3 传输到U盘

```bash
# 挂载加密U盘
mount /dev/sdb1 /mnt/secure-usb

# 复制加密包
cp /data/offline-bundle/release-v1.0.5.enc /mnt/secure-usb/

# 生成传输记录
cat > /mnt/secure-usb/transfer-record.txt << EOF
Transfer Record
Date: $(date)
Bundle: release-v1.0.5.enc
Size: $(ls -lh /mnt/secure-usb/release-v1.0.5.enc | awk '{print $5}')
SHA256: $(sha256sum /mnt/secure-usb/release-v1.0.5.enc | awk '{print $1}')
Operator: $(whoami)
EOF

# 安全卸载
umount /mnt/secure-usb
```

## 3. 测试环境使用（导入部署）

### 3.1 准备环境

确保测试环境已安装：
- Gitea (Git 服务器)
- Harbor (Docker 镜像仓库) 
- Jenkins (CI/CD)
- Nexus/Verdaccio (依赖仓库)

### 3.2 从U盘导入

```bash
# 挂载U盘
mount /dev/sdb1 /mnt/secure-usb

# 验证文件
sha256sum -c /mnt/secure-usb/transfer-record.txt

# 复制到本地
cp /mnt/secure-usb/release-v1.0.5.enc /data/import/

# 卸载U盘
umount /mnt/secure-usb
```

### 3.3 执行导入

```bash
# 设置环境变量（与开发环境相同的密码）
export BUNDLE_PASSWORD="your-strong-password-here"

# 执行导入
python3 offline-bundle-tool.py import \
  -c offline-bundle-config.yaml \
  -f /data/import/release-v1.0.5.enc

# 输出示例
2024-01-25 15:00:10 - INFO - 开始导入离线包: /data/import/release-v1.0.5.enc
2024-01-25 15:00:11 - INFO - 校验和验证通过
2024-01-25 15:00:12 - INFO - 导入 Git 项目: segment-alarm-clickhouse
2024-01-25 15:00:15 - INFO - 导入 Git 项目: segment-alarm-flink
2024-01-25 15:00:18 - INFO - 导入 Docker 镜像: segment-alarm-app
2024-01-25 15:00:35 - INFO - 导入 Maven 依赖...
2024-01-25 15:00:52 - INFO - 触发构建: segment-alarm-clickhouse
2024-01-25 15:00:53 - INFO - 构建触发成功: segment-alarm-clickhouse
2024-01-25 15:00:54 - INFO - 离线包导入完成
导入成功
```

## 4. 高级用法

### 4.1 选择性打包

创建自定义项目列表文件：

```json
{
  "projects": [
    {
      "name": "hotfix-module",
      "type": "git",
      "path": "/home/dev/projects/hotfix-module"
    },
    {
      "name": "hotfix-image",
      "type": "docker",
      "image": "hotfix:emergency-fix"
    }
  ]
}
```

### 4.2 自动化脚本

创建 `auto-bundle.sh`：

```bash
#!/bin/bash
# 自动化打包脚本

# 配置
BUNDLE_NAME="release-$(date +%Y%m%d-%H%M%S)"
CONFIG_FILE="offline-bundle-config.yaml"
PROJECTS_FILE="offline-bundle-projects.json"

# 更新代码
echo "更新代码..."
for project in $(jq -r '.projects[] | select(.type=="git") | .path' $PROJECTS_FILE); do
    echo "更新: $project"
    cd $project
    git pull
done

# 构建 Docker 镜像
echo "构建镜像..."
cd /home/dev/projects/segment-alarm-clickhouse
mvn clean package
docker build -t segment-alarm-app:latest .

# 执行打包
echo "创建离线包..."
python3 offline-bundle-tool.py export \
    -c $CONFIG_FILE \
    -n $BUNDLE_NAME \
    -p $PROJECTS_FILE

echo "打包完成: $BUNDLE_NAME"
```

### 4.3 集成到 CI/CD

Jenkins Pipeline 示例：

```groovy
pipeline {
    agent any
    
    triggers {
        cron('0 2 * * *') // 每天凌晨2点
    }
    
    stages {
        stage('Prepare') {
            steps {
                sh 'git pull'
                sh 'mvn clean package'
                sh 'docker build -t segment-alarm-app:${BUILD_NUMBER} .'
            }
        }
        
        stage('Bundle') {
            steps {
                sh '''
                    python3 offline-bundle-tool.py export \
                        -c offline-bundle-config.yaml \
                        -n release-${BUILD_NUMBER} \
                        -p offline-bundle-projects.json
                '''
            }
        }
        
        stage('Archive') {
            steps {
                archiveArtifacts artifacts: '**/release-*.enc'
            }
        }
    }
}
```

## 5. 故障排查

### 5.1 常见问题

**问题1：加密密码错误**
```
ERROR - 解密失败: Invalid token
```
解决：确保开发和测试环境使用相同的 `BUNDLE_PASSWORD`

**问题2：Git 推送失败**
```
ERROR - Git 推送失败: Permission denied
```
解决：
- 检查 Gitea 用户权限
- 确认 Git 仓库已创建
- 验证网络连接

**问题3：Docker 镜像导入失败**
```
ERROR - Docker load 失败: no space left on device
```
解决：
- 清理 Docker 无用镜像：`docker image prune -a`
- 检查磁盘空间：`df -h`

### 5.2 日志分析

工具会在工作目录生成详细日志：

```bash
# 查看日志
tail -f /data/offline-bundle/bundle.log

# 过滤错误
grep ERROR /data/offline-bundle/bundle.log
```

### 5.3 手动恢复

如果自动导入失败，可以手动恢复：

```bash
# 1. 手动解密
openssl enc -aes-256-cbc -d \
    -in release-v1.0.5.enc \
    -out release-v1.0.5.tar.gz \
    -pass pass:$BUNDLE_PASSWORD

# 2. 解压
tar xzf release-v1.0.5.tar.gz

# 3. 手动导入 Git
cd release-v1.0.5/code
git clone segment-alarm-clickhouse.bundle temp-repo
cd temp-repo
git remote add gitea http://gitea.test.local:3000/team/segment-alarm-clickhouse.git
git push gitea --all

# 4. 手动导入镜像
docker load -i release-v1.0.5/images/segment-alarm-app.tar
```

## 6. 安全建议

1. **密码管理**
   - 使用强密码（至少16位）
   - 定期更换密码
   - 使用密码管理器

2. **U盘安全**
   - 使用硬件加密U盘
   - 专人专用
   - 定期格式化

3. **审计追踪**
   - 记录所有传输操作
   - 保留传输日志
   - 定期审查

4. **权限控制**
   - 最小权限原则
   - 分离打包和导入权限
   - 定期权限审查

通过这个完整的离线打包工具，可以安全、高效地在物理隔离的开发和测试环境之间传输代码和依赖。