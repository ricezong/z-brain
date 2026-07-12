<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">知识库管理</h1>
        <p class="page-subtitle">创建和管理知识库，配置分块策略与提示词模板</p>
      </div>
      <el-button type="primary" round @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        创建知识库
      </el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input
        v-model="filters.name"
        placeholder="搜索知识库名称"
        clearable
        style="width: 220px"
        @input="debouncedSearch"
        @clear="reloadList"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filters.category" placeholder="分类" clearable style="width: 140px" @change="reloadList">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable style="width: 100px" @change="reloadList">
        <el-option label="启用" value="active" />
        <el-option label="停用" value="inactive" />
      </el-select>
    </div>

    <!-- 知识库卡片网格（无限滚动） -->
    <div
      ref="scrollContainerRef"
      class="card-grid kb-scroll-container"
      v-loading="loading"
      @scroll="onScroll"
    >
      <div class="kb-card" v-for="item in list" :key="item.id" @click="goToDocs(item)">
        <div class="kb-card-header">
          <div class="kb-icon">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="kb-header-info">
            <h3 class="kb-name">{{ item.name }}</h3>
            <span class="kb-category" v-if="item.category">{{ item.category }}</span>
          </div>
          <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, item)">
            <el-icon class="kb-more" @click.stop><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <p class="kb-desc">{{ item.description || '暂无描述' }}</p>

        <div class="kb-stats">
          <div class="kb-stat">
            <span class="kb-stat-value">{{ item.docCount || 0 }}</span>
            <span class="kb-stat-label">文档</span>
          </div>
          <div class="kb-stat">
            <span class="kb-stat-value">{{ item.chunkCount || 0 }}</span>
            <span class="kb-stat-label">分块</span>
          </div>
          <div class="kb-stat">
            <span class="kb-stat-value">{{ item.chunkSize || 300 }}</span>
            <span class="kb-stat-label">Token/块</span>
          </div>
        </div>

        <div class="kb-card-footer">
          <span class="status-tag" :class="item.status === 'active' ? 'status-success' : 'status-info'">
            {{ item.status === 'active' ? '启用' : '停用' }}
          </span>
          <span class="kb-time">{{ formatDateTime(item.createTime) }}</span>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && list.length === 0" class="empty-state">
        <el-icon class="empty-icon"><FolderOpened /></el-icon>
        <p class="empty-text">暂无知识库，点击右上角创建</p>
      </div>

      <!-- 加载更多提示 -->
      <div v-if="loadingMore" class="load-more-hint">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-if="noMore && list.length > 0" class="load-more-hint">
        <span>已加载全部</span>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑知识库' : '创建知识库'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" label-position="right">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入知识库名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述（可选）" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select
            v-model="form.category"
            placeholder="请选择或输入分类"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="分块大小" prop="chunkSize">
          <el-input-number v-model="form.chunkSize" :min="64" :max="1024" :step="32" />
          <span class="form-tip">子块 Token 数（默认 300）</span>
        </el-form-item>
        <el-form-item label="提示词模板">
          <el-select v-model="form.promptTemplateId" placeholder="选择提示词模板（可选）" clearable style="width: 100%">
            <el-option v-for="t in templateList" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" v-if="editingId">
          <el-radio-group v-model="form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  listCategories
} from '@/api/knowledgeBase'
import { listPromptTemplates } from '@/api/promptTemplate'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const loadingMore = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const categories = ref([])
const templateList = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const scrollContainerRef = ref(null)

const PAGE_SIZE = 10

const filters = reactive({ name: '', category: '', status: '' })
const pageNum = ref(1)

/** 防抖搜索 */
let searchTimer = null
function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    reloadList()
  }, 300)
}

/** 是否已加载全部 */
const noMore = computed(() => list.value.length >= total.value && total.value > 0)

const form = reactive({
  name: '',
  description: '',
  category: '',
  chunkSize: 300,
  promptTemplateId: null,
  status: 'active'
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择或输入分类', trigger: 'change' }],
  chunkSize: [{ required: true, message: '请输入分块大小', trigger: 'blur' }]
}

/** 首次加载 / 筛选后重新加载 */
async function reloadList() {
  pageNum.value = 1
  loading.value = true
  try {
    const res = await listKnowledgeBases({
      ...filters,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE
    })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

/** 滚动加载下一页 */
async function loadMore() {
  if (loadingMore.value || loading.value || noMore.value) return
  loadingMore.value = true
  pageNum.value++
  try {
    const res = await listKnowledgeBases({
      ...filters,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE
    })
    const newItems = res.data?.list || []
    list.value.push(...newItems)
    total.value = res.data?.total || total.value
  } catch {
    pageNum.value--
  } finally {
    loadingMore.value = false
  }
}

/** 滚动事件 */
function onScroll() {
  const el = scrollContainerRef.value
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (scrollHeight - scrollTop - clientHeight < 80) {
    loadMore()
  }
}

async function loadCategories() {
  try {
    const res = await listCategories()
    categories.value = res.data || []
  } catch { /* ignore */ }
}

async function loadTemplates() {
  try {
    const res = await listPromptTemplates()
    templateList.value = res.data || []
  } catch { /* ignore */ }
}


function openCreateDialog() {
  editingId.value = null
  Object.assign(form, { name: '', description: '', category: '', chunkSize: 300, promptTemplateId: null, status: 'active' })
  dialogVisible.value = true
}

function openEditDialog(item) {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    description: item.description || '',
    category: item.category || '',
    chunkSize: item.chunkSize || 300,
    promptTemplateId: item.promptTemplateId,
    status: item.status || 'active'
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (editingId.value) {
      await updateKnowledgeBase(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createKnowledgeBase({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    reloadList()
    loadCategories()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(item) {
  await ElMessageBox.confirm(`确定删除知识库「${item.name}」吗？`, '提示', { type: 'warning' })
  await deleteKnowledgeBase(item.id)
  ElMessage.success('删除成功')
  reloadList()
  loadCategories()
}

function goToDocs(item) {
  router.push({ path: '/documents', query: { kbId: item.id } })
}

function handleCommand(cmd, item) {
  if (cmd === 'edit') openEditDialog(item)
  else if (cmd === 'delete') handleDelete(item)
}

onMounted(() => {
  reloadList()
  loadCategories()
  loadTemplates()
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

/* 无限滚动容器 */
.kb-scroll-container {
  min-height: 200px;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 4px 4px 4px;
  margin: -4px -4px 0 -4px;
}

.kb-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  transition: all 0.3s ease;
  cursor: pointer;
}
.kb-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-lighter);
}

.kb-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.kb-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--primary-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.kb-header-info {
  flex: 1;
  min-width: 0;
}
.kb-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kb-category {
  font-size: 12px;
  color: var(--text-secondary);
}
.kb-more {
  font-size: 18px;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.2s;
}
.kb-more:hover {
  color: var(--primary);
  background: var(--bg-hover);
}

.kb-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}

.kb-stats {
  display: flex;
  gap: 24px;
  padding: 16px 0;
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 14px;
}
.kb-stat-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.kb-stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.kb-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kb-time {
  font-size: 12px;
  color: var(--text-placeholder);
}

.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.empty-state {
  grid-column: 1 / -1;
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

/* 加载更多提示 */
.load-more-hint {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 0;
  font-size: 13px;
  color: var(--text-placeholder);
}
</style>
