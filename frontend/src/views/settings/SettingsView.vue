<template>
  <div class="settings-view">
    <div class="settings-container">
      <div class="settings-header">
        <div>
          <h2>设置</h2>
          <p>调整对话与知识库的全局行为</p>
        </div>
      </div>

      <div class="settings-section">
        <div class="settings-section-head">
          <h3>对话</h3>
          <p>控制 AI 回答的整体行为</p>
        </div>
        <div class="settings-row">
          <div class="settings-row-label">
            <div class="t">默认模型</div>
            <div class="d">未手动切换时使用的对话模型</div>
          </div>
          <button class="btn btn-secondary btn-sm">DeepSeek</button>
        </div>
        <div class="settings-row">
          <div class="settings-row-label">
            <div class="t">流式输出</div>
            <div class="d">边生成边显示回答</div>
          </div>
          <div
            class="toggle"
            :class="{ on: streamEnabled }"
            @click="streamEnabled = !streamEnabled"
            role="switch"
            :aria-checked="streamEnabled"
            tabindex="0"
          ></div>
        </div>
      </div>

      <div class="settings-section">
        <div class="settings-section-head">
          <h3>知识库</h3>
          <p>控制文档解析与检索的行为</p>
        </div>
        <div class="settings-row">
          <div class="settings-row-label">
            <div class="t">分块策略</div>
            <div class="d">父子分块（父块 1000 Token，子块 200 Token）</div>
          </div>
          <button class="btn btn-secondary btn-sm">父子分块</button>
        </div>
        <div class="settings-row">
          <div class="settings-row-label">
            <div class="t">检索数量</div>
            <div class="d">每次回答引用的最大分块数</div>
          </div>
          <button class="btn btn-secondary btn-sm">Top 5</button>
        </div>
        <div class="settings-row">
          <div class="settings-row-label">
            <div class="t">自动重排</div>
            <div class="d">检索后使用 reranker 模型重排结果</div>
          </div>
          <div
            class="toggle"
            :class="{ on: rerankEnabled }"
            @click="rerankEnabled = !rerankEnabled"
            role="switch"
            :aria-checked="rerankEnabled"
            tabindex="0"
          ></div>
        </div>
      </div>

      <div class="settings-section">
        <div class="settings-section-head">
          <h3>关于</h3>
          <p>系统信息</p>
        </div>
        <div class="settings-row">
          <div class="settings-row-label">
            <div class="t">版本</div>
            <div class="d">Z-Brain v1.0.0</div>
          </div>
        </div>
        <div class="settings-row">
          <div class="settings-row-label">
            <div class="t">技术栈</div>
            <div class="d">Spring Boot 3.4.5 + Spring AI 1.1.8 + Vue 3</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const streamEnabled = ref(true)
const rerankEnabled = ref(false)
</script>

<style scoped>
.settings-view {
  flex: 1;
  overflow-y: auto;
  padding: var(--s-6);
}

.settings-container {
  max-width: 720px;
  margin: 0 auto;
}

.settings-header {
  margin-bottom: var(--s-6);
}
.settings-header h2 { font-size: 22px; font-weight: 600; letter-spacing: -0.01em; margin-bottom: 4px; }
.settings-header p { font-size: 13px; color: var(--text-secondary); }

.settings-section {
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  margin-bottom: var(--s-4);
}

.settings-section-head {
  padding: var(--s-4) var(--s-5);
  border-bottom: 1px solid var(--border);
}
.settings-section-head h3 { font-size: 14px; font-weight: 600; }
.settings-section-head p { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

.settings-row {
  padding: var(--s-4) var(--s-5);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-4);
  border-bottom: 1px solid var(--border);
}
.settings-row:last-child { border-bottom: none; }
.settings-row-label .t { font-size: 13px; font-weight: 500; }
.settings-row-label .d { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

.btn {
  display: inline-flex; align-items: center; gap: var(--s-2);
  padding: 5px 10px; border-radius: var(--r-md);
  font-size: 12px; font-weight: 500;
  transition: background 0.12s, border-color 0.12s, color 0.12s;
  border: 1px solid transparent;
}
.btn-secondary { background: var(--bg); color: var(--text); border-color: var(--border); }
.btn-secondary:hover { background: var(--bg-hover); }
.btn-sm { padding: 5px 10px; font-size: 12px; }

.toggle {
  position: relative;
  width: 36px; height: 20px;
  background: var(--bg-active);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.toggle.on { background: var(--primary); }
.toggle::after {
  content: '';
  position: absolute;
  top: 2px; left: 2px;
  width: 16px; height: 16px;
  background: var(--bg);
  border-radius: 50%;
  transition: transform 0.15s;
  box-shadow: var(--shadow-sm);
}
.toggle.on::after { transform: translateX(16px); }
</style>
