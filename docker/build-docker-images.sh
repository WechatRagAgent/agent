#!/bin/bash

# Docker镜像构建脚本
set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 版本标签
VERSION=${1:-latest}

echo -e "${BLUE}🚀 开始构建微信RAG智能问答系统Docker镜像...${NC}"
echo -e "${BLUE}版本标签: ${VERSION}${NC}"

# 检查Docker环境
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ 错误: Docker未安装或未在PATH中${NC}"
    exit 1
fi

# 检查是否在docker目录，并验证项目结构
if [ ! -f "../pom.xml" ] || [ ! -d "../agent-web" ] || [ ! -d "../agent-datasync" ]; then
    echo -e "${RED}❌ 错误: 请在项目的docker目录执行此脚本${NC}"
    echo -e "${YELLOW}当前目录: $(pwd)${NC}"
    echo -e "${YELLOW}请确保在 /path/to/wechat-rag-agent/docker/ 目录下执行${NC}"
    exit 1
fi

# 检查Dockerfile是否存在
if [ ! -f "../Dockerfile" ]; then
    echo -e "${RED}❌ 错误: 项目根目录中找不到Dockerfile${NC}"
    exit 1
fi

echo -e "${YELLOW}📦 构建微信RAG智能问答系统镜像...${NC}"
echo -e "${BLUE}构建上下文: $(realpath ..)${NC}"

# 切换到项目根目录进行构建
cd ..

docker build \
    -t wechat-rag-agent:${VERSION} \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    .

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Docker镜像构建成功${NC}"
else
    echo -e "${RED}❌ Docker镜像构建失败${NC}"
    exit 1
fi

echo -e "${GREEN}🎉 镜像构建完成！${NC}"

# 显示镜像信息
echo -e "${BLUE}📋 构建的镜像:${NC}"
docker images | grep "wechat-rag-agent" | grep "${VERSION}"

echo -e "${BLUE}💡 使用提示:${NC}"
echo -e "  查看镜像: docker images | grep wechat-rag-agent"
echo -e "  运行应用: ../run-docker.sh"
echo -e "  手动运行: docker run -p 8080:8080 wechat-rag-agent:${VERSION}"
echo -e "  在docker目录运行: ./run-docker.sh"