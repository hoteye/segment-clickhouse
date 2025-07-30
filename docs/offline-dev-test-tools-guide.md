# 离线开发测试环境工具链指南

## 1. 概述

本文档介绍支持开发环境到测试环境离线传输、自动化部署的工具链方案。

## 2. 核心工具推荐

### 2.1 离线传输打包工具

#### **Nexus Repository Manager OSS**
- **功能**：企业级仓库管理，支持离线包导出
- **特点**：
  - 支持 Maven、NPM、Docker、PyPI 等格式
  - 提供离线包导出功能
  - 支持增量同步
- **开源**：是

#### **JFrog Artifactory OSS**
- **功能**：通用制品仓库管理
- **特点**：
  - 支持多种包格式
  - 提供导出/导入功能
  - 支持元数据管理
- **开源**：社区版免费

### 2.2 加密传输工具

#### **VeraCrypt**
- **功能**：跨平台加密容器
- **特点**：
  - 创建加密的虚拟磁盘
  - 支持隐藏卷
  - AES-256、Serpent、Twofish 加密
- **使用场景**：创建加密U盘

#### **7-Zip**
- **功能**：压缩加密工具
- **特点**：
  - AES-256 加密
  - 支持分卷压缩
  - 跨平台
- **适用**：文件级加密

### 2.3 离线 CI/CD 工具

#### **Drone**
- **功能**：轻量级 CI/CD 平台
- **特点**：
  - 容器化运行
  - 支持离线部署
  - 配置简单（.drone.yml）
- **离线支持**：优秀

#### **Gitea**
- **功能**：轻量级 Git 服务
- **特点**：
  - 单二进制文件部署
  - 资源占用少
  - 内置 CI/CD（Gitea Actions）
- **适合**：测试环境 GitLab 替代

## 3. 完整工作流工具链

### 3.1 开发环境打包工具

```bash
#!/bin/bash
# offline-packager.sh - 离线打包工具

# 配置
EXPORT_DIR="/data/export/$(date +%Y%m%d_%H%M%S)"
ENCRYPT_KEY_FILE="/secure/keys/transfer.key"

# 创建导出目录
mkdir -p $EXPORT_DIR/{code,images,dependencies}

# 1. 导出代码（使用 git bundle）
echo "导出代码仓库..."
cd /path/to/project
git bundle create $EXPORT_DIR/code/project.bundle --all

# 2. 导出 Docker 镜像
echo "导出 Docker 镜像..."
docker save -o $EXPORT_DIR/images/app.tar myapp:latest
docker save -o $EXPORT_DIR/images/deps.tar mysql:8.0 redis:6.2

# 3. 导出依赖包
echo "导出 Maven 依赖..."
mvn dependency:copy-dependencies -DoutputDirectory=$EXPORT_DIR/dependencies/maven

# 4. 生成清单文件
cat > $EXPORT_DIR/manifest.json << EOF
{
  "version": "1.0",
  "created": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "contents": {
    "code": ["project.bundle"],
    "images": ["app.tar", "deps.tar"],
    "dependencies": {
      "maven": "$(ls $EXPORT_DIR/dependencies/maven | wc -l) files"
    }
  }
}
EOF

# 5. 加密打包
echo "加密打包..."
tar czf - -C $EXPORT_DIR . | openssl enc -aes-256-cbc -salt -pass file:$ENCRYPT_KEY_FILE -out $EXPORT_DIR.enc

echo "打包完成: $EXPORT_DIR.enc"
```

### 3.2 测试环境导入工具

```bash
#!/bin/bash
# offline-importer.sh - 离线导入工具

# 配置
IMPORT_FILE="$1"
DECRYPT_KEY_FILE="/secure/keys/transfer.key"
WORK_DIR="/data/import/$(date +%Y%m%d_%H%M%S)"

# 创建工作目录
mkdir -p $WORK_DIR

# 1. 解密解包
echo "解密文件..."
openssl enc -aes-256-cbc -d -pass file:$DECRYPT_KEY_FILE -in $IMPORT_FILE | tar xzf - -C $WORK_DIR

# 2. 验证清单
echo "验证清单..."
if [ ! -f "$WORK_DIR/manifest.json" ]; then
    echo "错误：找不到清单文件"
    exit 1
fi

# 3. 导入代码到 Gitea
echo "导入代码..."
cd /tmp
git clone $WORK_DIR/code/project.bundle imported-project
cd imported-project
git remote add gitea http://gitea.test.local/team/project.git
git push gitea --all
git push gitea --tags

# 4. 导入 Docker 镜像
echo "导入 Docker 镜像..."
for image in $WORK_DIR/images/*.tar; do
    docker load -i $image
done

# 5. 更新本地 Maven 仓库
echo "更新 Maven 依赖..."
cp -r $WORK_DIR/dependencies/maven/* /data/maven-repo/

# 6. 触发 CI/CD
echo "触发构建..."
curl -X POST http://drone.test.local/api/repos/team/project/builds \
     -H "Authorization: Bearer $DRONE_TOKEN"

echo "导入完成"
```

