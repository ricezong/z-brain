<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">系统配置</h1>
        <p class="page-subtitle">管理系统提示词、LLM 模型配置与外部 API 配置</p>
      </div>
    </div>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- ==================== 系统提示词 ==================== -->
      <el-tab-pane label="系统提示词" name="prompts">
        <div v-loading="promptLoading" class="prompt-list">
          <div class="prompt-card" v-for="item in promptList" :key="item.id">
            <div class="prompt-card-header">
              <div class="prompt-card-info">
                <div class="prompt-card-title-row">
                  <h3 class="prompt-card-name">{{ item.name }}</h3>
                  <el-tag size="small" effect="plain" round type="info">{{ item.promptKey }}</el-tag>
                  <el-tag v-if="!item.isActive" size="small" type="danger" effect="plain" round>已禁用</el-tag>
                </div>
                <p class="prompt-card-desc">{{ item.description }}</p>
              </div>
              <el-button link type="primary" size="small" @click="openPromptDialog(item)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
            </div>
            <div class="prompt-card-body">
              <pre class="prompt-card-content">{{ item.content }}</pre>
            </div>
            <div class="prompt-card-footer">
              <span class="prompt-card-time">更新于 {{ formatDateTime(item.updateTime || item.createTime) }}</span>
            </div>
          </div>

          <div v-if="!promptLoading && promptList.length === 0" class="empty-state">
            <el-icon class="empty-icon"><Document /></el-icon>
            <p class="empty-text">暂无系统提示词</p>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== LLM 模型配置 ==================== -->
      <el-tab-pane label="LLM 模型配置" name="models">
        <div class="model-header">
          <el-select v-model="modelTypeFilter" placeholder="按类型筛选" clearable style="width: 160px" @change="loadModels">
            <el-option label="全部" value="" />
            <el-option label="对话模型" value="chat" />
            <el-option label="向量模型" value="embedding" />
            <el-option label="重排模型" value="rerank" />
          </el-select>
          <el-button type="primary" round @click="openModelDialog()">
            <el-icon><Plus /></el-icon>
            新增模型
          </el-button>
        </div>

        <el-table v-loading="modelLoading" :data="modelList" style="width: 100%" class="model-table">
          <el-table-column label="模型名称" prop="name" min-width="160" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="modelTypeTag(row.modelType)" size="small" effect="plain">{{ modelTypeLabel(row.modelType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="模型标识" prop="modelName" min-width="180" />
          <el-table-column label="Base URL" prop="baseUrl" min-width="220" show-overflow-tooltip />
          <el-table-column label="温度" width="80" prop="temperature" />
          <el-table-column label="默认" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault" type="warning" size="small" round>默认</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openModelDialog(row)">编辑</el-button>
              <el-button v-if="!row.isDefault" link type="warning" size="small" @click="handleSetDefault(row)">设为默认</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteModel(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <!-- ==================== 外部 API 配置 ==================== -->
      <el-tab-pane label="外部 API 配置" name="apiConfig">
        <div class="model-header">
          <span class="model-header-hint">管理外部服务 API 连接配置（按 configType 区分）</span>
          <el-button type="primary" round @click="openApiConfigDialog()">
            <el-icon><Plus /></el-icon>
            新增配置
          </el-button>
        </div>
        <el-table v-loading="apiConfigLoading" :data="apiConfigList" style="width: 100%" class="model-table">
          <el-table-column label="配置类型" prop="configType" min-width="140">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.configType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="API Key" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.apiKey ? row.apiKey.substring(0, 8) + '••••••' : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="Base URL" prop="baseUrl" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openApiConfigDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 提示词编辑对话框 ==================== -->
    <el-dialog v-model="promptDialogVisible" title="编辑系统提示词" width="780px" destroy-on-close>
      <el-form ref="promptFormRef" :model="promptForm" :rules="promptRules" label-width="100px" label-position="right">
        <el-form-item label="提示词名称">
          <el-input v-model="promptForm.name" placeholder="提示词名称" />
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="promptForm.description" type="textarea" :rows="2" placeholder="说明该提示词的用途" />
        </el-form-item>
        <el-form-item label="提示词内容" prop="content">
          <el-input
            v-model="promptForm.content"
            type="textarea"
            :rows="12"
            placeholder="提示词内容，支持 {query} {history} 占位符"
          />
          <div class="form-hint">
            <el-icon><InfoFilled /></el-icon>
            支持占位符：<code>{'{'+'query}'}</code> 用户查询，<code>{'{'+'history}'}</code> 对话历史
          </div>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="promptForm.isActive" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="promptSubmitting" @click="submitPromptForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 模型编辑对话框 ==================== -->
    <el-dialog v-model="modelDialogVisible" :title="editingModelId ? '编辑模型配置' : '新增模型配置'" width="680px" destroy-on-close>
      <el-form ref="modelFormRef" :model="modelForm" :rules="modelRules" label-width="100px" label-position="right">
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="modelForm.name" placeholder="如：DeepSeek 对话模型" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="modelForm.modelType" style="width: 100%">
            <el-option label="对话模型 (chat)" value="chat" />
            <el-option label="向量模型 (embedding)" value="embedding" />
            <el-option label="重排模型 (rerank)" value="rerank" />
          </el-select>
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-select v-model="modelForm.provider" style="width: 100%">
            <el-option label="OpenAI 兼容" value="openai_compatible" />
            <el-option label="阿里云百炼 (DashScope)" value="dashscope" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型标识" prop="modelName">
          <el-input v-model="modelForm.modelName" placeholder="如：deepseek-v4-pro" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="modelForm.apiKey" placeholder="API Key" show-password />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="modelForm.baseUrl" placeholder="如：https://api.deepseek.com" />
        </el-form-item>
        <el-form-item label="温度">
          <el-slider v-model="modelForm.temperature" :min="0" :max="2" :step="0.1" show-input style="padding-right: 16px" />
        </el-form-item>
        <el-form-item label="最大 Token">
          <el-input-number v-model="modelForm.maxTokens" :min="1" :max="128000" :step="512" style="width: 100%" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="modelForm.isDefault" />
          <span class="form-hint-text">设为默认后，该类型的模型调用将使用此配置</span>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="modelForm.isActive" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="modelSubmitting" @click="submitModelForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== API 配置编辑对话框 ==================== -->
    <el-dialog v-model="apiConfigDialogVisible" :title="editingApiConfig ? '编辑 API 配置' : '新增 API 配置'" width="680px" destroy-on-close>
      <el-form :model="apiConfigForm" label-width="120px" label-position="right">
        <el-form-item label="配置类型">
          <el-input v-if="!editingApiConfig" v-model="apiConfigForm.configType" placeholder="如：llama_index" />
          <el-tag v-else effect="plain">{{ apiConfigForm.configType }}</el-tag>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="apiConfigForm.enabled" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="apiConfigForm.apiKey" placeholder="API Key" show-password />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="apiConfigForm.baseUrl" placeholder="API Base URL" />
        </el-form-item>
        <el-form-item label="扩展配置(JSON)">
          <el-input
            v-model="apiConfigForm.configJson"
            type="textarea"
            :rows="6"
            placeholder='{"key": "value"}'
            style="font-family: monospace;"
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">以 JSON 格式填写该配置类型的扩展参数</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="apiConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="apiConfigSubmitting" @click="submitApiConfigForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPrompts,
  updatePrompt,
  listLlmModels,
  createLlmModel,
  updateLlmModel,
  deleteLlmModel,
  setDefaultLlmModel,
  listApiConfigs,
  updateApiConfig
} from '@/api/system'
import { formatDateTime } from '@/utils/format'

const activeTab = ref('prompts')

// ==================== 系统提示词 ====================
const promptLoading = ref(false)
const promptList = ref([])
const promptDialogVisible = ref(false)
const promptSubmitting = ref(false)
const promptFormRef = ref(null)
const editingPromptId = ref(null)

const promptForm = reactive({
  name: '',
  description: '',
  content: '',
  isActive: true
})

const promptRules = {
  content: [{ required: true, message: '请输入提示词内容', trigger: 'blur' }]
}

async function loadPrompts() {
  promptLoading.value = true
  try {
    const res = await listPrompts()
    promptList.value = res.data || []
  } finally {
    promptLoading.value = false
  }
}

function openPromptDialog(item) {
  editingPromptId.value = item.id
  Object.assign(promptForm, {
    name: item.name,
    description: item.description,
    content: item.content,
    isActive: item.isActive
  })
  promptDialogVisible.value = true
}

async function submitPromptForm() {
  promptSubmitting.value = true
  try {
    await updatePrompt(editingPromptId.value, { ...promptForm })
    ElMessage.success('保存成功')
    promptDialogVisible.value = false
    loadPrompts()
  } finally {
    promptSubmitting.value = false
  }
}

// ==================== LLM 模型配置 ====================
const modelLoading = ref(false)
const modelList = ref([])
const modelTypeFilter = ref('')
const modelDialogVisible = ref(false)
const modelSubmitting = ref(false)
const modelFormRef = ref(null)
const editingModelId = ref(null)

const modelForm = reactive({
  name: '',
  modelType: 'chat',
  provider: 'openai_compatible',
  modelName: '',
  apiKey: '',
  baseUrl: '',
  temperature: 0.3,
  maxTokens: 4096,
  isDefault: false,
  isActive: true
})

const modelRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
  modelName: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }]
}

