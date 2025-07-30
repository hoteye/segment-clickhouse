#!/usr/bin/env python3
"""
离线打包传输工具 - 完整实现
支持开发环境打包、加密传输、测试环境自动导入部署
"""

import os
import sys
import json
import yaml
import shutil
import hashlib
import tarfile
import logging
import argparse
import subprocess
from pathlib import Path
from datetime import datetime
from typing import Dict, List, Optional
import requests
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
import base64

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class OfflineBundleTool:
    """离线打包传输工具主类"""
    
    def __init__(self, config_path: str):
        """初始化工具
        
        Args:
            config_path: 配置文件路径
        """
        self.config = self._load_config(config_path)
        self.work_dir = Path(self.config['work_dir'])
        self.work_dir.mkdir(parents=True, exist_ok=True)
        
    def _load_config(self, config_path: str) -> Dict:
        """加载配置文件"""
        with open(config_path, 'r') as f:
            if config_path.endswith('.yaml') or config_path.endswith('.yml'):
                return yaml.safe_load(f)
            else:
                return json.load(f)
    
    def _get_encryption_key(self, password: str, salt: bytes) -> bytes:
        """生成加密密钥"""
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=32,
            salt=salt,
            iterations=100000,
        )
        key = base64.urlsafe_b64encode(kdf.derive(password.encode()))
        return key
    
    def export_bundle(self, bundle_name: str, projects: List[Dict]) -> str:
        """导出打包
        
        Args:
            bundle_name: 包名称
            projects: 项目列表
            
        Returns:
            加密包文件路径
        """
        logger.info(f"开始创建离线包: {bundle_name}")
        
        # 创建导出目录
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        export_dir = self.work_dir / f"export_{timestamp}"
        export_dir.mkdir(parents=True)
        
        # 创建子目录
        (export_dir / "code").mkdir()
        (export_dir / "images").mkdir()
        (export_dir / "dependencies").mkdir()
        (export_dir / "configs").mkdir()
        
        # 创建清单
        manifest = {
            "bundle_name": bundle_name,
            "timestamp": timestamp,
            "version": "1.0",
            "contents": {
                "projects": [],
                "images": [],
                "dependencies": {},
                "configs": []
            }
        }
        
        # 导出各项目
        for project in projects:
            logger.info(f"导出项目: {project['name']}")
            self._export_project(project, export_dir, manifest)
        
        # 导出依赖
        self._export_dependencies(export_dir, manifest)
        
        # 导出配置
        self._export_configs(export_dir, manifest)
        
        # 保存清单
        manifest_path = export_dir / "manifest.json"
        with open(manifest_path, 'w') as f:
            json.dump(manifest, f, indent=2)
        
        # 创建校验和
        self._create_checksums(export_dir)
        
        # 打包加密
        bundle_file = self._create_encrypted_bundle(export_dir, bundle_name)
        
        # 清理临时目录
        shutil.rmtree(export_dir)
        
        logger.info(f"离线包创建完成: {bundle_file}")
        return bundle_file
    
    def _export_project(self, project: Dict, export_dir: Path, manifest: Dict):
        """导出单个项目"""
        project_type = project.get('type', 'git')
        
        if project_type == 'git':
            # Git 仓库导出
            bundle_file = export_dir / "code" / f"{project['name']}.bundle"
            cmd = ['git', 'bundle', 'create', str(bundle_file), '--all']
            subprocess.run(cmd, cwd=project['path'], check=True)
            
            # 导出最新标签信息
            tags_file = export_dir / "code" / f"{project['name']}.tags"
            cmd = ['git', 'tag', '-l', '--sort=-version:refname']
            result = subprocess.run(cmd, cwd=project['path'], 
                                  capture_output=True, text=True)
            tags_file.write_text(result.stdout)
            
            manifest['contents']['projects'].append({
                'name': project['name'],
                'type': 'git',
                'bundle': f"{project['name']}.bundle",
                'tags': f"{project['name']}.tags"
            })
            
        elif project_type == 'docker':
            # Docker 镜像导出
            image_name = project['image']
            image_file = export_dir / "images" / f"{project['name']}.tar"
            
            cmd = ['docker', 'save', '-o', str(image_file), image_name]
            subprocess.run(cmd, check=True)
            
            manifest['contents']['images'].append({
                'name': project['name'],
                'image': image_name,
                'file': f"{project['name']}.tar"
            })
    
    def _export_dependencies(self, export_dir: Path, manifest: Dict):
        """导出依赖包"""
        deps_dir = export_dir / "dependencies"
        
        # Maven 依赖
        if self.config.get('maven', {}).get('enabled'):
            maven_dir = deps_dir / "maven"
            maven_dir.mkdir()
            
            # 复制本地仓库
            local_repo = Path(self.config['maven']['local_repo'])
            if local_repo.exists():
                logger.info("导出 Maven 依赖...")
                # 只复制项目相关的依赖
                for pom_file in Path('.').glob('**/pom.xml'):
                    cmd = ['mvn', 'dependency:copy-dependencies', 
                          f'-DoutputDirectory={maven_dir}',
                          '-DincludeScope=compile']
                    subprocess.run(cmd, cwd=pom_file.parent, check=True)
                
                manifest['contents']['dependencies']['maven'] = True
        
        # NPM 依赖
        if self.config.get('npm', {}).get('enabled'):
            npm_dir = deps_dir / "npm"
            npm_dir.mkdir()
            
            logger.info("导出 NPM 依赖...")
            for package_file in Path('.').glob('**/package.json'):
                project_name = package_file.parent.name
                project_npm_dir = npm_dir / project_name
                
                # 使用 npm pack 导出依赖
                cmd = ['npm', 'pack', '--pack-destination', str(project_npm_dir)]
                subprocess.run(cmd, cwd=package_file.parent, check=True)
                
            manifest['contents']['dependencies']['npm'] = True
    
    def _export_configs(self, export_dir: Path, manifest: Dict):
        """导出配置文件"""
        configs_dir = export_dir / "configs"
        
        # 导出环境配置模板
        for config_file in self.config.get('export_configs', []):
            src = Path(config_file)
            if src.exists():
                dst = configs_dir / src.name
                shutil.copy2(src, dst)
                manifest['contents']['configs'].append(src.name)
    
    def _create_checksums(self, export_dir: Path):
        """创建校验和文件"""
        checksums = {}
        
        for file_path in export_dir.rglob('*'):
            if file_path.is_file():
                relative_path = file_path.relative_to(export_dir)
                checksums[str(relative_path)] = self._calculate_checksum(file_path)
        
        checksum_file = export_dir / "checksums.json"
        with open(checksum_file, 'w') as f:
            json.dump(checksums, f, indent=2)
    
    def _calculate_checksum(self, file_path: Path) -> str:
        """计算文件校验和"""
        sha256_hash = hashlib.sha256()
        with open(file_path, "rb") as f:
            for byte_block in iter(lambda: f.read(4096), b""):
                sha256_hash.update(byte_block)
        return sha256_hash.hexdigest()
    
    def _create_encrypted_bundle(self, export_dir: Path, bundle_name: str) -> str:
        """创建加密包"""
        # 创建 tar 包
        tar_file = self.work_dir / f"{bundle_name}.tar.gz"
        with tarfile.open(tar_file, "w:gz") as tar:
            tar.add(export_dir, arcname=bundle_name)
        
        # 加密
        encrypted_file = self.work_dir / f"{bundle_name}.enc"
        
        # 生成随机盐
        salt = os.urandom(16)
        
        # 获取密码
        password = self.config.get('encryption_password', 'default_password')
        key = self._get_encryption_key(password, salt)
        
        # 加密文件
        fernet = Fernet(key)
        
        with open(tar_file, 'rb') as f:
            file_data = f.read()
        
        encrypted_data = salt + fernet.encrypt(file_data)
        
        with open(encrypted_file, 'wb') as f:
            f.write(encrypted_data)
        
        # 删除临时 tar 文件
        tar_file.unlink()
        
        return str(encrypted_file)
    
    def import_bundle(self, bundle_file: str) -> bool:
        """导入并部署离线包
        
        Args:
            bundle_file: 加密包文件路径
            
        Returns:
            是否成功
        """
        logger.info(f"开始导入离线包: {bundle_file}")
        
        # 创建工作目录
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        import_dir = self.work_dir / f"import_{timestamp}"
        import_dir.mkdir(parents=True)
        
        try:
            # 解密解包
            self._decrypt_bundle(bundle_file, import_dir)
            
            # 读取清单
            manifest_path = list(import_dir.glob('*/manifest.json'))[0]
            with open(manifest_path, 'r') as f:
                manifest = json.load(f)
            
            bundle_dir = manifest_path.parent
            
            # 验证校验和
            if not self._verify_checksums(bundle_dir):
                raise Exception("校验和验证失败")
            
            # 导入项目
            self._import_projects(bundle_dir, manifest)
            
            # 导入镜像
            self._import_images(bundle_dir, manifest)
            
            # 导入依赖
            self._import_dependencies(bundle_dir, manifest)
            
            # 部署配置
            self._deploy_configs(bundle_dir, manifest)
            
            # 触发 CI/CD
            self._trigger_builds(manifest)
            
            logger.info("离线包导入完成")
            return True
            
        except Exception as e:
            logger.error(f"导入失败: {e}")
            return False
        finally:
            # 清理临时目录
            if import_dir.exists():
                shutil.rmtree(import_dir)
    
    def _decrypt_bundle(self, bundle_file: str, import_dir: Path):
        """解密包文件"""
        with open(bundle_file, 'rb') as f:
            encrypted_data = f.read()
        
        # 提取盐
        salt = encrypted_data[:16]
        encrypted_content = encrypted_data[16:]
        
        # 生成密钥
        password = self.config.get('encryption_password', 'default_password')
        key = self._get_encryption_key(password, salt)
        
        # 解密
        fernet = Fernet(key)
        decrypted_data = fernet.decrypt(encrypted_content)
        
        # 保存为临时 tar 文件
        tar_file = import_dir / "bundle.tar.gz"
        with open(tar_file, 'wb') as f:
            f.write(decrypted_data)
        
        # 解压
        with tarfile.open(tar_file, "r:gz") as tar:
            tar.extractall(import_dir)
        
        # 删除临时文件
        tar_file.unlink()
    
    def _verify_checksums(self, bundle_dir: Path) -> bool:
        """验证校验和"""
        checksum_file = bundle_dir / "checksums.json"
        with open(checksum_file, 'r') as f:
            expected_checksums = json.load(f)
        
        for file_path, expected_checksum in expected_checksums.items():
            if file_path == "checksums.json":
                continue
                
            full_path = bundle_dir / file_path
            if not full_path.exists():
                logger.error(f"文件不存在: {file_path}")
                return False
            
            actual_checksum = self._calculate_checksum(full_path)
            if actual_checksum != expected_checksum:
                logger.error(f"校验和不匹配: {file_path}")
                return False
        
        logger.info("校验和验证通过")
        return True
    
    def _import_projects(self, bundle_dir: Path, manifest: Dict):
        """导入项目代码"""
        for project in manifest['contents']['projects']:
            if project['type'] == 'git':
                logger.info(f"导入 Git 项目: {project['name']}")
                
                # 克隆 bundle
                bundle_file = bundle_dir / "code" / project['bundle']
                temp_repo = self.work_dir / f"temp_{project['name']}"
                
                cmd = ['git', 'clone', str(bundle_file), str(temp_repo)]
                subprocess.run(cmd, check=True)
                
                # 推送到测试环境 Git 服务器
                git_url = f"{self.config['test_git_url']}/{project['name']}.git"
                cmd = ['git', 'remote', 'add', 'test', git_url]
                subprocess.run(cmd, cwd=temp_repo, check=True)
                
                cmd = ['git', 'push', 'test', '--all', '--force']
                subprocess.run(cmd, cwd=temp_repo, check=True)
                
                cmd = ['git', 'push', 'test', '--tags', '--force']
                subprocess.run(cmd, cwd=temp_repo, check=True)
                
                # 清理临时仓库
                shutil.rmtree(temp_repo)
    
    def _import_images(self, bundle_dir: Path, manifest: Dict):
        """导入 Docker 镜像"""
        for image_info in manifest['contents']['images']:
            logger.info(f"导入 Docker 镜像: {image_info['name']}")
            
            image_file = bundle_dir / "images" / image_info['file']
            cmd = ['docker', 'load', '-i', str(image_file)]
            subprocess.run(cmd, check=True)
            
            # 如果配置了私有仓库，推送镜像
            if self.config.get('test_docker_registry'):
                old_tag = image_info['image']
                new_tag = f"{self.config['test_docker_registry']}/{old_tag}"
                
                cmd = ['docker', 'tag', old_tag, new_tag]
                subprocess.run(cmd, check=True)
                
                cmd = ['docker', 'push', new_tag]
                subprocess.run(cmd, check=True)
    
    def _import_dependencies(self, bundle_dir: Path, manifest: Dict):
        """导入依赖包"""
        deps = manifest['contents']['dependencies']
        
        # Maven 依赖
        if deps.get('maven'):
            logger.info("导入 Maven 依赖...")
            maven_dir = bundle_dir / "dependencies" / "maven"
            
            # 复制到本地仓库
            local_repo = Path(self.config['test_maven_repo'])
            local_repo.mkdir(parents=True, exist_ok=True)
            
            for jar_file in maven_dir.glob("*.jar"):
                shutil.copy2(jar_file, local_repo)
        
        # NPM 依赖
        if deps.get('npm'):
            logger.info("导入 NPM 依赖...")
            npm_dir = bundle_dir / "dependencies" / "npm"
            
            # 如果配置了 Verdaccio
            if self.config.get('test_npm_registry'):
                for tgz_file in npm_dir.rglob("*.tgz"):
                    cmd = ['npm', 'publish', str(tgz_file), 
                          '--registry', self.config['test_npm_registry']]
                    subprocess.run(cmd, check=True)
    
    def _deploy_configs(self, bundle_dir: Path, manifest: Dict):
        """部署配置文件"""
        configs_dir = bundle_dir / "configs"
        
        for config_file in manifest['contents']['configs']:
            src = configs_dir / config_file
            if src.exists():
                # 根据配置部署到指定位置
                logger.info(f"部署配置文件: {config_file}")
                # 这里可以根据实际需求处理配置文件
    
    def _trigger_builds(self, manifest: Dict):
        """触发 CI/CD 构建"""
        if self.config.get('test_ci_url'):
            for project in manifest['contents']['projects']:
                logger.info(f"触发构建: {project['name']}")
                
                # 示例：触发 Jenkins 构建
                if 'jenkins' in self.config['test_ci_url']:
                    url = f"{self.config['test_ci_url']}/job/{project['name']}/build"
                    headers = {'Authorization': f"Bearer {self.config.get('ci_token', '')}"}
                    
                    try:
                        response = requests.post(url, headers=headers)
                        if response.status_code == 201:
                            logger.info(f"构建触发成功: {project['name']}")
                        else:
                            logger.warning(f"构建触发失败: {project['name']}")
                    except Exception as e:
                        logger.error(f"触发构建异常: {e}")


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='离线打包传输工具')
    parser.add_argument('action', choices=['export', 'import'], 
                       help='操作类型')
    parser.add_argument('-c', '--config', required=True, 
                       help='配置文件路径')
    parser.add_argument('-n', '--name', help='包名称（导出时使用）')
    parser.add_argument('-f', '--file', help='包文件路径（导入时使用）')
    parser.add_argument('-p', '--projects', help='项目配置文件（导出时使用）')
    
    args = parser.parse_args()
    
    # 创建工具实例
    tool = OfflineBundleTool(args.config)
    
    if args.action == 'export':
        if not args.name or not args.projects:
            parser.error('导出操作需要指定 --name 和 --projects')
        
        # 加载项目配置
        with open(args.projects, 'r') as f:
            projects = json.load(f)
        
        # 执行导出
        bundle_file = tool.export_bundle(args.name, projects)
        print(f"导出完成: {bundle_file}")
        
    elif args.action == 'import':
        if not args.file:
            parser.error('导入操作需要指定 --file')
        
        # 执行导入
        success = tool.import_bundle(args.file)
        if success:
            print("导入成功")
        else:
            print("导入失败", file=sys.stderr)
            sys.exit(1)


if __name__ == '__main__':
    main()