## 4. 开源的集成解决方案

### 4.1 **GoCD**
- **特点**：
  - 原生支持离线部署
  - 强大的 Pipeline 管理
  - 支持手动触发和审批
- **离线工作流**：
  1. 开发环境打包 artifacts
  2. 通过加密介质传输
  3. 测试环境导入并触发 Pipeline

### 4.2 **Harbor + Skopeo**
- **Harbor**：企业级镜像仓库
- **Skopeo**：镜像传输工具
- **离线同步示例**：
```bash
# 开发环境：导出镜像
skopeo copy --all docker://dev-harbor.local/myapp:latest dir:/export/myapp

# 测试环境：导入镜像  
skopeo copy --all dir:/import/myapp docker://test-harbor.local/myapp:latest
```

### 4.3 **Sonatype Nexus + Maven Wagon**
- **功能**：支持仓库间离线同步
- **工具**：nexus-repository-import-scripts
- **使用**：
```bash
# 导出仓库
python nexus3-repository-export.py --url http://nexus-dev --repo maven-releases

# 导入仓库
python nexus3-repository-import.py --url http://nexus-test --repo maven-releases
```

## 5. 自动化工作流示例

### 5.1 使用 Ansible 的离线部署

```yaml
# offline-deploy.yml
---
- name: 离线部署工作流
  hosts: test_environment
  vars:
    import_path: /media/usb/import
    
  tasks:
    - name: 验证导入文件
      stat:
        path: "{{ import_path }}/package.enc"
      register: import_file

    - name: 解密导入包
      shell: |
        openssl enc -aes-256-cbc -d \
          -in {{ import_path }}/package.enc \
          -out /tmp/package.tar.gz \
          -pass file:/secure/keys/transfer.key

    - name: 解压文件
      unarchive:
        src: /tmp/package.tar.gz
        dest: /tmp/import
        remote_src: yes

    - name: 导入 Git 仓库
      shell: |
        cd /tmp/import/code
        for bundle in *.bundle; do
          repo_name=$(basename $bundle .bundle)
          git clone $bundle /tmp/$repo_name
          cd /tmp/$repo_name
          git remote add origin http://gitea:3000/org/$repo_name.git
          git push origin --all
          git push origin --tags
        done

    - name: 导入 Docker 镜像
      docker_image:
        name: "{{ item }}"
        load_path: "/tmp/import/images/{{ item }}.tar"
        source: load
      with_items:
        - app
        - mysql
        - redis

    - name: 触发 CI/CD 构建
      uri:
        url: http://drone:8080/api/repos/org/project/builds
        method: POST
        headers:
          Authorization: "Bearer {{ drone_token }}"
```

### 5.2 使用 Python 的自动化脚本

