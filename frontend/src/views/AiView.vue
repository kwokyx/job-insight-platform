<script setup>
import { ref, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { fetchSSE, predictSalary } from '../api'
import { Bot, User, Sparkles, Send, Trash2, Lightbulb, Cpu, Calculator } from 'lucide-vue-next'
import { useAuthStore } from '../store/auth'
import { streamAiChat } from '../api'
import { marked } from 'marked'

const authStore = useAuthStore()
const message = ref('')
const isLoading = ref(false)
const chatHistoryRef = ref(null)
const hasApiKey = ref(true) // 假设可用，请求失败再标记

const conversation = ref([
  { role: 'assistant', content: '你好！我是你的 AI 职业顾问，可以帮你分析：\n• 行业薪资水平与趋势\n• 职位技能要求\n• 职业发展路径建议\n\n请问有什么想了解的？' }
])

// 快捷提问
const quickQuestions = [
  '目前人工智能方向的平均薪资是多少？',
  '后端开发需要掌握哪些核心技能？',
  '哪些城市的就业机会最多？',
  '从前端转全栈需要学什么？'
]

const scrollToBottom = async () => {
  await nextTick()
  if (chatHistoryRef.value) {
    chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
  }
}

const sendMessage = async (text) => {
  const userMsg = (text || message.value).trim()
  if (!userMsg || isLoading.value) return
  
  conversation.value.push({ role: 'user', content: userMsg })
  message.value = ''
  isLoading.value = true
  scrollToBottom()

  // 尝试调用真实 AI API
  try {
    let aiReply = ''
    conversation.value.push({ role: 'assistant', content: '' })
    const lastIdx = conversation.value.length - 1

    await streamAiChat(authStore.token, { message: userMsg }, {
      onMessage: (data) => {
        if (data.reasoning_content) {
          conversation.value[lastIdx].reasoning = (conversation.value[lastIdx].reasoning || '') + data.reasoning_content
        }
        let textUpdate = data.content || data.raw || ''
        if (textUpdate) {
          aiReply += textUpdate
          const parsed = parseContent(aiReply)
          if (parsed.reasoning && !data.reasoning_content) {
            conversation.value[lastIdx].reasoning = parsed.reasoning
          }
          conversation.value[lastIdx].content = parsed.content
        }
        scrollToBottom()
      },
      onDone: () => {
        if (!aiReply) {
          conversation.value[lastIdx].content = '分析完成，但未获取到有效回复。'
        }
      },
      onError: (data) => {
        conversation.value[lastIdx].content = `抱歉，处理请求时出现问题：${data.message || '未知错误'}`
        hasApiKey.value = false
      }
    })
  } catch (error) {
    // 如果流式 API 不可用，使用智能本地回复
    const lastMsg = conversation.value[conversation.value.length - 1]
    if (lastMsg.role === 'assistant' && !lastMsg.content) {
      conversation.value.pop()
    }
    
    const reply = generateLocalReply(userMsg)
    conversation.value.push({ role: 'assistant', content: reply })
    hasApiKey.value = false
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

const generateLocalReply = (question) => {
  const q = question.toLowerCase()
  
  if (q.includes('薪资') || q.includes('工资') || q.includes('薪水') || q.includes('待遇')) {
    return `根据平台数据分析，当前市场薪资概况如下：\n\n• 人工智能/算法方向：25K~45K/月\n• 后端开发（Java/Go）：18K~35K/月\n• 前端开发：15K~30K/月\n• 数据分析/BI：15K~28K/月\n• DevOps/SRE：20K~40K/月\n\n一线城市薪资普遍高于二线城市 20%~40%。建议前往「数据洞察」页面查看更详细的薪资趋势图表。`
  }
  
  if (q.includes('技能') || q.includes('学什么') || q.includes('掌握')) {
    return `当前市场热门技能需求排名：\n\n1. Python — 数据科学与 AI 领域必备\n2. Java/Spring — 企业级后端主力\n3. SQL — 数据处理基本功\n4. JavaScript/TypeScript — 前端与全栈\n5. Docker/K8s — 云原生部署\n6. 机器学习框架（PyTorch/TensorFlow）\n\n建议根据目标方向有针对性地深耕 2~3 个核心技能，同时保持对新技术的关注。`
  }
  
  if (q.includes('城市') || q.includes('哪里') || q.includes('地区')) {
    return `根据平台采集的岗位数据，就业机会最集中的城市：\n\n1. 北京 — 互联网/AI 头部企业聚集\n2. 上海 — 金融科技与外企集中\n3. 深圳 — 硬件+软件双轮驱动\n4. 杭州 — 电商与云计算生态\n5. 成都/武汉 — 新一线性价比之选\n\n建议在「岗位大厅」中按城市筛选查看具体岗位分布。`
  }
  
  if (q.includes('规划') || q.includes('转行') || q.includes('发展') || q.includes('建议')) {
    return `职业发展建议：\n\n1. 明确目标方向 — 选定 1~2 个感兴趣且有市场需求的领域\n2. 构建技术栈 — 围绕目标方向系统学习核心技术\n3. 项目积累 — 通过实际项目或开源贡献提升经验\n4. 持续学习 — 关注行业趋势，保持技术更新\n5. 软实力提升 — 沟通、协作、项目管理同样重要\n\n可以在平台的「数据洞察」中查看各方向的市场需求趋势，帮助做出更明智的选择。`
  }
  
  return `感谢你的提问。基于平台的大数据分析能力，我建议你：\n\n1. 查看「数据洞察」页面获取最新的行业分析报告\n2. 在「岗位大厅」搜索相关职位了解具体要求\n3. 关注技能热度排行，把握学习方向\n\n如果你有更具体的问题（如某个城市的薪资水平、某个技能的市场需求等），我可以给出更精准的分析。`
}

const parseContent = (fullText) => {
  let reasoning = ''
  let content = fullText
  
  if (fullText.includes('<think>')) {
    const parts = fullText.split('</think>')
    reasoning = parts[0].replace('<think>', '').trim()
    content = parts.length > 1 ? parts[1].trim() : ''
  }
  
  return { reasoning, content }
}

const renderMarkdown = (text) => {
  if (!text) return ''
  return marked(text)
}

// === 薪资预测工具 ===
const predictForm = ref({ city: '', education: '本科', experience: '', skills: '', industry: '' })
const predictResult = ref(null)
const isPredicting = ref(false)
const predictError = ref('')

const doPredict = async () => {
  isPredicting.value = true
  predictError.value = ''
  predictResult.value = null
  try {
    const skills = predictForm.value.skills.split(/[,，、\s]+/).filter(Boolean)
    const result = await predictSalary({
      city: predictForm.value.city || undefined,
      education: predictForm.value.education || undefined,
      experience: predictForm.value.experience || undefined,
      skills: skills,
      industry: predictForm.value.industry || undefined
    })
    predictResult.value = result
    scrollToBottom()
  } catch (e) {
    predictError.value = e.message || '预测失败，请确保后端算法服务已启动'
  } finally {
    isPredicting.value = false
  }
}

const clearChat = () => {
  conversation.value = [
    { role: 'assistant', content: '对话已清空。请问有什么想了解的？' }
  ]
}
</script>

<template>
  <div class="chat-layout">
    <PremiumCard class="chat-container">
      <template #header>
         <div class="chat-header">
           <div class="chat-title">
             <Bot class="header-icon" :size="24" />
             <div>
               <h2>AI 职业顾问</h2>
               <span class="chat-sub">基于大数据的智能分析</span>
             </div>
           </div>
           <div class="header-right">
             <GlowButton variant="ghost" @click="clearChat" class="clear-btn">
               <Trash2 :size="15" /> 清空
             </GlowButton>
           </div>
         </div>
      </template>

      <div class="chat-history" ref="chatHistoryRef">
        <div 
          v-for="(msg, index) in conversation" 
          :key="index"
          class="chat-bubble-wrapper"
          :class="msg.role"
        >
          <div class="chat-avatar">
            <Bot v-if="msg.role === 'assistant'" :size="20" />
            <User v-else :size="20" />
          </div>
          <div class="chat-bubble" :class="{ 'markdown-content': msg.role === 'assistant' }">
            <div v-if="msg.reasoning" class="chat-reasoning">
              <span class="reasoning-title"><Lightbulb :size="14" /> 模型思考过程：</span>
              <p v-for="(line, lIndex) in msg.reasoning.split('\n')" :key="'r'+lIndex" class="chat-line">
                {{ line }}
              </p>
            </div>
            
            <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
            <div v-else>
              <p v-for="(line, lIndex) in (msg.content || '').split('\n')" :key="lIndex" class="chat-line">
                {{ line }}
              </p>
            </div>
          </div>
        </div>

        <div v-if="isLoading" class="chat-bubble-wrapper assistant loading-state">
           <div class="chat-avatar"><Bot :size="20" /></div>
           <div class="chat-bubble typing-indicator">
              <span></span><span></span><span></span>
           </div>
        </div>

        <!-- 快捷提问 -->
        <div v-if="conversation.length <= 1 && !isLoading" class="quick-questions">
          <p class="quick-title">试试这些问题：</p>
          <div class="quick-list">
            <button 
              v-for="q in quickQuestions" 
              :key="q" 
              class="quick-btn"
              @click="sendMessage(q)"
            >
              <Sparkles :size="14" /> {{ q }}
            </button>
          </div>
        </div>

      </div>
      
      <div class="chat-input-area">
        <div class="input-wrapper">
          <Sparkles class="input-icon" :size="20" />
          <input 
            v-model="message" 
            @keyup.enter="sendMessage()"
            type="text" 
            placeholder="输入你的问题..." 
            class="chat-input" 
            :disabled="isLoading"
          />
        </div>
        <GlowButton variant="primary" @click="sendMessage()" :disabled="isLoading" class="send-btn">
          <Send :size="18" />
        </GlowButton>
      </div>
    </PremiumCard>

    <!-- AI 薪资预测工具独立板块 (右侧) -->
    <div class="predict-column">
      <PremiumCard glowColor="purple" class="predict-card">
        <template #header>
          <div class="predict-header">
            <div class="predict-title">
              <Cpu :size="22" class="predict-icon" />
              <div>
                <h2>AI 薪资评估模型</h2>
                <span style="font-size: 13px; color: var(--c-text-muted);">基于机器学习算法实时演算</span>
              </div>
            </div>
          </div>
        </template>
        
        <div class="predict-body">
          <div class="predict-form">
            <div class="form-row">
              <div class="form-field">
                <label>目标城市</label>
                <input v-model="predictForm.city" placeholder="如: 北京" class="glass-input-sm" />
              </div>
              <div class="form-field">
                <label>理想学历</label>
                <select v-model="predictForm.education" class="glass-input-sm">
                  <option value="">不限</option>
                  <option>大专</option><option>本科</option><option>硕士</option><option>博士</option>
                </select>
              </div>
            </div>
            <div class="form-row">
              <div class="form-field">
                <label>工作经验</label>
                <input v-model="predictForm.experience" placeholder="如: 3-5年" class="glass-input-sm" />
              </div>
              <div class="form-field">
                <label>目标行业</label>
                <input v-model="predictForm.industry" placeholder="如: 互联网" class="glass-input-sm" />
              </div>
            </div>
            <div class="form-row">
              <div class="form-field flex-2">
                <label>核心技能 (用逗号分隔)</label>
                <input v-model="predictForm.skills" placeholder="如: Python, Java, 大语言模型" class="glass-input-sm" />
              </div>
            </div>
            
            <GlowButton variant="primary" :loading="isPredicting" @click="doPredict" style="padding: 12px; margin-top: 8px;">
              <Calculator :size="16" /> 开始智能预估
            </GlowButton>
          </div>

          <div v-if="predictError" class="predict-error">{{ predictError }}</div>

          <div v-if="predictResult" class="predict-result">
            <div class="result-salary">
              <span class="result-label">AI 演算薪资区间</span>
              <strong class="result-value">
                {{ predictResult.predicted_min || '?' }}K — {{ predictResult.predicted_max || '?' }}K
              </strong>
              <span class="result-median" style="font-size: 14px; color: var(--c-text-secondary);">
                中位数预估: {{ predictResult.predicted_median || '-' }}K/月
              </span>
            </div>
          </div>
        </div>
      </PremiumCard>
    </div>
  </div>
</template>

<style scoped>
.chat-layout {
  height: 100%;
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
}

@media (max-width: 1024px) {
  .chat-layout {
    grid-template-columns: 1fr;
    display: flex;
    flex-direction: column;
    height: auto;
    min-height: 100%;
  }
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 600px;
}
/* override card padding */
:deep(.card-body) {
  padding: 0 !important;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.chat-title h2 {
  margin: 0;
  font-size: 20px;
}
.chat-sub {
  font-size: 12px;
  color: var(--c-text-muted);
}
.header-icon {
  color: var(--c-accent-primary);
}

.header-right {
  display: flex;
  gap: 8px;
}
.clear-btn {
  font-size: 12px;
  padding: 6px 12px;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-height: calc(100vh - 250px);
}

.chat-history::-webkit-scrollbar {
  width: 6px;
}

.chat-reasoning {
  margin-bottom: 12px;
  padding: 12px;
  background: var(--c-bg-surface-active);
  border-left: 3px solid var(--c-accent-purple);
  border-radius: 8px;
  font-size: 13px;
  color: var(--c-text-muted);
}
.reasoning-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--c-accent-purple);
  margin-bottom: 8px;
}
.chat-history::-webkit-scrollbar-thumb {
  background: var(--c-border-glass-hover);
  border-radius: 10px;
}

.chat-bubble-wrapper {
  display: flex;
  gap: 14px;
  max-width: 85%;
  animation: slideUp var(--duration-normal) var(--ease-out);
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.chat-bubble-wrapper.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--c-bg-surface-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-primary);
}

.chat-bubble-wrapper.assistant .chat-avatar {
  background: rgba(59, 130, 246, 0.2);
  color: var(--c-accent-primary);
}

.chat-bubble {
  padding: 14px 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--c-border-glass);
  line-height: 1.7;
  font-size: 14px;
}

