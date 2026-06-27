<template>
  <div class="chunk-review-page">
    <!-- 顶部操作栏 -->
    <div class="review-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" @click="goBack" round plain>返回</el-button>
        <span class="page-title">分块审核工作台</span>
        <el-tag v-if="document" effect="plain" round>{{ document.fileName }}</el-tag>
      </div>
      <el-button type="success" :icon="Check" @click="handleSubmitReview" round>提交审核</el-button>
    </div>

    <div class="review-body">
      <!-- 左侧：分块列表 -->
      <div class="chunk-list-panel">
        <div class="panel-header">
          <span class="panel-title">分块列表</span>
          <el-tag effect="plain" round size="small">{{ chunks.length }} 个</el-tag>
        </div>
        <div class="chunk-list-content" v-loading="loading">
          <div
            v-for="chunk in chunks"
            :key="chunk.id"
            class="chunk-item"
            :class="{ active: selectedChunk?.id === chunk.id }"
            @click="onChunkSelect(chunk)"
          >
            <div class="chunk-item-header">
              <el-tag
                :type="chunk.chunkType === 'parent' ? 'primary' : 'success'"
                effect="light"
                round
                size="small"
              >
                {{ chunk.chunkType === 'parent' ? '父块' : '子块' }}
              </el-tag>
              <el-tag
                :type="chunk.status === 'active' ? 'success' : 'info'"
                effect="plain"
                round
                size="small"
              >
                {{ chunk.status === 'active' ? '激活' : '草稿' }}
              </el-tag>
              <span class="chunk-token">{{ chunk.tokenCount || 0 }} tokens</span>
            </div>
            <div class="chunk-item-content">{{ chunk.content?.substring(0, 80) }}...</div>
          </div>
          <el-empty v-if="!loading && chunks.length === 0" description="暂无分块数据" :image-size="60" />
        </div>
      </div>

      <!-- 右侧：分块详情 -->
      <div class="chunk-detail-panel">
        <template v-if="selectedChunk">
          <div class="panel-header">
            <span class="panel-title">分块详情</span>
            <div class="flex gap-8">
              <el-button size="small" :icon="EditPen" @click="handleEdit" round>编辑</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="handleDelete" round plain>删除</el-button>
            </div>
          </div>
          <div class="chunk-detail-content">
            <el-descriptions :column="2" border class="chunk-meta">
              <el-descriptions-item label="ID">{{ selectedChunk.id }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ selectedChunk.chunkType === 'parent' ? '父块' : '子块' }}</el-descriptions-item>
              <el-descriptions-item label="Token 数">{{ selectedChunk.tokenCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ selectedChunk.status === 'active' ? '激活' : '草稿' }}</el-descriptions-item>
              <el-descriptions-item label="父块 ID" :span="2">{{ selectedChunk.parentId || '无' }}</el-descriptions-item>
            </el-descriptions>

            <div class="content-section">
              <div class="section-label">分块内容</div>
              <div class="content-box">{{ selectedChunk.content }}</div>
            </div>

            <div v-if="selectedChunk.metadata" class="content-section">
              <div class="section-label">元数据</div>
              <pre class="metadata-box">{{ formatMetadata(selectedChunk.metadata) }}</pre>
            </div>
          </div>
        </template>
        <el-empty v-else description="请选择左侧分块查看详情" :image-size="80" />
      </div>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑分块内容" width="640px">
      <el-input v-model="editContent" type="textarea" :rows="10" placeholder="请输入分块内容" />
      <template #footer>
        <el-button @click="editDialogVisible = false" round>取消</el-button>
        <el-button type="primary" @click="handleSaveEdit" round>保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, EditPen, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { chunkApi } from '@/api/chunk'
import { documentApi } from '@/api/document'
import type { Chunk, Document, ReviewSubmitRequest } from '@/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const document = ref<Document | null>(null)
const chunks = ref<Chunk[]>([])
const selectedChunk = ref<Chunk | null>(null)
const editDialogVisible = ref(false)
const editContent = ref('')

