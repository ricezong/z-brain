<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">文档管理</h1>
        <p class="page-subtitle">上传、解析和管理知识库文档，跟踪处理进度</p>
      </div>
      <el-button type="primary" round @click="uploadDialogVisible = true">
        <el-icon><Upload /></el-icon>
        上传文档
      </el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filters.kbId" placeholder="知识库" clearable filterable style="width: 200px" @change="loadList">
        <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <el-input v-model="filters.fileName" placeholder="文件名" clearable style="width: 200px" @keyup.enter="loadList">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px" @change="loadList">
        <el-option v-for="(v, k) in docStatusMap" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button type="primary" plain @click="loadList">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <!-- 文档列表 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column label="文件名" min-width="220">
          <template #default="{ row }">
            <div class="file-name-cell">
              <div class="file-type-icon" :class="`type-${row.fileType}`">
                {{ (row.fileType || '?').toUpperCase().slice(0, 3) }}
              </div>
              <span class="file-name">{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="所属知识库" width="140">
          <template #default="{ row }">
            <span>{{ getKbName(row.kbId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="分块数" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.chunkCount" type="info" effect="plain" round>{{ row.chunkCount }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <span class="status-tag" :class="getStatusClass(row.status)">
              {{ getDocStatus(row.status).label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="120">
          <template #default="{ row }">
            <el-progress
              v-if="row.status === 'parsing' || row.status === 'embedding'"
              :percentage="row.parseProgress || 0"
              :stroke-width="6"
              :show-text="true"
              :color="row.status === 'embedding' ? '#8b5cf6' : '#f59e0b'"
            />
            <span v-else-if="row.status === 'success'" class="text-success">
              <el-icon><CircleCheck /></el-icon> 完成
            </span>
            <span v-else-if="row.status === 'failed'" class="text-danger" :title="row.errorMessage">
              <el-icon><CircleClose /></el-icon> 失败
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goChunkReview(row)">
              <el-icon><Files /></el-icon> 分块审核
            </el-button>
            <el-button
              v-if="row.status === 'pending_review' || row.status === 'embedding' || row.status === 'success'"
              link type="success" size="small"
              :disabled="row.status !== 'pending_review'"
              @click="handleEmbed(row)"
            >
              <el-icon><Promotion /></el-icon> 向量化
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传文档" width="520px" destroy-on-close>
      <el-form label-width="90px" label-position="right">
        <el-form-item label="知识库" required>
          <el-select v-model="uploadForm.kbId" placeholder="请选择知识库" filterable style="width: 100%">
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分块大小">
          <el-input-number v-model="uploadForm.chunkSize" :min="64" :max="1024" :step="32" placeholder="留空使用知识库默认" />
          <span class="upload-tip">留空使用知识库默认值</span>
        </el-form-item>
        <el-form-item label="文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            drag
            accept=".pdf,.doc,.docx,.ppt,.pptx,.txt,.md,.html,.xls,.xlsx"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF, DOC, DOCX, PPT, PPTX, TXT, MD, HTML, XLS, XLSX 格式，最大 200MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">开始上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDocuments, uploadDocument, deleteDocument, triggerEmbedding, getDocumentProgress } from '@/api/document'
import { listKnowledgeBases } from '@/api/knowledgeBase'
import { formatFileSize, formatDateTime, docStatusMap, getDocStatus } from '@/utils/format'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const uploading = ref(false)
const list = ref([])
const total = ref(0)
const kbOptions = ref([])
const uploadDialogVisible = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)
const progressTimers = ref({})

const filters = reactive({
  kbId: route.query.kbId ? Number(route.query.kbId) : '',
  fileName: '',
  status: ''
})
const pagination = reactive({ pageNum: 1, pageSize: 10 })

const uploadForm = reactive({ kbId: '', chunkSize: null })

async function loadList() {
  loading.value = true
  try {
    const res = await listDocuments({
      ...filters,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
    // 对处理中的文档启动轮询
    list.value.forEach((doc) => {
      if (doc.status === 'parsing' || doc.status === 'embedding') {
        startProgressPolling(doc.id)
      }
    })
  } finally {
    loading.value = false
  }
}

async function loadKbOptions() {
  try {
    const res = await listKnowledgeBases({ pageNum: 1, pageSize: 1000 })
    kbOptions.value = res.data?.list || []
  } catch { /* ignore */ }
}

function resetFilters() {
  filters.kbId = ''
  filters.fileName = ''
  filters.status = ''
  pagination.pageNum = 1
  loadList()
}

function getKbName(kbId) {
  return kbOptions.value.find((kb) => kb.id === kbId)?.name || '-'
}

function getStatusClass(status) {
  const map = {
    pending: 'status-info',
    parsing: 'status-warning',
    pending_review: 'status-primary',
    embedding: 'status-warning',
    success: 'status-success',
    failed: 'status-danger'
  }
  return map[status] || 'status-info'
}

function startProgressPolling(docId) {
  if (progressTimers.value[docId]) return
  progressTimers.value[docId] = setInterval(async () => {
    try {
      const res = await getDocumentProgress(docId)
      const progress = res.data
      const doc = list.value.find((d) => d.id === docId)
      if (doc) {
        doc.status = progress.status
        doc.parseProgress = progress.progress
        doc.chunkCount = progress.chunkCount
      }
      if (progress.status === 'success' || progress.status === 'failed' || progress.status === 'pending_review') {
        clearInterval(progressTimers.value[docId])
        delete progressTimers.value[docId]
      }
    } catch {
      clearInterval(progressTimers.value[docId])
      delete progressTimers.value[docId]
    }
  }, 3000)
}

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function handleExceed() {
  ElMessage.warning('每次只能上传一个文件')
}

async function handleUpload() {
  if (!uploadForm.kbId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('kbId', uploadForm.kbId)
    formData.append('file', selectedFile.value)
    if (uploadForm.chunkSize) formData.append('chunkSize', uploadForm.chunkSize)
    const res = await uploadDocument(formData)
    ElMessage.success('上传成功，正在异步解析...')
    uploadDialogVisible.value = false
    selectedFile.value = null
    uploadForm.kbId = ''
    uploadForm.chunkSize = null
    loadList()
    // 开始轮询进度
    startProgressPolling(res.data)
  } finally {
    uploading.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除文档「${row.fileName}」吗？`, '提示', { type: 'warning' })
  await deleteDocument(row.id)
  ElMessage.success('删除成功')
  loadList()
}

async function handleEmbed(row) {
  await ElMessageBox.confirm(`确定触发文档「${row.fileName}」的向量化吗？`, '提示', { type: 'info' })
  await triggerEmbedding(row.id)
  ElMessage.success('向量化任务已触发')
  loadList()
}

function goChunkReview(row) {
  router.push(`/chunks/${row.id}`)
}

onMounted(() => {
  loadKbOptions()
  loadList()
})

onUnmounted(() => {
  Object.values(progressTimers.value).forEach(clearInterval)
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.file-type-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.file-type-icon.type-pdf { background: #ef4444; }
.file-type-icon.type-doc, .file-type-icon.type-docx { background: #2563eb; }
.file-type-icon.type-ppt, .file-type-icon.type-pptx { background: #f97316; }
.file-type-icon.type-txt, .file-type-icon.type-md { background: #6b7280; }
.file-type-icon.type-html { background: #06b6d4; }
.file-type-icon.type-xls, .file-type-icon.type-xlsx { background: #10b981; }
.file-type-icon.type-unknown { background: #94a3b8; }
.file-name {
  font-weight: 500;
  color: var(--text-primary);
}

.text-muted { color: var(--text-secondary); font-size: 13px; }
.text-success { color: var(--success); font-size: 13px; display: inline-flex; align-items: center; gap: 4px; }
.text-danger { color: var(--danger); font-size: 13px; display: inline-flex; align-items: center; gap: 4px; cursor: help; }

.upload-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}
.upload-icon {
  font-size: 40px;
  color: var(--primary-light);
  margin-bottom: 8px;
}
</style>
