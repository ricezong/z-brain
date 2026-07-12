<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-header">
      <div class="review-header-left">
        <el-button circle text class="back-btn" @click="$router.push('/documents')">
          <el-icon size="20"><ArrowLeft /></el-icon>
        </el-button>
        <div>
          <h1 class="page-title">分块审核</h1>
          <p class="page-subtitle" v-if="docInfo">{{ docInfo.fileName }} · 共 {{ chunks.length }} 个分块</p>
        </div>
      </div>
      <div class="header-actions">
        <el-tag v-if="modifiedChunks.length" type="warning" effect="plain" round size="small">
          <el-icon><EditPen /></el-icon> {{ modifiedChunks.length }} 处修改
        </el-tag>
        <el-tag v-if="deletedIds.length" type="danger" effect="plain" round size="small">
          <el-icon><Delete /></el-icon> {{ deletedIds.length }} 处删除
        </el-tag>
        <el-button type="primary" round :disabled="!modifiedChunks.length && !deletedIds.length" @click="submitReviewDialog = true">
          <el-icon><Check /></el-icon>
          提交审核
        </el-button>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="searchKeyword" placeholder="搜索分块内容" clearable style="width: 240px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-radio-group v-model="filterType" size="default">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="parent">父块</el-radio-button>
          <el-radio-button value="child">子块</el-radio-button>
        </el-radio-group>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" plain size="small" :disabled="selectedIds.length < 2" @click="openMergeDialog">
          <el-icon><Connection /></el-icon> 合并 ({{ selectedIds.length }})
        </el-button>
      </div>
    </div>

    <!-- 分块列表 -->
    <div v-loading="loading" class="chunk-list">
      <div
        v-for="(chunk, index) in filteredChunks"
        :key="chunk.id"
        class="chunk-card"
        :class="{ selected: selectedIds.includes(chunk.id), editing: editingId === chunk.id }"
      >
        <div class="chunk-card-bar">
          <el-checkbox
            :model-value="selectedIds.includes(chunk.id)"
            @change="(val) => toggleSelect(chunk.id, val)"
          />
          <span class="chunk-index">#{{ index + 1 }}</span>
          <el-tag size="small" :type="chunk.chunkType === 'parent' ? 'primary' : 'info'" effect="plain" round>
            {{ chunk.chunkType === 'parent' ? '父块' : '子块' }}
          </el-tag>
          <el-tag size="small" :type="chunk.status === 'active' ? 'success' : 'warning'" effect="plain" round>
            {{ chunk.status === 'active' ? '生效' : '草稿' }}
          </el-tag>
          <span class="chunk-tokens" v-if="chunk.tokenCount">{{ chunk.tokenCount }} tokens</span>
        </div>

        <div class="chunk-content" v-if="editingId !== chunk.id">
          <pre>{{ chunk.content }}</pre>
        </div>
        <div class="chunk-edit-area" v-else>
          <el-input
            v-model="editContent"
            type="textarea"
            :rows="6"
            placeholder="编辑分块内容"
          />
        </div>

        <div class="chunk-actions">
          <template v-if="editingId === chunk.id">
            <el-button type="primary" size="small" @click="saveEdit(chunk)">保存</el-button>
            <el-button size="small" @click="cancelEdit">取消</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" size="small" @click="startEdit(chunk)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-button link type="warning" size="small" @click="openSplitDialog(chunk)">
              <el-icon><Scissor /></el-icon> 拆分
            </el-button>
            <el-popconfirm title="确定删除此分块？" @confirm="handleDelete(chunk)">
              <template #reference>
                <el-button link type="danger" size="small">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </div>
      </div>

      <div v-if="!loading && filteredChunks.length === 0" class="empty-state">
        <el-icon class="empty-icon"><Files /></el-icon>
        <p class="empty-text">暂无分块数据</p>
      </div>
    </div>

    <!-- 拆分对话框 -->
    <el-dialog v-model="splitDialogVisible" title="拆分分块" width="600px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        在下方内容中点击光标位置，将分块拆分为两个。拆分位置为字符偏移量。
      </el-alert>
      <el-form label-width="100px">
        <el-form-item label="分块 ID">
          <el-tag>{{ splitForm.chunkId }}</el-tag>
        </el-form-item>
        <el-form-item label="拆分位置">
          <el-input-number v-model="splitForm.splitPosition" :min="0" :max="splitMaxLength" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="splitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="splitting" @click="handleSplit">确定拆分</el-button>
      </template>
    </el-dialog>

    <!-- 合并对话框 -->
    <el-dialog v-model="mergeDialogVisible" title="合并分块" width="480px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        将选中的 {{ selectedIds.length }} 个分块合并为一个。合并后生成新的分块。
      </el-alert>
      <el-form label-width="100px">
        <el-form-item label="选中分块">
          <div class="merge-list">
            <el-tag v-for="id in selectedIds" :key="id" style="margin: 2px">#{{ id }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="父块 ID">
          <el-input v-model="mergeForm.parentId" placeholder="可选，合并后所属父块 ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="merging" @click="handleMerge">确定合并</el-button>
      </template>
    </el-dialog>

    <!-- 提交审核对话框 -->
    <el-dialog v-model="submitReviewDialog" title="提交审核" width="600px" destroy-on-close>
      <el-alert type="warning" :closable="false" style="margin-bottom: 16px">
        提交后将记录本次审核的所有变更（新增、修改、删除的分块）。
      </el-alert>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="修改的分块数">{{ modifiedChunks.length }}</el-descriptions-item>
        <el-descriptions-item label="删除的分块数">{{ deletedIds.length }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="submitReviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitReview">确定提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  listChunksByDocId,
  updateChunk,
  deleteChunk,
  mergeChunks,
  splitChunk
} from '@/api/chunk'
import { getDocumentById, submitReview } from '@/api/document'

