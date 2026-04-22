<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageTitle = computed(() => route.meta?.title || '页面')
const pageDescription = computed(
  () => route.meta?.description || '当前页面已接入新的前端目录，正在做功能对齐。'
)
</script>

<template>
  <section class="placeholder-page">
    <div class="hero">
      <span class="eyebrow">Frontend Alignment</span>
      <h1>{{ pageTitle }}</h1>
      <p>{{ pageDescription }}</p>
    </div>

    <div class="card-grid">
      <article class="info-card">
        <h2>当前状态</h2>
        <p>路由、应用壳层和构建链已经对齐到可运行结构。</p>
      </article>
      <article class="info-card">
        <h2>下一步</h2>
        <p>后续可以按页面逐个把真实业务组件和接口重新接回去。</p>
      </article>
      <article class="info-card">
        <h2>开发提示</h2>
        <p>现在可以先用 `npm run dev` 启动前端，再按模块恢复功能。</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.placeholder-page {
  display: grid;
  gap: 24px;
}

.hero {
  padding: 32px;
  border-radius: 24px;
  background:
    linear-gradient(135deg, rgba(17, 72, 171, 0.92), rgba(46, 128, 202, 0.82)),
    #1148ab;
  color: #fff;
  box-shadow: 0 24px 48px rgba(17, 72, 171, 0.18);
}

.eyebrow {
  display: inline-block;
  margin-bottom: 12px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  opacity: 0.78;
}

.hero h1 {
  margin: 0 0 10px;
  font-size: clamp(28px, 4vw, 44px);
  line-height: 1.1;
}

.hero p {
  margin: 0;
  max-width: 720px;
  font-size: 16px;
  line-height: 1.7;
  opacity: 0.92;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.info-card {
  padding: 20px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.05);
}

.info-card h2 {
  margin: 0 0 10px;
  color: #10233d;
  font-size: 18px;
}

.info-card p {
  margin: 0;
  color: #4b627c;
  line-height: 1.7;
}
</style>