function modelTypeLabel(type) {
  const map = { chat: '对话', embedding: '向量', rerank: '重排' }
  return map[type] || type
}

function modelTypeTag(type) {
  const map = { chat: 'primary', embedding: 'success', rerank: 'warning' }
  return map[type] || ''
}

async function loadModels() {
  modelLoading.value = true
  try {
    const res = await listLlmModels()
    let all = res.data || []
    if (modelTypeFilter.value) {
      all = all.filter(m => m.modelType === modelTypeFilter.value)
    }
    modelList.value = all
  } finally {
    modelLoading.value = false
  }
}

function openModelDialog(item) {
  if (item) {
    editingModelId.value = item.id
    Object.assign(modelForm, {
      name: item.name,
      modelType: item.modelType,
      provider: item.provider,
      modelName: item.modelName,
      apiKey: item.apiKey || '',
      baseUrl: item.baseUrl || '',
      temperature: item.temperature ?? 0.3,
      maxTokens: item.maxTokens ?? 4096,
      isDefault: item.isDefault,
      isActive: item.isActive
    })
  } else {
    editingModelId.value = null
    Object.assign(modelForm, {
      name: '',
      modelType: 'chat',
      provider: 'openai_compatible',
      modelName: '',
      apiKey: '',
      baseUrl: '',
      temperature: 0.3,
      maxTokens: 4096,
      isDefault: false,
      isActive: true
    })
  }
  modelDialogVisible.value = true
}