const docId = Number(route.params.docId)

async function loadData() {
  loading.value = true
  try {
    const [docRes, chunkRes] = await Promise.all([
      documentApi.getById(docId),
      chunkApi.listByDocId(docId),
    ])
    document.value = docRes.data
    chunks.value = chunkRes.data
  } catch (e) { console.error(e) } finally { loading.value = false }
}

function onChunkSelect(chunk: Chunk) {
  selectedChunk.value = chunk
}

function handleEdit() {
  if (!selectedChunk.value) return
  editContent.value = selectedChunk.value.content
  editDialogVisible.value = true
}

async function handleSaveEdit() {
  if (!selectedChunk.value) return
  try {
    await chunkApi.update({ id: selectedChunk.value.id, content: editContent.value })
    ElMessage.success('保存成功')
    selectedChunk.value.content = editContent.value
    editDialogVisible.value = false
  } catch (e) { console.error(e) }
}

async function handleDelete() {
  if (!selectedChunk.value) return
  try {
    await ElMessageBox.confirm('确定要删除该分块吗？', '提示', { type: 'warning' })
    await chunkApi.delete(selectedChunk.value.id)
    ElMessage.success('删除成功')
    chunks.value = chunks.value.filter((c) => c.id !== selectedChunk.value!.id)
    selectedChunk.value = null
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

async function handleSubmitReview() {
  try {
    await ElMessageBox.confirm('确定要提交审核吗？提交后将触发向量化。', '提示', { type: 'warning' })
    const data: ReviewSubmitRequest = {}
    await documentApi.submitReview(docId, data)
    ElMessage.success('审核已提交，正在向量化')
    const kbId = document.value?.kbId
    router.push(kbId ? `/knowledge-bases/${kbId}` : '/knowledge-bases')
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

function goBack() {
  const kbId = document.value?.kbId
  router.push(kbId ? `/knowledge-bases/${kbId}` : '/knowledge-bases')
}

function formatMetadata(metadata: string): string {
  try { return JSON.stringify(JSON.parse(metadata), null, 2) } catch { return metadata }
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.chunk-review-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px;
  gap: 16px;
}

.review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.review-body {
  flex: 1;
  display: flex;
  gap: 16px;
  overflow: hidden;
}

.chunk-list-panel {
  width: 360px;
  background: var(--surface);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.chunk-detail-panel {
  flex: 1;
  background: var(--surface);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;

  .panel-title {
    font-weight: 600;
    font-size: 15px;
    color: var(--text-primary);
  }
}

.chunk-list-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.chunk-item {
  padding: 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  margin-bottom: 8px;
  border: 2px solid transparent;
  background: var(--bg);
  transition: all 0.2s ease;

  &:hover {
    background: var(--primary-bg);
    border-color: var(--primary-lighter);
    transform: translateY(-1px);
  }

  &.active {
    background: var(--primary-bg);
    border-color: var(--primary);
    box-shadow: var(--shadow-primary);
  }

  .chunk-item-header {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;

    .chunk-token {
      margin-left: auto;
      font-size: 12px;
      color: var(--text-tertiary);
      font-weight: 500;
    }
  }

  .chunk-item-content {
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}

.chunk-detail-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;

  .chunk-meta {
    margin-bottom: 20px;
  }

  .content-section {
    margin-bottom: 20px;

    .section-label {
      font-size: 13px;
      font-weight: 600;
      color: var(--text-secondary);
      margin-bottom: 8px;
    }

    .content-box {
      background: var(--bg);
      border-radius: var(--radius-md);
      padding: 16px;
      font-size: 14px;
      line-height: 1.8;
      color: var(--text-primary);
      white-space: pre-wrap;
      border: 1px solid var(--border-light);
    }

    .metadata-box {
      background: var(--bg);
      border-radius: var(--radius-md);
      padding: 16px;
      font-size: 13px;
      color: var(--text-secondary);
      overflow-x: auto;
      border: 1px solid var(--border-light);
    }
  }
}
</style>