```python
#!/usr/bin/env python3
# offline_transfer_tool.py

import os
import json
import subprocess
import hashlib
from datetime import datetime
from pathlib import Path

class OfflineTransferTool:
    def __init__(self, config_file):
        with open(config_file) as f:
            self.config = json.load(f)
    
    def create_package(self, projects):
        """创建离线传输包"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        export_dir = Path(f"/data/export/{timestamp}")
        export_dir.mkdir(parents=True)
        
        manifest = {
            "version": "1.0",
            "timestamp": timestamp,
            "projects": []
        }
        
        # 导出各个项目
        for project in projects:
            self._export_project(project, export_dir)
            manifest["projects"].append({
                "name": project["name"],
                "version": project["version"],
                "type": project["type"]
            })
        
        # 保存清单
        with open(export_dir / "manifest.json", "w") as f:
            json.dump(manifest, f, indent=2)
        
        # 加密打包
        package_file = self._encrypt_package(export_dir)
        return package_file
    
    def import_package(self, package_file):
        """导入离线包"""
        work_dir = Path("/tmp/import") / datetime.now().strftime("%Y%m%d_%H%M%S")
        work_dir.mkdir(parents=True)
        
        # 解密解包
        self._decrypt_package(package_file, work_dir)
        
        # 读取清单
        with open(work_dir / "manifest.json") as f:
            manifest = json.load(f)
        
        # 导入项目
        for project in manifest["projects"]:
            self._import_project(project, work_dir)
        
        # 触发构建
        self._trigger_builds(manifest["projects"])
    
    def _export_project(self, project, export_dir):
        """导出单个项目"""
        if project["type"] == "git":
            # Git 仓库导出
            bundle_file = export_dir / "code" / f"{project['name']}.bundle"
            bundle_file.parent.mkdir(exist_ok=True)
            subprocess.run([
                "git", "bundle", "create", str(bundle_file), "--all"
            ], cwd=project["path"])
        
        elif project["type"] == "docker":
            # Docker 镜像导出
            image_file = export_dir / "images" / f"{project['name']}.tar"
            image_file.parent.mkdir(exist_ok=True)
            subprocess.run([
                "docker", "save", "-o", str(image_file), project["image"]
            ])
    
    def _import_project(self, project, work_dir):
        """导入单个项目"""
        if project["type"] == "git":
            # 导入到 Gitea
            bundle_file = work_dir / "code" / f"{project['name']}.bundle"
            temp_repo = Path("/tmp") / project["name"]
            
            subprocess.run(["git", "clone", str(bundle_file), str(temp_repo)])
            subprocess.run([
                "git", "remote", "add", "gitea", 
                f"{self.config['gitea_url']}/{project['name']}.git"
            ], cwd=temp_repo)
            subprocess.run(["git", "push", "gitea", "--all"], cwd=temp_repo)
        
        elif project["type"] == "docker":
            # 导入 Docker 镜像
            image_file = work_dir / "images" / f"{project['name']}.tar"
            subprocess.run(["docker", "load", "-i", str(image_file)])
    
    def _encrypt_package(self, export_dir):
        """加密打包"""
        tar_file = f"{export_dir}.tar.gz"
        enc_file = f"{export_dir}.enc"
        
        # 创建 tar 包
        subprocess.run([
            "tar", "czf", tar_file, "-C", str(export_dir.parent), export_dir.name
        ])
        
        # 加密
        subprocess.run([
            "openssl", "enc", "-aes-256-cbc", "-salt",
            "-in", tar_file, "-out", enc_file,
            "-pass", f"file:{self.config['encryption_key_file']}"
        ])
        
        # 清理临时文件
        os.remove(tar_file)
        return enc_file
    
    def _decrypt_package(self, package_file, work_dir):
        """解密解包"""
        tar_file = work_dir / "package.tar.gz"
        
        # 解密
        subprocess.run([
            "openssl", "enc", "-aes-256-cbc", "-d",
            "-in", package_file, "-out", str(tar_file),
            "-pass", f"file:{self.config['decryption_key_file']}"
        ])
        
        # 解包
        subprocess.run([
            "tar", "xzf", str(tar_file), "-C", str(work_dir)
        ])
        
        # 清理
        tar_file.unlink()
    
    def _trigger_builds(self, projects):
        """触发 CI/CD 构建"""
        for project in projects:
            # 触发 Drone 构建
            subprocess.run([
                "curl", "-X", "POST",
                f"{self.config['drone_url']}/api/repos/{project['name']}/builds",
                "-H", f"Authorization: Bearer {self.config['drone_token']}"
            ])

# 使用示例
if __name__ == "__main__":
    tool = OfflineTransferTool("config.json")
    
    # 开发环境：打包
    projects = [
        {"name": "myapp", "version": "1.0", "type": "git", "path": "/code/myapp"},
        {"name": "myapp", "version": "1.0", "type": "docker", "image": "myapp:1.0"}
    ]
    package = tool.create_package(projects)
    print(f"创建包: {package}")
    
    # 测试环境：导入
    # tool.import_package("/media/usb/20240125_143022.enc")
```

## 6. 商业工具选择（如果预算允许）

### 6.1 **JFrog Artifactory Pro**
- 原生支持离线同步
- 提供 CLI 工具
- 支持增量同步

### 6.2 **GitLab EE**
- Geo 功能支持离线同步
- 内置 CI/CD
- 支持离线 Runner

### 6.3 **TeamCity**
- 支持离线 Agent
- Artifact 依赖管理
- 支持手动触发

## 7. 最佳实践建议

### 7.1 安全性
- 使用硬件加密U盘
- 实施双人复核制度
- 记录所有传输日志

### 7.2 自动化
- 尽可能自动化所有步骤
- 使用脚本减少人为错误
- 实施自动化测试

### 7.3 版本管理
- 严格的版本号管理
- 保留传输历史记录
- 支持回滚机制

## 8. 故障排查

### 8.1 常见问题
1. **加密U盘无法识别**
   - 检查U盘驱动
   - 验证加密软件版本

2. **导入失败**
   - 检查文件完整性
   - 验证权限设置
   - 查看详细日志

3. **构建触发失败**
   - 验证 CI/CD 服务状态
   - 检查认证 Token
   - 确认网络连接

### 8.2 应急方案
- 准备备用U盘
- 保留最近3次的传输包
- 建立手动导入流程

通过这套工具链，可以实现开发环境到测试环境的自动化离线部署，大大提高效率并减少人为错误。