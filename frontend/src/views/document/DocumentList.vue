<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <span class="page-title">文档管理</span>
        <p class="page-subtitle">上传、解析和管理知识库文档</p>
      </div>
      <div class="flex gap-12">
        <el-select v-model="selectedKbId" placeholder="选择知识库" style="width: 200px" @change="handleKbChange">
          <template #prefix><el-icon><Collection /></el-icon></template>
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-upload :show-file-list="false" :before-upload="handleUpload" :http-request="() => {}">
          <el-button type="primary" :icon="Upload" :disabled="!selectedKbId" round>上传文档</el-button>
        </el-upload>
      </div>
    </div>

    <div class="search-bar">
      <el-input v-model="searchFileName" placeholder="搜索文件名..." clearable style="width: 240px" @keyup.enter="handleSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="searchStatus" placeholder="全部状态" clearable style="width: 140px">
        <el-option label="待解析" value="pending" />
        <el-option label="解析中" value="parsing" />
        <el-option label="待审核" value="pending_review" />
        <el-option label="向量化中" value="embedding" />
        <el-option label="已完成" value="success" />
        <el-option label="失败" value="failed" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch" round>查询</el-button>
      <el-button :icon="Refresh" @click="handleReset" round plain>重置</el-button>
    </div>

    <div class="table-container">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="fileName" label="文件名" min-width="200">
          <template #default="{ row }">
            <div class="file-name-cell">
              <div class="file-icon" :class="row.fileType">
                <el-icon><Document /></el-icon>
              </div>
              <span class="file-name-text">{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100" align="center">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag effect="plain" round size="small">{{ row.fileType?.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="documentStatusMap(row.status).type" effect="light" round>
              {{ documentStatusMap(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="90" align="center">
          <template #default="{ row }"><span class="stat-num">{{ row.chunkCount }}</span></template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="180" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending_review'" link size="small" @click="goToChunkReview(row)">审核</el-button>
            <el-button v-if="row.status === 'pending_review'" link size="small" @click="handleTriggerEmbedding(row)">向量化</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            <el-button v-if="row.status === 'failed'" link type="warning" size="small" @click="showError(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Upload, Search, Refresh, Document, Collection } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { documentApi } from '@/api/document'
import { useAppStore } from '@/stores/app'
import { storeToRefs } from 'pinia'
import { formatDateTime, formatFileSize, documentStatusMap } from '@/utils/format'
import type { Document as DocumentType } from '@/types'

const router = useRouter()
const appStore = useAppStore()
const { kbList, currentKb } = storeToRefs(appStore)

const loading = ref(false)
const tableData = ref<DocumentType[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchFileName = ref('')
const searchStatus = ref('')
const selectedKbId = ref<number | undefined>()
const pollingTimers = ref<Map<number, ReturnType<typeof setInterval>>>(new Map())

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const res = await documentApi.list({
      kbId: selectedKbId.value,
      fileName: searchFileName.value || undefined,
      status: searchStatus.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    tableData.value = res.data.list
    total.value = res.data.total
    checkPolling()
  } catch (e) { console.error(e) } finally { loading.value = false }
}

function checkPolling() {
  tableData.value.forEach((doc) => {
    if (['pending', 'parsing', 'embedding'].includes(doc.status)) {
      if (!pollingTimers.value.has(doc.id)) {
        const timer = setInterval(async () => {
          try {
            const res = await documentApi.getProgress(doc.id)
            const progress = res.data
            const idx = tableData.value.findIndex((d) => d.id === doc.id)
            if (idx >= 0) {
              tableData.value[idx].status = progress.status
              tableData.value[idx].parseProgress = progress.progress
              tableData.value[idx].chunkCount = progress.chunkCount
              tableData.value[idx].errorMessage = progress.errorMessage
            }
            if (!['pending', 'parsing', 'embedding'].includes(progress.status)) {
              clearInterval(timer)
              pollingTimers.value.delete(doc.id)
            }
          } catch (e) { console.error(e) }
        }, 3000)
        pollingTimers.value.set(doc.id, timer)
      }
    }
  })
}

function stopAllPolling() {
  pollingTimers.value.forEach((timer) => clearInterval(timer))
  pollingTimers.value.clear()
}

function handleKbChange() { pageNum.value = 1; loadData() }
function handleSearch() { pageNum.value = 1; loadData() }
function handleReset() { searchFileName.value = ''; searchStatus.value = ''; pageNum.value = 1; loadData() }

async function handleUpload(file: File) {
  if (!selectedKbId.value) { ElMessage.warning('请先选择知识库'); return false }
  try {
    await documentApi.upload(selectedKbId.value, file)
    ElMessage.success('文档上传成功，正在后台解析')
    loadData()
  } catch (e) { console.error(e) }
  return false
}

async function handleTriggerEmbedding(row: DocumentType) {
  try {
    await ElMessageBox.confirm(`确定要对文档「${row.fileName}」进行向量化吗？`, '提示', { type: 'warning' })
    await documentApi.triggerEmbedding(row.id)
    ElMessage.success('向量化任务已触发')
    loadData()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

function goToChunkReview(row: DocumentType) { router.push(`/documents/${row.id}/chunks`) }
function showError(row: DocumentType) { ElMessageBox.alert(row.errorMessage || '未知错误', '错误详情', { type: 'error' }) }

async function handleDelete(row: DocumentType) {
  try {
    await ElMessageBox.confirm(`确定要删除文档「${row.fileName}」吗？`, '提示', { type: 'warning' })
    await documentApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

onMounted(() => {
  selectedKbId.value = currentKb.value?.id
  if (selectedKbId.value) loadData()
})
onUnmounted(() => stopAllPolling())
</script>

<style scoped lang="scss">
.page-subtitle {
  font-size: 14px;
  color: var(--text-tertiary);
  margin-top: 4px;
  font-weight: 400;
}

.table-container {
  background: var(--surface);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .file-icon {
    width: 32px;
    height: 32px;
    border-radius: var(--radius-sm);
    background: var(--primary-bg);
    color: var(--primary);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    &.pdf { background: #fef2f2; color: #ef4444; }
    &.doc, &.docx { background: #eff6ff; color: #3b82f6; }
    &.ppt, &.pptx { background: #fff7ed; color: #f97316; }
    &.xls, &.xlsx { background: #ecfdf5; color: #10b981; }
  }

  .file-name-text {
    font-weight: 500;
    color: var(--text-primary);
  }
}

.stat-num {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary);
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
