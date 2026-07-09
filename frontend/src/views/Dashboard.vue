<template>
  <div class="page-container">
    <!-- 欢迎横幅 -->
    <div class="hero-banner">
      <div class="hero-content">
        <h1 class="hero-title">欢迎使用 Z-Brain 智能知识库</h1>
        <p class="hero-desc">基于 RAG 架构的知识管理与智能问答平台，支持多格式文档解析、语义分块、多路召回与精准问答</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" round @click="$router.push('/chat')">
            <el-icon><ChatDotRound /></el-icon>
            开始问答
          </el-button>
          <el-button size="large" round @click="$router.push('/knowledge-bases')">
            <el-icon><Plus /></el-icon>
            创建知识库
          </el-button>
        </div>
      </div>
      <div class="hero-decoration">
        <div class="deco-circle deco-1"></div>
        <div class="deco-circle deco-2"></div>
        <div class="deco-circle deco-3"></div>
      </div>
    </div>

    <!-- 数据概览卡片 -->
    <div class="stat-grid">
      <div class="stat-card" v-for="card in statCards" :key="card.label">
        <div class="stat-card-icon" :style="{ background: card.gradient }">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-card-info">
          <div class="stat-card-value">{{ card.value }}</div>
          <div class="stat-card-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <!-- 功能模块 -->
    <div class="section-header">
      <h2 class="section-title">功能模块</h2>
    </div>
    <div class="feature-grid">
      <div
        class="feature-card"
        v-for="feature in features"
        :key="feature.title"
        @click="$router.push(feature.path)"
      >
        <div class="feature-icon" :style="{ background: feature.gradient }">
          <el-icon><component :is="feature.icon" /></el-icon>
        </div>
        <div class="feature-info">
          <h3 class="feature-title">{{ feature.title }}</h3>
          <p class="feature-desc">{{ feature.desc }}</p>
        </div>
        <el-icon class="feature-arrow"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 技术架构概览 -->
    <div class="section-header">
      <h2 class="section-title">技术架构</h2>
    </div>
    <div class="arch-grid">
      <div class="arch-card" v-for="arch in architectures" :key="arch.title">
        <div class="arch-number">{{ arch.num }}</div>
        <h3 class="arch-title">{{ arch.title }}</h3>
        <p class="arch-desc">{{ arch.desc }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listKnowledgeBases } from '@/api/knowledgeBase'
import { listDocuments } from '@/api/document'
import { listPromptTemplates } from '@/api/promptTemplate'

const statCards = ref([
  { label: '知识库', value: 0, icon: 'Collection', gradient: 'linear-gradient(135deg, #6366f1, #8b5cf6)' },
  { label: '文档', value: 0, icon: 'Document', gradient: 'linear-gradient(135deg, #06b6d4, #3b82f6)' },
  { label: '提示词模板', value: 0, icon: 'EditPen', gradient: 'linear-gradient(135deg, #f59e0b, #ef4444)' },
  { label: '智能问答', value: 'Ready', icon: 'ChatDotRound', gradient: 'linear-gradient(135deg, #10b981, #06b6d4)' }
])

const features = [
  { title: '知识库管理', desc: '创建、配置和管理知识库，支持分类与分块策略配置', icon: 'Collection', gradient: 'linear-gradient(135deg, #6366f1, #8b5cf6)', path: '/knowledge-bases' },
  { title: '文档管理', desc: '上传多种格式文档，实时跟踪解析进度，支持向量化触发', icon: 'Document', gradient: 'linear-gradient(135deg, #06b6d4, #3b82f6)', path: '/documents' },
  { title: '分块审核', desc: '人工审核分块结果，支持合并、拆分、编辑与父子关系调整', icon: 'Files', gradient: 'linear-gradient(135deg, #f59e0b, #f97316)', path: '/documents' },
  { title: '智能问答', desc: '基于 RAG 的流式问答，支持 Query 改写与引用溯源', icon: 'ChatDotRound', gradient: 'linear-gradient(135deg, #10b981, #06b6d4)', path: '/chat' },
  { title: '提示词模板', desc: '灵活配置系统提示词与用户提示词，支持知识库级别定制', icon: 'EditPen', gradient: 'linear-gradient(135deg, #ec4899, #f43f5e)', path: '/prompt-templates' }
]

