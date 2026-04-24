const { chromium } = require('playwright')
const fs = require('fs')
const path = require('path')

const baseUrl = 'http://127.0.0.1'
const apiBase = 'http://127.0.0.1/api/v1'
const artifactDir = 'C:/Users/32020/Desktop/occupational _competencies_platform/frontend/playwright-artifacts'

fs.mkdirSync(artifactDir, { recursive: true })

function solvePrompt(prompt) {
  const text = String(prompt || '').trim()
  const math = text.match(/(\d+)\s*([+\-x*脳])\s*(\d+)/i)
  if (math) {
    const a = Number(math[1])
    const op = math[2]
    const b = Number(math[3])
    return String(op === '+' ? a + b : op === '-' ? a - b : a * b)
  }

  const char = text.match(/[:：]\s*([A-Za-z0-9\s]+)$/)
  if (char) return char[1].replace(/\s+/g, '')

  throw new Error(`unsupported captcha prompt: ${text}`)
}

async function login() {
  const captchaRes = await fetch(`${apiBase}/auth/captcha`)
  const captchaJson = await captchaRes.json()
  const captcha = captchaJson.data
  const captchaCode = solvePrompt(captcha.captchaPrompt)
  const loginRes = await fetch(`${apiBase}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: 'codex_crawler_check_1777033986282',
      password: 'CodexCheck123',
      captchaId: captcha.captchaId,
      captchaCode
    })
  })
  const loginJson = await loginRes.json()
  if (loginJson.code !== 200) throw new Error(JSON.stringify(loginJson))
  return loginJson.data
}

async function textOf(page, selector) {
  const el = page.locator(selector).first()
  if (await el.count() === 0) return null
  const text = await el.textContent().catch(() => null)
  return text ? text.replace(/\s+/g, ' ').trim() : null
}

async function textsOf(page, selector, limit = 3) {
  const values = await page.locator(selector).evaluateAll((nodes, max) => {
    return nodes.slice(0, max).map((node) => node.textContent.replace(/\s+/g, ' ').trim())
  }, limit)
  return values || []
}

async function main() {
  const auth = await login()
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1200 } })
  await page.addInitScript((payload) => {
    localStorage.setItem('careerPlatform-access-token', payload.accessToken)
    localStorage.setItem('careerPlatform-refresh-token', payload.refreshToken || '')
    localStorage.setItem('careerPlatform-user', JSON.stringify(payload.user))
  }, auth)

  const pageStartedAt = Date.now()
  await page.goto(`${baseUrl}/crawler`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.locator('.collector-page').waitFor({ state: 'visible', timeout: 60000 })
  const pageReadyMs = Date.now() - pageStartedAt
  await page.screenshot({ path: path.join(artifactDir, 'crawler-fix-before-create.png'), fullPage: true })

  const taskName = `acceptance-${Date.now()}`
  await page.locator('.collector-hero-actions .glow-button').last().click()
  await page.locator('.task-form').waitFor({ state: 'visible', timeout: 30000 })
  const targetOptions = await page.locator('.task-form select').nth(1).locator('option').evaluateAll((nodes) => nodes.map((node) => node.textContent.trim()))
  await page.locator('.task-form input').nth(0).fill(taskName)
  await page.locator('.task-form select').nth(0).selectOption('801')
  await page.locator('.task-form input').nth(2).fill('Java')
  await page.locator('.task-form select').nth(1).selectOption('100')
  await page.locator('.task-form input[type="number"]').fill('5')
  await page.locator('.task-form .form-submit').click()
  await page.waitForTimeout(1500)

  const snapshots = []
  snapshots.push({
    pageReadyMs,
    successBanner: await textOf(page, '.success-banner'),
    liveTitle: await textOf(page, '.live-title'),
    liveDetail: await textOf(page, '.live-detail'),
    watchdogBadge: await textOf(page, '.watchdog-badge'),
    watchdogBody: await textOf(page, '.watchdog-body'),
    targetOptions
  })

  for (let round = 1; round <= 12; round += 1) {
    await page.waitForTimeout(1000)
    snapshots.push({
      round,
      topTitle: await textOf(page, '.task-row .task-title'),
      topProgress: await textOf(page, '.task-row .progress-count'),
      liveTitle: await textOf(page, '.live-title'),
      liveStatus: await textOf(page, '.live-title-row .pill'),
      liveDetail: await textOf(page, '.live-detail'),
      watchdogBadge: await textOf(page, '.watchdog-badge'),
      watchdogBody: await textOf(page, '.watchdog-body'),
      liveDataCards: await textsOf(page, '.live-data-card', 4),
      activeShards: await textsOf(page, '.shard-item-active', 2),
      completedShards: await textsOf(page, '.live-shard-card:last-child .shard-item', 2),
      successBanner: await textOf(page, '.success-banner'),
      errorBanner: await textOf(page, '.error-banner')
    })
    await page.screenshot({ path: path.join(artifactDir, `crawler-fix-round-${round}.png`), fullPage: true })
  }

  const row = page.locator('.task-row').first()
  if (await row.count()) {
    const finishStartedAt = Date.now()
    const finishButton = row.locator('.mini-action').nth(3)
    if (await finishButton.isVisible().catch(() => false)) {
      await finishButton.click().catch(() => {})
      await page.waitForTimeout(1200)
      snapshots.push({
        action: 'finish',
        ackMs: Date.now() - finishStartedAt,
        targetRowTitle: await textOf(row, '.task-title'),
        successBanner: await textOf(page, '.success-banner'),
        errorBanner: await textOf(page, '.error-banner'),
        rowStatus: await textOf(row, '.pill')
      })
    }
  }

  await page.screenshot({ path: path.join(artifactDir, 'crawler-fix-after-finish.png'), fullPage: true })
  await browser.close()
  console.log(JSON.stringify(snapshots, null, 2))
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