.chat-line {
  margin: 0;
  min-height: 1.2em;
}

.chat-bubble-wrapper.user .chat-bubble {
  background: var(--c-accent-primary);
  border-color: transparent;
  color: #fff;
  border-top-right-radius: 4px;
}

.chat-bubble-wrapper.assistant .chat-bubble {
  border-top-left-radius: 4px;
}

/* Quick Questions */
.quick-questions {
  padding: 8px 0;
}

.quick-title {
  font-size: 13px;
  color: var(--c-text-muted);
  margin-bottom: 12px;
}

.quick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quick-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: rgba(255,255,255,0.03);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-size: 14px;
  text-align: left;
  transition: all var(--duration-fast);
}
.quick-btn:hover {
  background: rgba(255,255,255,0.06);
  border-color: rgba(168, 85, 247, 0.3);
  color: var(--c-text-primary);
}

/* Typing Indicator */
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 18px 20px;
}
.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-text-muted);
  animation: typing 1.4s infinite ease-in-out both;
}
.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.chat-input-area {
  padding: 20px 24px;
  border-top: 1px solid var(--c-border-glass);
  display: flex;
  gap: 12px;
}

.input-wrapper {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 16px;
  color: var(--c-accent-purple);
}

.chat-input {
  width: 100%;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  padding: 14px 20px 14px 48px;
  color: var(--c-text-primary);
  font-size: 15px;
  transition: all var(--duration-fast);
}
.chat-input:focus {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(168, 85, 247, 0.5);
  box-shadow: 0 0 15px rgba(168, 85, 247, 0.2);
}

