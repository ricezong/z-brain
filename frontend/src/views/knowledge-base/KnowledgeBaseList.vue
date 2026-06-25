<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <span class="page-title">知识库管理</span>
        <p class="page-subtitle">创建和管理您的知识库空间</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog" round>创建知识库</el-button>
    </div>

    <div class="search-bar">
      <el-input v-model="searchName" placeholder="搜索知识库名称..." clearable style="width: 260px" @keyup.enter="handleSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="searchCategory" placeholder="全部分类" clearable style="width: 160px">
        <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch" round>查询</el-button>
      <el-button :icon="Refresh" @click="handleReset" round plain>重置</el-button>
    </div>

    <div class="table-container">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="name" label="名称" min-width="180">
          <template #default="{ row }">
            <div class="kb-name-cell">
              <div class="kb-icon"><el-icon><Collection /></el-icon></div>
              <span class="kb-name-text">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" align="center">
          <template #default="{ row }">
            <el-tag effect="plain" round>{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="docCount" label="文档数" width="100" align="center">
          <template #default="{ row }">
            <span class="stat-num">{{ row.docCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="100" align="center">
          <template #default="{ row }">
            <span class="stat-num">{{ row.chunkCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" effect="light" round>
              {{ row.status === 'active' ? '活跃' : '已归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-divider direction="vertical" />
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑知识库' : '创建知识库'" width="520px" class="kb-dialog">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px" label-position="left">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入知识库描述" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="formData.category" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
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
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Refresh, Collection } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { knowledgeBaseApi } from '@/api/knowledge-base'
import { useAppStore } from '@/stores/app'
import { formatDateTime } from '@/utils/format'
import type { KnowledgeBase, KnowledgeBaseCreateRequest, KnowledgeBaseUpdateRequest } from '@/types'

const appStore = useAppStore()

const loading = ref(false)
const tableData = ref<KnowledgeBase[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchName = ref('')
const searchCategory = ref('')
const categories = ref<string[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(0)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive<KnowledgeBaseCreateRequest>({
  name: '',
  description: '',
  category: 'general',
})

const formRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
}

async function loadCategories() {
  try {
    const res = await knowledgeBaseApi.categories()
    categories.value = res.data
  } catch (e) { console.error(e) }
}

async function loadData() {
  loading.value = true
  try {
    const res = await knowledgeBaseApi.list({
      name: searchName.value || undefined,
      category: searchCategory.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    tableData.value = res.data.list
    total.value = res.data.total
  } catch (e) { console.error(e) } finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; loadData() }
function handleReset() { searchName.value = ''; searchCategory.value = ''; pageNum.value = 1; loadData() }

function openCreateDialog() {
  isEdit.value = false
  formData.name = ''
  formData.description = ''
  formData.category = 'general'
  dialogVisible.value = true
}

function openEditDialog(row: KnowledgeBase) {
  isEdit.value = true
  editId.value = row.id
  formData.name = row.name
  formData.description = row.description
  formData.category = row.category
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) {
        const updateData: KnowledgeBaseUpdateRequest = { name: formData.name, description: formData.description, category: formData.category }
        await knowledgeBaseApi.update(editId.value, updateData)
        ElMessage.success('编辑成功')
      } else {
        await knowledgeBaseApi.create(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
      appStore.loadKbList()
    } catch (e) { console.error(e) } finally { submitting.value = false }
  })
}

async function handleDelete(row: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(`确定要删除知识库「${row.name}」吗？`, '提示', { type: 'warning' })
    await knowledgeBaseApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
    appStore.loadKbList()
  } catch (e) { if (e !== 'cancel') console.error(e) }
}

onMounted(() => { loadCategories(); loadData() })
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

.kb-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;

  .kb-icon {
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

  .kb-name-text {
    font-weight: 600;
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