const architectures = [
  { num: '01', title: '文档解析', desc: 'LlamaIndex Cloud 智能解析 PDF，Tika 解析其余格式，支持版面与表格识别' },
  { num: '02', title: '语义分块', desc: 'Markdown 语义边界父块 + 递归字符子块，父子双层索引结构' },
  { num: '03', title: '多路召回', desc: '向量检索 + 全文检索 + 模糊匹配，RRF 融合排序后 Rerank 精排' },
  { num: '04', title: 'Query 改写', desc: '利用 LLM 改写查询增强检索，多轮对话指代消解提升上下文理解效果' }
]

onMounted(async () => {
  try {
    const [kbRes, docRes, ptRes] = await Promise.all([
      listKnowledgeBases({ pageNum: 1, pageSize: 1 }),
      listDocuments({ pageNum: 1, pageSize: 1 }),
      listPromptTemplates()
    ])
    statCards.value[0].value = kbRes.data?.total || 0
    statCards.value[1].value = docRes.data?.total || 0
    statCards.value[2].value = ptRes.data?.length || 0
  } catch {
    // 静默处理
  }
})
</script>

<style scoped>
/* ==================== Hero 横幅 ==================== */
.hero-banner {
  position: relative;
  background: var(--primary-gradient);
  border-radius: var(--radius-xl);
  padding: 48px 40px;
  overflow: hidden;
  margin-bottom: 32px;
}
.hero-content {
  position: relative;
  z-index: 2;
  max-width: 600px;
}
.hero-title {
  font-size: 32px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -1px;
  margin-bottom: 12px;
}
.hero-desc {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.85);
  line-height: 1.7;
  margin-bottom: 28px;
}
.hero-actions {
  display: flex;
  gap: 12px;
}
.hero-actions .el-button--primary {
  background: #fff;
  color: var(--primary);
  border-color: #fff;
}
.hero-actions .el-button--primary:hover {
  background: rgba(255, 255, 255, 0.9);
}
.hero-actions .el-button:not(.el-button--primary) {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.3);
}
.hero-actions .el-button:not(.el-button--primary):hover {
  background: rgba(255, 255, 255, 0.25);
}

.hero-decoration {
  position: absolute;
  top: 0;
  right: 0;
  width: 400px;
  height: 100%;
  overflow: hidden;
}
.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}
.deco-1 { width: 200px; height: 200px; top: -40px; right: -40px; }
.deco-2 { width: 140px; height: 140px; bottom: -30px; right: 80px; }
.deco-3 { width: 80px; height: 80px; top: 40%; right: 200px; background: rgba(255, 255, 255, 0.08); }

/* ==================== 数据统计卡片 ==================== */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 40px;
}
.stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  transition: all 0.3s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.stat-card-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
  flex-shrink: 0;
}
.stat-card-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}
.stat-card-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

/* ==================== 区块标题 ==================== */
.section-header {
  margin-bottom: 20px;
}
.section-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.3px;
}

/* ==================== 功能模块卡片 ==================== */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 40px;
}
.feature-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 24px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  cursor: pointer;
  transition: all 0.3s ease;
}
.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-lighter);
}
.feature-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  flex-shrink: 0;
}
.feature-info {
  flex: 1;
}
.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.feature-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}
.feature-arrow {
  color: var(--text-placeholder);
  font-size: 16px;
  transition: all 0.3s;
}
.feature-card:hover .feature-arrow {
  color: var(--primary);
  transform: translateX(4px);
}

/* ==================== 技术架构 ==================== */
.arch-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.arch-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 28px 24px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  position: relative;
  overflow: hidden;
}
.arch-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: var(--primary-gradient);
  opacity: 0;
  transition: opacity 0.3s;
}
.arch-card:hover::before {
  opacity: 1;
}
.arch-number {
  font-size: 28px;
  font-weight: 800;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 12px;
}
.arch-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}
.arch-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
}
</style>