.send-btn {
  border-radius: 999px;
  padding: 0 24px;
}

@media (max-width: 768px) {
  .chat-layout {
    height: auto;
    min-height: calc(100vh - 140px);
  }
  .chat-history {
    padding: 16px;
  }
  .chat-bubble-wrapper {
    max-width: 95%;
  }
  .chat-input-area {
    padding: 12px;
    gap: 8px;
  }
  .chat-input {
    padding: 10px 16px 10px 40px;
    font-size: 16px;
  }
  .send-btn {
    padding: 0 16px;
  }
  .quick-btn {
    font-size: 13px;
    padding: 10px 14px;
  }
}

/* Markdown Styling */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 600;
}
.markdown-body :deep(h3) {
  font-size: 16px;
  color: var(--c-accent-primary);
}
.markdown-body :deep(p) {
  margin-bottom: 0.8em;
}
.markdown-body :deep(ul), .markdown-body :deep(ol) {
  padding-left: 1.5em;
  margin-bottom: 0.8em;
}
.markdown-body :deep(li) {
  margin-bottom: 0.4em;
}
.markdown-body :deep(strong) {
  font-weight: 700;
  color: var(--c-accent-secondary);
}

/* Predict Widget Column */
.predict-column {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.predict-card {
  flex: 1;
  max-width: none;
}
.predict-header { display: flex; align-items: center; justify-content: space-between; }
.predict-title { display: flex; align-items: center; gap: 12px; }
.predict-title h2 { margin: 0; font-size: 18px; }
.predict-icon { color: var(--c-accent-purple); }
.predict-body { display: flex; flex-direction: column; gap: 20px; margin-top: 12px; padding: 0 12px 12px 12px; }
.predict-form { display: flex; flex-direction: column; gap: 16px; }
.form-row { display: flex; gap: 16px; flex-wrap: wrap; }
.form-field { flex: 1; min-width: 120px; }
.form-field.flex-2 { flex: 2; }
.form-field label { display: block; font-size: 13px; color: var(--c-text-secondary); margin-bottom: 6px; }
.glass-input-sm {
  width: 100%;
  padding: 10px 14px;
  background: var(--c-bg-surface-hover);
  border: 1px solid var(--c-border-glass);
  border-radius: var(--radius-sm);
  color: var(--c-text-primary);
  font-size: 14px;
  transition: all 0.2s;
}
.glass-input-sm:focus {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 2px var(--c-accent-primary-glow);
  outline: none;
}
.predict-error {
  padding: 12px 16px; border-radius: var(--radius-sm);
  background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.3);
  color: #FCA5A5; font-size: 13px;
}
.predict-result {
  padding: 24px 16px; border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(255,255,255,0.05), transparent);
  border: 1px solid var(--c-border-glass);
  display: flex; align-items: center; justify-content: center;
}
.result-salary { text-align: center; }
.result-label { display: block; font-size: 14px; color: var(--c-accent-purple); margin-bottom: 8px; font-weight: 600; }
.result-value {
  display: block; font-size: 34px; font-weight: 800; font-family: var(--font-display);
  background: linear-gradient(135deg, #F97316, #FB923C);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  margin-bottom: 8px;
}
</style>
