import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1200', 'p(99)<2500']
  },
  scenarios: {
    open_api_overview: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      exec: 'openApiOverview'
    },
    open_api_insights: {
      executor: 'constant-vus',
      vus: 8,
      duration: '30s',
      exec: 'openApiInsights'
    },
    auth_captcha: {
      executor: 'constant-vus',
      vus: 4,
      duration: '20s',
      exec: 'authCaptcha'
    }
  }
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'

export function openApiOverview() {
  const res = http.get(`${BASE_URL}/api/v1/open/analysis/overview`)
  check(res, {
    'overview status ok': (r) => r.status === 200,
    'overview has code': (r) => String(r.json('code')) === '200'
  })
  sleep(0.5)
}

export function openApiInsights() {
  const res = http.get(`${BASE_URL}/api/v1/open/analysis/insights?industry=互联网&city=上海&months=12`)
  check(res, {
    'insights status ok': (r) => r.status === 200,
    'insights has sample': (r) => r.json('data.sample') !== null,
    'insights has recommendations': (r) => Array.isArray(r.json('data.recommendations'))
  })
  sleep(0.5)
}

export function authCaptcha() {
  const res = http.get(`${BASE_URL}/api/v1/auth/captcha?type=AUTO`)
  check(res, {
    'captcha status ok': (r) => r.status === 200,
    'captcha has id': (r) => !!r.json('data.captchaId'),
    'captcha has prompt': (r) => !!r.json('data.captchaPrompt')
  })
  sleep(0.2)
}