const route = useRoute()
const router = useRouter()
const docId = Number(route.params.docId)

const loading = ref(false)
const chunks = ref([])
const docInfo = ref(null)
const searchKeyword = ref('')
const filterType = ref('')
const selectedIds = ref([])
const editingId = ref(null)
const editContent = ref('')
const modifiedChunks = ref([])
const deletedIds = ref([])

const splitDialogVisible = ref(false)
const splitting = ref(false)
const splitForm = reactive({ chunkId: null, splitPosition: 0 })
const splitMaxLength = computed(() => {
  const chunk = chunks.value.find((c) => c.id === splitForm.chunkId)
  return chunk?.content?.length || 0
})

const mergeDialogVisible = ref(false)
const merging = ref(false)
const mergeForm = reactive({ parentId: '' })

const submitReviewDialog = ref(false)
const submitting = ref(false)

const filteredChunks = computed(() => {
  return chunks.value.filter((c) => {
    if (filterType.value && c.chunkType !== filterType.value) return false
    if (searchKeyword.value && !c.content?.includes(searchKeyword.value)) return false
    return true
  })
})

async function loadData() {
  loading.value = true
  try {
    const [docRes, chunkRes] = await Promise.all([
      getDocumentById(docId),
      listChunksByDocId(docId)
    ])
    docInfo.value = docRes.data
    chunks.value = chunkRes.data || []
  } finally {
    loading.value = false
  }
}

function toggleSelect(id, checked) {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value.push(id)
  } else {
    selectedIds.value = selectedIds.value.filter((i) => i !== id)
  }
}

function startEdit(chunk) {
  editingId.value = chunk.id
  editContent.value = chunk.content
}

function cancelEdit() {
  editingId.value = null
  editContent.value = ''
}

async function saveEdit(chunk) {
  await updateChunk({ id: chunk.id, content: editContent.value })
  chunk.content = editContent.value
  // 记录修改
  const existing = modifiedChunks.value.find((m) => m.id === chunk.id)
  if (existing) {
    existing.content = editContent.value
  } else {
    modifiedChunks.value.push({ ...chunk, content: editContent.value })
  }
  ElMessage.success('保存成功')
  editingId.value = null
  editContent.value = ''
}

async function handleDelete(chunk) {
  await deleteChunk(chunk.id)
  chunks.value = chunks.value.filter((c) => c.id !== chunk.id)
  deletedIds.value.push(chunk.id)
  ElMessage.success('删除成功')
}

function openSplitDialog(chunk) {
  splitForm.chunkId = chunk.id
  splitForm.splitPosition = Math.floor(chunk.content.length / 2)
  splitDialogVisible.value = true
}

async function handleSplit() {
  splitting.value = true
  try {
    const res = await splitChunk({ chunkId: splitForm.chunkId, splitPosition: splitForm.splitPosition })
    const newChunks = res.data || []
    // 替换原分块
    const idx = chunks.value.findIndex((c) => c.id === splitForm.chunkId)
    if (idx >= 0) {
      chunks.value.splice(idx, 1, ...newChunks)
    }
    ElMessage.success('拆分成功')
    splitDialogVisible.value = false
  } finally {
    splitting.value = false
  }
}

function openMergeDialog() {
  mergeForm.parentId = ''
  mergeDialogVisible.value = true
}

async function handleMerge() {
  merging.value = true
  try {
    const res = await mergeChunks({
      chunkIds: selectedIds.value,
      parentId: mergeForm.parentId || null
    })
    const mergedChunk = res.data
    // 移除被合并的分块，插入新分块
    chunks.value = chunks.value.filter((c) => !selectedIds.value.includes(c.id))
    if (mergedChunk) {
      chunks.value.unshift(mergedChunk)
    }
    selectedIds.value = []
    ElMessage.success('合并成功')
    mergeDialogVisible.value = false
  } finally {
    merging.value = false
  }
}

async function handleSubmitReview() {
  submitting.value = true
  try {
    await submitReview(docId, {
      added: [],
      modified: modifiedChunks.value.map((c) => ({
        id: c.id, docId, kbId: c.kbId, parentId: c.parentId,
        chunkType: c.chunkType, content: c.content, tokenCount: c.tokenCount, metadata: c.metadata
      })),
      deleted: deletedIds.value
    })
    ElMessage.success('审核已提交')
    submitReviewDialog.value = false
    modifiedChunks.value = []
    deletedIds.value = []
    router.push('/documents')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.review-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  color: var(--text-secondary);
  flex-shrink: 0;
}
.back-btn:hover {
  color: var(--primary);
}
.header-actions {
  display: flex;
  gap: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
}

.chunk-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition: all 0.2s ease;
}
.chunk-card.selected {
  border-color: var(--primary-light);
  box-shadow: 0 0 0 1px var(--primary-light);
}
.chunk-card.editing {
  border-color: var(--primary);
}

.chunk-card-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid var(--border-light);
}
.chunk-index {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}
.chunk-tokens {
  font-size: 12px;
  color: var(--text-placeholder);
  margin-left: auto;
}

.chunk-content {
  padding: 16px;
  max-height: 300px;
  overflow-y: auto;
}
.chunk-content pre {
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-regular);
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
.chunk-edit-area {
  padding: 16px;
}

.chunk-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border-top: 1px solid var(--border-light);
  background: #f8fafc;
}

.merge-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
}
.empty-icon {
  font-size: 48px;
  color: var(--text-placeholder);
  margin-bottom: 12px;
}
.empty-text {
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
