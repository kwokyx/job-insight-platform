function toFiniteNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function roundToTwo(value) {
  return Math.round(value * 100) / 100
}

const NON_MONTHLY_PATTERN = /(元\/?(?:天|日|次|小时|时)|\/(?:天|日|次|小时|时)|时薪|日结)/i
const ANNUAL_PATTERN = /(\/年|年薪)/i

function parseSalaryText(text) {
  const normalized = `${text ?? ''}`.trim()
  if (!normalized) return undefined
  if (NON_MONTHLY_PATTERN.test(normalized)) return null

  const numbers = normalized
    .match(/\d+(?:\.\d+)?/g)
    ?.map((item) => Number(item))
    .filter((item) => Number.isFinite(item)) || []

  if (!numbers.length) return null

  const first = numbers[0]
  const second = numbers[1] ?? numbers[0]

  let unitFactor = 1
  if (/万/.test(normalized)) {
    unitFactor = 10
  } else if (/千/.test(normalized)) {
    unitFactor = 1
  } else if (/元/.test(normalized)) {
    unitFactor = 0.001
  }

  const periodFactor = ANNUAL_PATTERN.test(normalized) ? 1 / 12 : 1
  const min = roundToTwo(Math.min(first, second) * unitFactor * periodFactor)
  const max = roundToTwo(Math.max(first, second) * unitFactor * periodFactor)

  return { min, max }
}

function parseSalaryFields(job = {}) {
  const minValue = toFiniteNumber(job.salaryMin ?? job.salary_min)
  const maxValue = toFiniteNumber(job.salaryMax ?? job.salary_max)

  if (minValue === null && maxValue === null) return null

  const normalizedMin = minValue ?? maxValue
  const normalizedMax = maxValue ?? minValue

  if (normalizedMin === null || normalizedMax === null) return null

  return {
    min: Math.min(normalizedMin, normalizedMax),
    max: Math.max(normalizedMin, normalizedMax)
  }
}

export function resolveComparableSalaryRange(job = {}) {
  const parsedText = parseSalaryText(job.salaryText ?? job.salary_text ?? job.salaryRaw ?? job.salary_raw ?? '')
  if (parsedText !== undefined) {
    return parsedText
  }
  return parseSalaryFields(job)
}

export function normalizeSalaryFilterRange(min, max) {
  const normalizedMin = min === '' ? '' : toFiniteNumber(min)
  const normalizedMax = max === '' ? '' : toFiniteNumber(max)

  const safeMin = normalizedMin === null || normalizedMin < 0 ? '' : normalizedMin
  const safeMax = normalizedMax === null || normalizedMax < 0 ? '' : normalizedMax

  if (safeMin !== '' && safeMax !== '' && safeMin > safeMax) {
    return { min: safeMax, max: safeMin }
  }

  return { min: safeMin, max: safeMax }
}

export function matchesSalaryRange(job, salaryMin, salaryMax) {
  const { min, max } = normalizeSalaryFilterRange(salaryMin, salaryMax)
  if (min === '' && max === '') return true

  const resolved = resolveComparableSalaryRange(job)
  if (!resolved) return false

  if (min !== '' && max !== '') {
    return resolved.min >= min && resolved.max <= max
  }
  if (min !== '') {
    return resolved.min >= min
  }
  return resolved.max <= max
}
