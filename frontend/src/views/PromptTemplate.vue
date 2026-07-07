<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">提示词模板</h1>
        <p class="page-subtitle">配置系统提示词与用户提示词，支持知识库级别定制</p>
      </div>
      <el-button type="primary" round @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新建模板
      </el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filterKbId" placeholder="按知识库筛选" clearable filterable style="width: 240px" @change="loadList">
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <el-button @click="loadDefault" plain>
        <el-icon><Star /></el-icon>
        查看默认模板
      </el-button>
    </div>

    <!-- 模板列表 -->
    <div v-loading="loading" class="template-grid">
      <div class="template-card" v-for="item in list" :key="item.id">
        <div class="template-header">
          <div class="template-icon">
            <el-icon><EditPen /></el-icon>
          </div>
          <div class="template-header-info">
            <h3 class="template-name">{{ item.name }}</h3>
            <div class="template-tags">
              <el-tag v-if="item.isDefault" type="warning" size="small" effect="plain" round>默认</el-tag>
              <el-tag v-if="item.kbId" type="primary" size="small" effect="plain" round>{{ getKbName(item.kbId) }}</el-tag>
              <el-tag v-else type="info" size="small" effect="plain" round>全局</el-tag>
            </div>
          </div>
          <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, item)">
            <el-icon class="template-more"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="copy">复制内容</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div class="template-body">
          <div class="prompt-section">
            <div class="prompt-label">系统提示词</div>
            <div class="prompt-content">{{ item.systemPrompt }}</div>
          </div>
          <div class="prompt-section">
            <div class="prompt-label">用户提示词</div>
            <div class="prompt-content">{{ item.userPrompt }}</div>
          </div>
        </div>

        <div class="template-footer">
          <span class="template-time">{{ formatDateTime(item.updateTime || item.createTime) }}</span>
          <el-button link type="primary" size="small" @click="openEditDialog(item)">
            <el-icon><Edit /></el-icon> 编辑
          </el-button>
        </div>
      </div>

      <div v-if="!loading && list.length === 0" class="empty-state">
        <el-icon class="empty-icon"><EditPen /></el-icon>
        <p class="empty-text">暂无提示词模板</p>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑模板' : '新建模板'"
      width="720px"
      destroy-on-close
      class="template-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" label-position="right">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入模板名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-select v-model="form.kbId" placeholder="不关联则为全局模板" clearable filterable style="width: 100%">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="5"
            placeholder="定义 AI 的角色、行为和约束"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="用户提示词" prop="userPrompt">
          <el-input
            v-model="form.userPrompt"
            type="textarea"
            :rows="5"
            placeholder="使用 {context} 和 {question} 占位符"
            maxlength="2000"
            show-word-limit
          />
          <div class="form-hint">
            <el-icon><InfoFilled /></el-icon>
            支持占位符：<code>{'{'+'context}'}</code> 引用上下文，<code>{'{'+'question}'}</code> 引用用户问题
          </div>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
          <span class="form-hint-text">设为默认后将作为未配置知识库的回退模板</span>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPromptTemplates,
  createPromptTemplate,
  updatePromptTemplate,
  deletePromptTemplate,
  getDefaultPromptTemplate
} from '@/api/promptTemplate'
import { listKnowledgeBases } from '@/api/knowledgeBase'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const kbList = ref([])
const filterKbId = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)

const form = reactive({
  name: '',
  kbId: null,
  systemPrompt: '',
  userPrompt: '',
  isDefault: false
})

const rules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  userPrompt: [{ required: true, message: '请输入用户提示词', trigger: 'blur' }]
}

async function loadList() {
  loading.value = true
  try {
    const res = await listPromptTemplates({ kbId: filterKbId.value || undefined })
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadKbList() {
  try {
    const res = await listKnowledgeBases({ pageNum: 1, pageSize: 1000 })
    kbList.value = res.data?.list || []
  } catch { /* ignore */ }
}

function getKbName(kbId) {
  return kbList.value.find((kb) => kb.id === kbId)?.name || '未知'
}

function openCreateDialog() {
  editingId.value = null
  Object.assign(form, { name: '', kbId: null, systemPrompt: '', userPrompt: '', isDefault: false })
  dialogVisible.value = true
}

function openEditDialog(item) {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    kbId: item.kbId,
    systemPrompt: item.systemPrompt,
    userPrompt: item.userPrompt,
    isDefault: item.isDefault
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (editingId.value) {
      await updatePromptTemplate(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createPromptTemplate({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(item) {
  await ElMessageBox.confirm(`确定删除模板「${item.name}」吗？`, '提示', { type: 'warning' })
  await deletePromptTemplate(item.id)
  ElMessage.success('删除成功')
  loadList()
}

function handleCommand(cmd, item) {
  if (cmd === 'edit') openEditDialog(item)
  else if (cmd === 'copy') {
    navigator.clipboard.writeText(`${item.systemPrompt}\n\n${item.userPrompt}`)
    ElMessage.success('已复制到剪贴板')
  }
  else if (cmd === 'delete') handleDelete(item)
}

async function loadDefault() {
  try {
    const res = await getDefaultPromptTemplate()
    if (res.data) {
      openEditDialog(res.data)
    } else {
      ElMessage.info('暂无默认模板')
    }
  } catch { /* ignore */ }
}

onMounted(() => {
  loadKbList()
  loadList()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  min-height: 200px;
}

.template-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
}
.template-card:hover {
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-lighter);
  transform: translateY(-2px);
}

.template-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border-light);
}
.template-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ec4899, #f43f5e);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.template-header-info {
  flex: 1;
  min-width: 0;
}
.template-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.template-tags {
  display: flex;
  gap: 6px;
}
.template-more {
  font-size: 18px;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.2s;
}
.template-more:hover {
  color: var(--primary);
  background: var(--bg-hover);
}

.template-body {
  flex: 1;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.prompt-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.prompt-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.prompt-content {
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.6;
  background: var(--bg-page);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-family: 'Fira Code', 'Consolas', monospace;
}

.template-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-top: 1px solid var(--border-light);
  background: #f8fafc;
}
.template-time {
  font-size: 12px;
  color: var(--text-placeholder);
}

.form-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}
.form-hint code {
  background: var(--bg-page);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--primary);
}
.form-hint-text {
  margin-left: 8px;
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
</style>
