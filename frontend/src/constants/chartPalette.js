export const chartPalette = {
  teal: '#8ECFC9',
  peach: '#FFBE7A',
  coral: '#FA7F6F',
  blue: '#82B0D2',
  lavender: '#BEB8DC',
  beige: '#E7DAD2',
  gray: '#999999',
  sage: '#A1C9A0',
  mauve: '#D6BFE3',
  gold: '#FFCE5C',
  // 10 色，避免 slice(0, 10) 撞色
  series: [
    '#82B0D2', '#8ECFC9', '#FA7F6F', '#BEB8DC', '#FFBE7A',
    '#A1C9A0', '#D6BFE3', '#FFCE5C', '#E7DAD2', '#999999'
  ]
}

export function withAlpha(hex, alpha) {
  const normalized = hex.replace('#', '')
  const hexValue = normalized.length === 3
    ? normalized.split('').map((char) => char + char).join('')
    : normalized

  const value = Number.parseInt(hexValue, 16)

  if (Number.isNaN(value)) {
    return hex
  }

  const r = (value >> 16) & 255
  const g = (value >> 8) & 255
  const b = value & 255

  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