async function submitModelForm() {
  await modelFormRef.value.validate()
  modelSubmitting.value = true
  try {
    if (editingModelId.value) {
      await updateLlmModel(editingModelId.value, { ...modelForm })
      ElMessage.success('更新成功')
    } else {
      await createLlmModel({ ...modelForm })
      ElMessage.success('创建成功')
    }
    modelDialogVisible.value = false
    loadModels()
  } finally {
    modelSubmitting.value = false
  }
}

async function handleSetDefault(row) {
  await ElMessageBox.confirm(`确定将「${row.name}」设为默认${modelTypeLabel(row.modelType)}模型吗？`, '提示', { type: 'warning' })
  await setDefaultLlmModel(row.id)
  ElMessage.success('设置成功')
  loadModels()
}

async function handleDeleteModel(row) {
  await ElMessageBox.confirm(`确定删除模型配置「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteLlmModel(row.id)
  ElMessage.success('删除成功')
  loadModels()
}

// ==================== 外部 API 配置 ====================
const apiConfigLoading = ref(false)
const apiConfigList = ref([])
const apiConfigDialogVisible = ref(false)
const apiConfigSubmitting = ref(false)
const editingApiConfig = ref(false)
const apiConfigForm = reactive({
  configType: '',
  enabled: true,
  apiKey: '',
  baseUrl: '',
  configJson: ''
})

async function loadApiConfigs() {
  apiConfigLoading.value = true
  try {
    const res = await listApiConfigs()
    apiConfigList.value = res.data || []
  } finally {
    apiConfigLoading.value = false
  }
}

function openApiConfigDialog(row) {
  if (row) {
    // 编辑模式
    editingApiConfig.value = true
    let configJson = ''
    if (row.config) {
      try {
        configJson = JSON.stringify(JSON.parse(row.config), null, 2)
      } catch {
        configJson = row.config
      }
    }
    Object.assign(apiConfigForm, {
      configType: row.configType,
      enabled: row.enabled ?? true,
      apiKey: row.apiKey || '',
      baseUrl: row.baseUrl || '',
      configJson
    })
  } else {
    // 新增模式
    editingApiConfig.value = false
    Object.assign(apiConfigForm, {
      configType: '',
      enabled: true,
      apiKey: '',
      baseUrl: '',
      configJson: ''
    })
  }
  apiConfigDialogVisible.value = true
}

async function submitApiConfigForm() {
  apiConfigSubmitting.value = true
  try {
    // 解析 configJson，允许空字符串
    let configValue = null
    const raw = apiConfigForm.configJson.trim()
    if (raw) {
      try {
        configValue = JSON.stringify(JSON.parse(raw))
      } catch (e) {
        ElMessage.error('扩展配置 JSON 格式不合法')
        apiConfigSubmitting.value = false
        return
      }
    }
    const payload = {
      enabled: apiConfigForm.enabled,
      apiKey: apiConfigForm.apiKey,
      baseUrl: apiConfigForm.baseUrl,
      config: configValue
    }
    await updateApiConfig(apiConfigForm.configType, payload)
    ElMessage.success('保存成功')
    apiConfigDialogVisible.value = false
    loadApiConfigs()
  } finally {
    apiConfigSubmitting.value = false
  }
}

onMounted(() => {
  loadPrompts()
  loadModels()
  loadApiConfigs()
})
</script>

<style scoped>
.config-tabs {
  margin-bottom: 8px;
}

/* ==================== 提示词卡片 ==================== */
.prompt-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.prompt-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition: all 0.3s ease;
}
.prompt-card:hover {
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-lighter);
}

.prompt-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}
.prompt-card-info {
  flex: 1;
}
.prompt-card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.prompt-card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.prompt-card-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0;
}

.prompt-card-body {
  padding: 12px 20px;
}
.prompt-card-content {
  font-size: 12px;
  color: var(--text-regular);
  line-height: 1.6;
  background: var(--bg-page);
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  font-family: 'Fira Code', 'Consolas', monospace;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}

.prompt-card-footer {
  padding: 10px 20px;
  border-top: 1px solid var(--border-light);
  background: #f8fafc;
}
.prompt-card-time {
  font-size: 12px;
  color: var(--text-placeholder);
}

/* ==================== 模型配置 ==================== */
.model-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.model-header-hint {
  font-size: 13px;
  color: var(--text-secondary);
}
.model-table {
  border-radius: var(--radius-md);
  overflow: hidden;
}

/* ==================== 通用 ==================== */
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
