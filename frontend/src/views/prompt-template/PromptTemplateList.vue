<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <span class="page-title">提示词模板管理</span>
        <p class="page-subtitle">配置 RAG 问答的系统提示词与用户提示词</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog" round>创建模板</el-button>
    </div>

    <div class="table-container">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="name" label="模板名称" min-width="180">
          <template #default="{ row }">
            <div class="template-name-cell">
              <div class="template-icon"><el-icon><EditPen /></el-icon></div>
              <span class="template-name-text">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="kbId" label="知识库" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.kbId" effect="plain" round size="small">ID: {{ row.kbId }}</el-tag>
            <el-tag v-else type="info" effect="plain" round size="small">通用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isDefault" label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success" effect="light" round size="small">默认</el-tag>
            <span v-else class="text-tertiary">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="systemPrompt" label="系统提示词" min-width="220" show-overflow-tooltip />
        <el-table-column prop="userPrompt" label="用户提示词" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模板' : '创建模板'" width="680px">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="知识库ID">
          <el-input-number v-model="formData.kbId" :min="1" placeholder="留空表示通用" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input v-model="formData.systemPrompt" type="textarea" :rows="4" placeholder="请输入系统提示词" />
        </el-form-item>
        <el-form-item label="用户提示词" prop="userPrompt">
          <el-input v-model="formData.userPrompt" type="textarea" :rows="4" placeholder="使用 {context} 和 {question} 占位符" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="formData.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false" round>取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit" round>确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, EditPen } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { promptTemplateApi } from '@/api/prompt-template'
import { formatDateTime } from '@/utils/format'
import type { PromptTemplate, PromptTemplateRequest } from '@/types'

const loading = ref(false)
const tableData = ref<PromptTemplate[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(0)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const formData = ref<PromptTemplateRequest>({
  name: '',
  kbId: undefined,
  systemPrompt: '',
  userPrompt: '',
  isDefault: false,
})

const rules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  userPrompt: [{ required: true, message: '请输入用户提示词', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const res = await promptTemplateApi.list()
    tableData.value = res.data
  } catch (e) { console.error(e) } finally { loading.value = false }
}

function openCreateDialog() {
  isEdit.value = false
  formData.value = { name: '', kbId: undefined, systemPrompt: '', userPrompt: '', isDefault: false }
  dialogVisible.value = true
}

function openEditDialog(row: PromptTemplate) {
  isEdit.value = true
  editId.value = row.id
  formData.value = {
    name: row.name,
    kbId: row.kbId || undefined,
    systemPrompt: row.systemPrompt,
    userPrompt: row.userPrompt,
    isDefault: row.isDefault,
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) {
        await promptTemplateApi.update(editId.value, formData.value)
        ElMessage.success('编辑成功')
      } else {
        await promptTemplateApi.create(formData.value)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) { console.error(e) } finally { submitting.value = false }
  })
}

async function handleDelete(row: PromptTemplate) {
  try {
    await ElMessageBox.confirm(`确定要删除模板「${row.name}」吗？`, '提示', { type: 'warning' })
    await promptTemplateApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

onMounted(() => loadData())
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

.template-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .template-icon {
    width: 32px;
    height: 32px;
    border-radius: var(--radius-sm);
    background: var(--primary-bg);
    color: var(--primary);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .template-name-text {
    font-weight: 600;
    color: var(--text-primary);
  }
}

.text-tertiary {
  color: var(--text-tertiary);
}
</style>
