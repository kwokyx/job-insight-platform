<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileText,
  Info,
  LoaderCircle,
  PauseCircle,
  PlayCircle,
  Plus,
  RefreshCw,
  ShieldCheck,
  SquareX,
  TerminalSquare,
  X as CloseIcon
} from 'lucide-vue-next'
import GlowButton from '../components/common/GlowButton.vue'
import {
  createCrawlTask,
  fetchCrawlLiveOverview,
  fetchCrawlQuality,
  fetchCrawlTask,
  fetchCrawlTaskLogs,
  fetchCrawlTasks,
  normalizeError,
  updateCrawlTaskStatus
} from '../api'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()
const router = useRouter()

const SOURCE_CHANNEL = 'zhaopin'
const SOURCE_CHANNEL_LABEL = '\u667a\u8054\u62db\u8058'
const TARGET_COUNT_OPTIONS = [10, 20, 50, 100, 200, 300, 500, 800, 1000]
const WATCHDOG_TIMEOUT_MS = 30000
const WATCHDOG_INITIAL_GRACE_MS = 60000
const WATCHDOG_RESTART_COOLDOWN_MS = 30000
const SCHEDULER_FALLBACK_SYNC_MS = 6000
const DASHBOARD_REQUEST_TIMEOUT_MS = 12000
const TASK_ACTION_TIMEOUT_MS = 8000
const SCHEDULER_REQUEST_TIMEOUT_MS = 4000
const CITY_OPTIONS = [
  { code: '530', label: '\u5317\u4eac' },
  { code: '531', label: '\u5929\u6d25' },
  { code: '538', label: '\u4e0a\u6d77' },
  { code: '551', label: '\u91cd\u5e86' },
  { code: '635', label: '\u5357\u4eac' },
  { code: '653', label: '\u676d\u5dde' },
  { code: '736', label: '\u6b66\u6c49' },
  { code: '763', label: '\u5e7f\u5dde' },
  { code: '765', label: '\u6df1\u5733' },
  { code: '801', label: '\u6210\u90fd' }
]
const REGION_DISPLAY_MAP = {
  '530': '\u5317\u4eac',
  '531': '\u5929\u6d25',
  '538': '\u4e0a\u6d77',
  '551': '\u91cd\u5e86',
  '635': '\u5357\u4eac',
  '653': '\u676d\u5dde',
  '736': '\u6b66\u6c49',
  '763': '\u5e7f\u5dde',
  '765': '\u6df1\u5733',
  '801': '\u6210\u90fd'
}

const loading = ref(false)
const submitting = ref(false)
const logsLoading = ref(false)
const detailLoading = ref(false)
const statusUpdating = ref('')
const error = ref('')
const successMsg = ref('')

const tasks = ref([])
const totalTasks = ref(0)
const quality = ref({})
const liveOverview = ref({})
const logs = ref([])
const activeTaskId = ref('')
const trackedTask = ref(null)
const detailTask = ref(null)
const detailTaskId = ref('')

const pollTimer = ref(null)
const fastPollingUntil = ref(0)
const clockNow = ref(Date.now())
const clockTimer = ref(null)
const schedulerShardState = ref({
  taskId: '',
  loading: false,
  lastLoadedAt: 0,
  freshWorkerId: '',
  activeShards: [],
  completedShards: []
})
const watchdogState = ref({
  taskId: '',
  createdAt: 0,
  lastSignalAt: 0,
  lastSignalSourceAt: 0,
  lastSignature: '',
  armed: false,
  restartCount: 0,
  lastRestartAt: 0,
  restarting: false,
  message: ''
})

const filters = ref({
  channel: SOURCE_CHANNEL,
  status: ''
})

const taskPage = ref(1)
const taskPageSize = ref(10)
const createFormOpen = ref(false)
const taskForm = ref(defaultTaskForm())

function defaultTaskForm() {
  return {
    taskName: '',
    channel: SOURCE_CHANNEL,
    keywords: '',
    city: '801',
    targetCount: 20,
    priority: 5
  }
}

const taskTotalPages = computed(() => Math.max(1, Math.ceil((totalTasks.value || 0) / taskPageSize.value)))
const showInitialTaskLoading = computed(() => loading.value && tasks.value.length === 0)
const showTaskRefreshing = computed(() => loading.value && tasks.value.length > 0)
const freshnessRows = computed(() => quality.value?.freshness || [])
const selectedTask = computed(() =>
  tasks.value.find((item) => item.taskId === activeTaskId.value) || trackedTask.value || detailTask.value || null
)
const filteredRunningTasks = computed(() => (liveOverview.value?.runningTasks || []).filter((item) => !isRealtimeNoiseTask(item)))
const realtimeTask = computed(() => {
  if (trackedTask.value?.taskId === activeTaskId.value) {
    return trackedTask.value
  }

  const progress = liveOverview.value?.activeProgress
  if (progress && !isRealtimeNoiseTask(progress)) {
    return progress
  }

  const latest = liveOverview.value?.latestTask
  if (latest && !isRealtimeNoiseTask(latest)) {
    return latest
  }

  if (filteredRunningTasks.value.length > 0) {
    return filteredRunningTasks.value[0]
  }

  return selectedTask.value
})
const realtimeLatestLog = computed(() => {
  const taskId = realtimeTask.value?.taskId
  if (!taskId) return null

  if (taskId === activeTaskId.value && logs.value.length > 0) {
    return logs.value[0]
  }

  if (liveOverview.value?.activeProgress?.taskId === taskId && liveOverview.value?.activeProgress?.latestLog) {
    return liveOverview.value.activeProgress.latestLog
  }

  return (liveOverview.value?.latestLogs || []).find((item) => item.taskId === taskId) || null
})
const realtimeStageLabel = computed(() => {
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (runtimeStatus === 1) return '闂傚倷鐒﹁ぐ鍐洪鐐╂灃闁挎梻鏅埢?
  if (runtimeStatus === 0) return '闂備礁婀遍崕銈囨暜閳ユ緞锝夋晜閻ｅ备鏋?
  if (liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId && liveOverview.value?.activeProgress?.stageLabel) {
    return liveOverview.value.activeProgress.stageLabel
  }
  return getStatusMeta(runtimeStatus).label
})
const realtimeStageDetail = computed(() => {
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (runtimeStatus === 1) return '闁荤喐绮庢晶妤呭箰閸涘﹥娅犻柣妯虹－椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊栭幈銊モ攽閹捐泛鍩屽┑鐘亾妞ゅ繐鐗嗙粻銉ф喐閹达负鈧線骞嬮敂鑺ユ珫閻庡厜鍋撻柛鏇楁杹閸嬫挻寰勯幇顓ф濡炪値鍋掗崢鍓х玻濡ゅ懏鐓欓梻鈧幇顖氬帯闂佹眹鍨归…宄扮暦濮樿埖鐓ラ悗锝庡亞閸樻劙姊绘担鐟扮祷婵炲樊鍘奸埢鏃堟晜閼恒儰姘﹀┑鐐村灦閿氭い鏂匡躬閺屾稑顭ㄩ崘顓烆伃闂佹眹鍊曠€氭澘顕ｉ鈧幊婊堝垂椤愵剛绀嗛梻濠庡亜濞诧箓濡靛鍫濈劦?
  if (runtimeStatus === 0) return '濠电偛顕慨楣冾敋瑜庨幈銊╂偄婵傚鏅犻梺鍦帛鐢帡鎮橀敍鍕ㄥ亾閻愮懓鈧浜搁妸褎顫曟繝闈涙－濞间即鏌ㄥ┑鍡樺櫤闂婎剦鍓涚槐鎺戔槈濮楀棙笑缂備礁澧庨崰鎾诲箯椤愶箑绀冮柕濞垮労閸炵儤绻涢幋鐐存儎闁告鍋愮紓鎾淬偅閸愩劎顦梺绯曞墲椤ㄥ懐寰婂ú顏呯厵閻庢稒顭囨晶顒傜磼娴ｈ棄鍚归柟鍙夋尦瀹曠厧鈹戦崶鈺佺稊闂佽娴烽幊鎾诲嫉椤掑嫬鍨傛慨妯垮煐閻撳嫰鎮楀☉娅虫垹绱為埀顒勬⒑閸涘﹤绗氱紒璇插€块敐鐐哄箛閺夎法顦遍梺鍝勭Р閸庮噣宕?
  if (liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId && liveOverview.value?.activeProgress?.stageDetail) {
    return liveOverview.value.activeProgress.stageDetail
  }

  if (runtimeStatus === 1) return '闁荤喐绮庢晶妤呭箰閸涘﹥娅犻柣妯虹－椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊栭幈銊モ攽閹捐泛鍩屽┑鐘亾妞ゅ繐鐗嗙粻銉ф喐閹达负鈧線骞嬮敂鑺ユ珫閻庡厜鍋撻柛鏇楁杹閸嬫挻寰勯幇顓ф濡炪値鍋掗崢鍓х玻濡ゅ懏鐓欓梻鈧幇顖氬帯闂佹眹鍨归…宄扮暦濮樿埖鐓ラ悗锝庡亞閸樻劙姊绘担鐟扮祷婵炲樊鍘奸埢鏃堟晜閼恒儰姘﹀┑鐐村灦閿氭い鏂匡躬閺屾稑顭ㄩ崘顓烆伃闂佹眹鍊曠€氭澘顕ｉ鈧幊婊堝垂椤愵剛绀嗛梻濠庡亜濞诧箓濡靛鍫濈劦?
  if (runtimeStatus === 2) return '濠电偛顕慨楣冾敋瑜庨幈銊╂偄婵傚鏅犻梺鍦帛鐢偤寮冲鑸电厵閻庢稒锚婵呯磼鏉堛劎绠氶柕鍥ㄥ姍楠炴﹢宕樺顔界€梺璇茬箳閸嬬偛煤濠婂牆桅婵鍩栭崕宥夋煕閺囥劌澧粭鎴︽⒑閸濆嫮澧愰柛瀣崌瀵爼鍩￠崒姘变化缂備焦姊瑰娆撳煝鎼淬劍鏅搁柣妯哄级閻濅即姊婚崒姘棞婵☆偅绋撻崚鎺楀Ω閳轰礁鍤戝┑鐘欏啰姘ㄩ柛?
  if (runtimeStatus === 3) return '濠电偛顕慨楣冾敋瑜庨幈銊╂偄婵傚鏅犻梺鑲┾拡閸撴稑鈻旈姀銈嗙厸濠㈣泛鑻弸鎴︽煕閵婏絽濡界€垫澘瀚蹇涱敃閵夋劖娲熼弻銊モ槈濡偐鍔銈嗘处閸撶喎顕ｆ繝姘ㄧ憸搴ｇ不濞嗗繆妲堥柟鐐墯閸庢劙鏌″畝鈧崰鏍ь嚕椤曗偓婵＄兘濡疯椤斿秶绱掔紒銏犲季闁哥姵鐩、妯荤節濮橆剙鍋嶉梺缁樻閺€杈╃矆閸儲鐓?
  return '濠电偛顕慨楣冾敋瑜庨幈銊╂偄婵傚鏅犻梺鍦帛鐢帡鎮橀敍鍕ㄥ亾閻愮懓鈧浜搁妸褎顫曟繝闈涚墢妞瑰啿顭跨捄鐚村姛缂佹劧绻濋幃鍦偓锝庝簻閺嗘瑩鎮楃涵鍛彧缂佸顦甸、姗€鎮欓棃娑辨喘闁诲孩顔栭崰鎺楀磻閹炬枼鏀芥い鏃傗拡閸庢劕顭胯閺咁偊骞冮幍顔绘勃闁绘垟鏅涙禍?
})
const realtimeShardStats = computed(() => {
  const stats = liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId
    ? liveOverview.value?.activeProgress?.shardStats
    : null
  return {
    total: Number(stats?.total || 0),
    pending: Number(stats?.pending || 0),
    running: Number(stats?.running || 0),
    completed: Number(stats?.completed || 0),
    failed: Number(stats?.failed || 0)
  }
})
const realtimeShardLogFallback = computed(() => {
  const taskId = realtimeTask.value?.taskId
  if (!taskId) {
    return { activeShards: [], completedShards: [] }
  }

  const candidates = []
  if (taskId === activeTaskId.value && Array.isArray(logs.value)) {
    candidates.push(...logs.value)
  }
  if (realtimeLatestLog.value) {
    candidates.push(realtimeLatestLog.value)
  }

  return parseRealtimeShardsFromLogs(candidates, taskId)
})
const realtimeSchedulerShardFallback = computed(() => {
  return schedulerShardState.value?.taskId === realtimeTask.value?.taskId
    ? schedulerShardState.value
    : { activeShards: [], completedShards: [] }
})
function shardIdentity(item) {
  if (!item) return ''
  if (item.shardId) return `id:${item.shardId}`
  return [
    item.page ?? '',
    item.keyword ?? '',
    resolveCityDisplayName(item.city ?? '')
  ].join('|')
}

function enrichRealtimeShards(primary, fallback, fallbackWorkerId = '') {
  if (!Array.isArray(primary) || primary.length === 0) {
    return Array.isArray(fallback) ? fallback : []
  }
  const fallbackMap = new Map(
    (Array.isArray(fallback) ? fallback : [])
      .map((item) => [shardIdentity(item), item])
      .filter(([key]) => key)
  )
  return primary.map((item) => {
    const matched = fallbackMap.get(shardIdentity(item))
    return {
      ...(matched || {}),
      ...item,
      city: resolveCityDisplayName(item?.city || matched?.city || ''),
      workerId: item?.workerId || item?.worker_id || matched?.workerId || matched?.worker_id || fallbackWorkerId
    }
  })
}
const realtimeActiveShards = computed(() => {
  const raw = liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId && Array.isArray(liveOverview.value?.activeProgress?.activeShards)
    ? liveOverview.value.activeProgress.activeShards
    : []
  if (raw.length) return enrichRealtimeShards(raw, realtimeSchedulerShardFallback.value.activeShards, realtimeSchedulerShardFallback.value.freshWorkerId)
  if (realtimeSchedulerShardFallback.value.activeShards?.length) return realtimeSchedulerShardFallback.value.activeShards
  return realtimeShardLogFallback.value.activeShards
})
const realtimeCompletedShards = computed(() => {
  const raw = liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId && Array.isArray(liveOverview.value?.activeProgress?.completedShards)
    ? liveOverview.value.activeProgress.completedShards
    : []
  if (raw.length) return enrichRealtimeShards(raw, realtimeSchedulerShardFallback.value.completedShards, realtimeSchedulerShardFallback.value.freshWorkerId)
  if (realtimeSchedulerShardFallback.value.completedShards?.length) return realtimeSchedulerShardFallback.value.completedShards
  return realtimeShardLogFallback.value.completedShards
})
const realtimeDataCards = computed(() => {
  const task = realtimeTask.value || {}
  return [
    { label: '闁诲骸婀遍…鍫濐嚕閼稿灚鍙忛柛鏇ㄥ灠閻?, value: crawledCount(task), note: `闂備胶鍎甸弲鈺呭窗濡ゅ懏鍋?${targetCount(task) || '--'} 闂備礁鎼ˇ浠嬪箺?},
    { label: '闂備礁鎼崐鐟邦熆濮椻偓璺?, value: safeNumber(task?.newCount), note: '闂備礁鎼悧婊堝礈濠靛鍋柛鈩冪☉缁€鍐╃箾閸℃绠扮€殿喗濞婇弻锝夊Ω閵夈儺浠鹃梺鍝勮嫰閿曨亪骞婇弴鐘辨勃闁兼祴鏅濆瓭闂? },
    { label: '闂備礁鎼ú銈夋偤閵娾晛钃?, value: safeNumber(task?.updatedCount), note: '闁诲骸婀遍…鍫濐嚕鐠虹尨鑰垮〒姘ｅ亾闁硅櫕娲滄禒锕傛嚃閳哄唭銏ゆ⒑濮瑰洤濡奸悗姘煎幖鐓ら柛褎顨呭Λ姗€鏌ｅΔ鈧悧濠囨嫃鐎ｎ喗鐓? },
    { label: '闂備礁鎲￠敋妞ゎ厾鍏樺畷?, value: safeNumber(task?.duplicateCount), note: '闂備礁鎲￠敋妞ゎ厾鍏樺畷鎶藉川婵犲啩姘﹂梺鎼炲劘閸斿秵鎱ㄩ姀銈嗙厽闁靛鍎遍顓㈡煕閿濆懏鍟炵€垫澘瀚濂稿醇椤愩垺鏆柣? }
  ]
})
const realtimeWatchdogVisible = computed(() => {
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  return runtimeStatus === 0 || runtimeStatus === 1 || watchdogState.value.restarting || watchdogState.value.restartCount > 0
})
const realtimeWatchdogCountdown = computed(() => {
  if (watchdogState.value.restarting) return 0
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (runtimeStatus !== 0 && runtimeStatus !== 1) return null
  if (watchdogState.value.armed) {
    if (!watchdogState.value.lastSignalAt) return Math.ceil(WATCHDOG_TIMEOUT_MS / 1000)
    const remainMs = WATCHDOG_TIMEOUT_MS - (clockNow.value - watchdogState.value.lastSignalAt)
    return Math.min(Math.ceil(WATCHDOG_TIMEOUT_MS / 1000), Math.max(0, Math.ceil(remainMs / 1000)))
  }
  const remainMs = WATCHDOG_INITIAL_GRACE_MS - (clockNow.value - (watchdogState.value.createdAt || clockNow.value))
  return Math.min(Math.ceil(WATCHDOG_INITIAL_GRACE_MS / 1000), Math.max(0, Math.ceil(remainMs / 1000)))
})
const realtimeWatchdogText = computed(() => {
  if (watchdogState.value.restarting) {
    return watchdogState.value.message || '闁荤喐绮庢晶妤呭箰閸涘﹥娅犻柣妯虹－椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊濆濠氬焵椤掑嫬绠伴幖娣焺濡差垶姊婚崒姘偓鎼佹偤閵娾晜鍎夐柛娑欐綑鐎氬顭跨捄渚剱闁绘挴鍋撻梺鍝勵槴閺呮粎绮欓幋鐘亾鐟欏嫬鈻曢柡浣哥Т閻ｆ繈鍩€椤掑嫭鐒鹃悗闈涙憸绾惧ジ鏌ｉ弬鍨棌闁告柡鍋撻梻渚€娼荤拹鐔煎礉韫囨稑鍚规繝濠傜墕缁€澶愭煏婵炑冩噺鐏忔繈姊洪崫鍕垫Ш闁哥姴閰ｉ幊鐔兼偄閸忚偐鍘掗悗骞垮劚閹冲繘宕曞▎鎾寸厪?
  }
  if (watchdogState.value.message) {
    return watchdogState.value.message
  }
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (runtimeStatus !== 0 && runtimeStatus !== 1) {
    return watchdogState.value.restartCount > 0
      ? `闂備礁鎼悧婊堝礂濞戙垹绠查柕蹇嬪€曠粈澶愭煃閳轰礁鏆炲ù鐘筹耿瀵爼鍩￠崒姘变淮闂佸憡鍩婄换婵嬪箠濞戙埄鏁傞柛鏇ㄥ€ｅΔ鍛拺闁圭粯甯炲瓭闂?${watchdogState.value.restartCount} 婵犵數鍋涢弸鎾箥閸愯弓澹曟繛?
      : '闁荤喐绮庢晶妤呭箰閸涘﹥娅犻柣妯荤ゴ閺岋箓鏌嶉埡浣告殲缂佺姵甯″濠氬炊閿濆懍澹曢梺鑽ゅ枑濞叉垵霉閸ヮ剙鍚规繝濠傜墕缁€澶愭煏婵炵偓娅呮繛鍛灲閺屾稑顫濋悡搴ｄ化濠电偛鐗婇崹鐢靛弲闁荤姴娲﹁ぐ鍐杽濠电偛顕慨楣冾敋瑜庨幈銊╂偄閻撳宫?
  }
  const remain = realtimeWatchdogCountdown.value
  if (!watchdogState.value.armed) {
    return `濠电偛顕慨楣冾敋瑜庨幈銊╂偄婵傚妗ㄩ梺鎸庣箓閹冲繘鐓鍌滅＜婵炴垶锕╁Σ鍝ョ磼閸撲礁鏋涢柡灞芥捣閳ь剚绋掕摫缂傚秮鍋撻梻渚€娼уΛ鏃傜矆娓氣偓瀹曡鎯旈妸銉х厬闂佺懓顕崑娑㈡倶濡も偓铻為柨婵嗘婢ь剚绻涚喊鍗炵仭婵炶壈顕ч埥澶娢熸笟顖氭暯闂備焦鎮堕崕鎶藉磻濡吋顫曢柛顐ｆ礃閺咁剚绻涢幋鐐电煀妞?${remain ?? Math.ceil(WATCHDOG_INITIAL_GRACE_MS / 1000)} 缂傚倷绀侀ˇ鎵暜閹烘鑸归悗娑欘焽椤╅鈧箍鍎卞Λ娆撴晸閵夆晜鐓曟繝闈涙瀛濈紓浣靛妽閻擄繝寮鍛殕闁逞屽墴閺屽牏鈧潧鎽滅壕濂告煟閺冨偆鐒炬い銈呮噹闇夐柛蹇涙？娴溿垽鏌涢妶鍡欑煉鐎规洘绻堟俊鎼佸煛娴ｈ浠ч梻浣告啞閸戝綊宕归鍕劦妞ゆ垼娉曠粻?
  }
  return `濠电姷顣介埀顒€鍟块埀顒€缍婇幃妯诲緞鐎ｎ偂姘﹂梺缁樺姉閺佹悂寮?${remain ?? Math.ceil(WATCHDOG_TIMEOUT_MS / 1000)} 缂傚倷绀侀ˇ鎵暜濡ゅ懏鍎夐柛娑欐綑鐎氬顭跨捄渚剱闁绘挴鍋撻梻浣圭湽閸斿瞼鈧凹鍨抽幑銏犖熺紒妯哄妳闂佹寧妫侀妴鈧柛瀣尰閹峰懐鎲撮崟鍓佺闂傚鍋勫ú锕傘€冮崱娑樺瀭闁靛ň鏅涚粈鍡涙煕閳╁啰鎳冩い锝咁煼閺屾稑鈻庨幆濂変簽閳ь剙鐏氶悡锟犲极瀹ュ懐鏆嗛柍褜鍓熼弻鍫⑩偓闈涙憸绾惧ジ鏌ｉ弬鍨暢缂佹劖顨婇弻銈嗙附婢跺鐩庢繝娈垮枓閺呮繄妲愰幒妤€绠婚悗娑櫭惃銏犖旈悩闈涗粶闁绘牕銈稿畷鎶藉箹娴ｆ瓕袝濡炪倖鐗楀銊х矓婵傚憡鐓曢柟鎯х－灏忛梺鐟扮畭閸ㄨ棄鐣峰┑鍥х疇闂侀€炲苯鍘哥紒?
})
const qualityCards = computed(() => {
  const q = quality.value || {}
  const completeness = q.completeness || {}
  return [
    {
      label: '闁诲骸鍘滈崑鎾翠繆閻愭彃鈷旂紒澶嬫尦閺岀喖顢楅埀顒勨€﹂悜钘夐棷?,
      value: q.totalJobs ?? '--',
      note: '闁荤喐绮庢晶妤呭箰閸涘﹥娅犻柣妯肩帛閸ゅ倿鎮橀悙璺轰汗缂佸鎸抽獮鏍偓娑櫳戝畷鍕亜椤愩埄妲搁摶?
    },
    {
      label: '闂備礁鎼粔鏉懨洪顫偓鍌炴偩鐏炵浜鹃柣鐔哄濠€浼存煛閸☆厾绉柟?,
      value: completeness.titleRate || '--',
      note: '闂備胶鍘у畷顒佺附閺冨倻绀婇柛娑欐綑閸愨偓闂佹悶鍎洪崜锕傚汲椤栫偞鍊垫繛鎴炵懐濞堟洘銇勯弬璺ㄧ劯鐎殿喖鐏氬鍕偓锝庡亝閻濓繝姊洪崨濠庢畼濠殿喚鏁婚幆?
    },
    {
      label: '闂備浇濮ら悧顒佺閿濆洨鐭堟い鎰╁€愰崑鎾绘偡閻楀牊鎷遍梺鍝勬４缂嶄線骞?,
      value: completeness.salaryRate || '--',
      note: '闂備浇濮ら悧顒佺閿濆洨鐭堟い鎰╁€愰崑鎾斥槈濞嗘ɑ鐣峰銈嗘煥閻倸顕ｉ崹顐㈢窞閻庯綆鍋呴悵锟犳⒑閸涘娈樺┑顔炬暬閹?
    },
    {
      label: '闂備焦鐪归崐鏍垂閹惰棄绠柍褜鍓熼弻娑橆吋婢跺閿┑鈩冾殔閻楀棝鎮?,
      value: q.suspectedZombieJobRate || '--',
      note: `闂備焦鐪归崐鏍垂閹惰棄绠柍褜鍓熷鍫曞煛娴ｇ懓顦╁┑鐘亾?${q.suspectedZombieJobs ?? 0} 闂備礁鎼ˇ浠嬪箺?
    }
  ]
})
const detailMetaRows = computed(() => {
  const task = detailTask.value
  if (!task) return []
  return [
    { label: '濠电偛顕慨楣冾敋瑜庨幈?ID', value: task.taskId || '--' },
    { label: '闂備胶绮悧妤€锕㈣ぐ鎺戠闁靛繈鍊曠粈?, value: task.parentTaskId || '--' },
    { label: '婵犵數鍋為幐鐐箾閳ь剙霉?, value: formatChannel(task.channel) },
    { label: '闂備胶纭堕弲娑欘殽閸濄儳鍗?, value: formatCityList(task.city, '闂備胶顭堢换鍫ュ礉瀹€鍕亗?) },
    { label: '闂備胶顭堢换鎴炵箾婵犲洤鏋佹い鎾跺У鐎?, value: formatList(task.keywords, '--') },
    { label: '濠电偞娼欓崥瀣晪闂佸憡蓱缁嬫捇鎯€?, value: `P${task.priority ?? 5}` },
    { label: '闂備胶绮…鍫ュ春閺嶎厼鐒?, value: getStatusMeta(task.status).label },
    { label: '闂備胶鍎甸弲鈺呭窗濡ゅ懏鍋夐柨婵嗩槸缁狙囨煃閳轰礁鏆炴繛?, value: targetCount(task) || '闂備礁鎼悧婊勭閿濆鏁婇柡鍥╁Х绾? },
    { label: '闁诲海鎳撻幉锟犳偂閿熺姴闂ù鐓庣摠閳?, value: crawledCount(task) },
    { label: '闂備礁鎲￠敋妞ゎ厾鍏樺畷?, value: task.duplicateCount ?? 0 },
    { label: '闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎯ь嚟椤?, value: task.createUser || '--' },
    { label: '闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鎹愵嚙缁秹鏌涢锝嗙闁?, value: formatTime(task.createTime) },
    { label: '闁诲孩顔栭崰鎺楀磻閹炬枼鏀芥い鏃傗拡閸庢劖淇婇悙鎻掆偓鍨潖?, value: formatTime(task.startTime) },
    { label: '缂傚倸鍊烽悞锕傚箰鐠囧樊鐒芥い鎰剁畱缁秹鏌涢锝嗙闁?, value: formatTime(task.endTime) },
    { label: '闂備礁鎼ú銈夋偤閵娾晛钃熷┑鐘叉搐缁秹鏌涢锝嗙闁?, value: formatTime(task.updateTime) }
  ]
})

function setFeedback(type, message) {
  if (type === 'error') {
    error.value = message
    successMsg.value = ''
    return
  }
  successMsg.value = message
  error.value = ''
}

function isRealtimeNoiseTask(task) {
  if (!task) return false
  const taskId = String(task.taskId || '')
  const taskName = String(task.taskName || '').toLowerCase()
  const scheduleType = String(task.scheduleType || '').toUpperCase()
  const createUser = String(task.createUser || '').toLowerCase()

  return taskId.startsWith('probe_')
    || taskName.includes('probe')
    || scheduleType === 'SCHEDULED_TEMPLATE'
    || createUser === 'probe'
    || createUser === 'system-probe'
}

function getStatusMeta(status) {
  const map = {
    0: { label: '闂備礁婀遍崕銈囨暜閳ユ緞锝夋晜閻ｅ备鏋?, tone: 'idle', icon: Clock3 },
    1: { label: '闂佸搫顦弲婊堝礉濮椻偓閵嗕線骞嬮悙纰樻灃?, tone: 'running', icon: LoaderCircle },
    2: { label: '闁诲海鎳撻幉陇銇愰崘顔藉仼妞ゆ帒瀚粻?, tone: 'done', icon: CheckCircle2 },
    3: { label: '闁诲氦顫夐悺鏇犱焊濞嗘垵鍨濋柕濞炬櫅缁?, tone: 'paused', icon: PauseCircle }
  }
  return map[status] || { label: '闂備礁鎼悧婊勭閻愮儤鍋?, tone: 'idle', icon: Activity }
}

function deriveTaskRuntimeStatus(task) {
  if (!task) return null
  const rawStatus = Number(task.status)
  const total = targetCount(task)
  const crawled = crawledCount(task)

  if (total > 0 && crawled > 0 && crawled < total) {
    return 1
  }

  if (rawStatus === 3 && total > 0 && !task?.endTime && crawled < total) {
    return crawled > 0 ? 1 : 0
  }

  if ((rawStatus === 0 || rawStatus === 3) && isTaskRecentlyQueued(task, total, crawled)) {
    return 0
  }

  return rawStatus
}

function isTaskRecentlyQueued(task, total, crawled) {
  if (total <= 0) return false
  if (crawled > 0) return false
  if (task?.endTime) return false

  const startTs = parseTaskTimestamp(task?.startTime)
  const createTs = parseTaskTimestamp(task?.createTime)
  const updateTs = parseTaskTimestamp(task?.updateTime)
  const latestTs = Math.max(startTs, createTs, updateTs)
  if (!latestTs) return false

  return Date.now() - latestTs <= 2 * 60 * 1000
}

function parseTaskTimestamp(value) {
  if (!value) return 0
  const normalized = String(value).replace(' ', 'T')
  const parsed = Date.parse(normalized)
  return Number.isFinite(parsed) ? parsed : 0
}

function formatTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatChannel(channel) {
  return channel === SOURCE_CHANNEL || !channel ? SOURCE_CHANNEL_LABEL : channel
}

function formatList(value, fallback = '--') {
  if (Array.isArray(value)) {
    return value.length ? value.join(' / ') : fallback
  }
  return value || fallback
}

function resolveCityDisplayName(value) {
  if (value == null) return ''
  const text = String(value).trim()
  if (!text) return ''
  return REGION_DISPLAY_MAP[text] || text
}

function formatCityList(value, fallback = '--') {
  if (Array.isArray(value)) {
    const items = value.map(resolveCityDisplayName).filter(Boolean)
    return items.length ? items.join(' / ') : fallback
  }
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return fallback
    if ((text.startsWith('[') && text.endsWith(']')) || (text.startsWith('{') && text.endsWith('}'))) {
      try {
        return formatCityList(JSON.parse(text), fallback)
      } catch (e) {
        return resolveCityDisplayName(text)
      }
    }
    if (text.includes(',')) {
      const items = text.split(',').map(resolveCityDisplayName).filter(Boolean)
      return items.length ? items.join(' / ') : fallback
    }
    return resolveCityDisplayName(text)
  }
  return resolveCityDisplayName(value) || fallback
}

function safeNumber(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function crawledCount(task) {
  return Math.max(safeNumber(task?.crawledCount), safeNumber(task?.finishedCount))
}

function targetCount(task) {
  return Math.max(safeNumber(task?.targetCount), safeNumber(task?.totalCount))
}

function progressPercent(task) {
  const total = targetCount(task)
  const crawled = crawledCount(task)
  if (!total) return crawled > 0 ? 100 : 0
  return Math.max(0, Math.min(100, Math.round((crawled / total) * 100)))
}

function progressText(task) {
  const total = targetCount(task)
  const crawled = crawledCount(task)
  if (total > 0) {
    return `${crawled} / ${total}`
  }
  return `${crawled} 闂備礁鎼ˇ浠嬪箺?
}

function formatShardSummary(shard) {
  if (!shard) return '--'
  const parts = []
  if (shard.page != null) parts.push(`濠碉紕鍋戦崐妤呭极閹间焦鍋?${shard.page}`)
  if (shard.keyword) parts.push(`闂備胶顭堢换鎴炵箾婵犲洤鏋佹い鎾跺У鐎?${shard.keyword}`)
  if (shard.city) parts.push(`闂備胶纭堕弲娑欘殽閸濄儳鍗?${formatList(shard.city, '--')}`)
  return parts.join(' / ') || '--'
}

function formatShardResult(shard) {
  if (!shard) return '--'
  if (safeNumber(shard.collectedCount) > 0) {
    return `闂傚倷鐒﹁ぐ鍐洪鐐╂灃?${safeNumber(shard.collectedCount)} 闂備礁鎼ˇ浠嬪箺?
  }
  return `闂備礁鎼崐鐟邦熆濮椻偓璺?${safeNumber(shard.newCount)} / 闂備礁鎼ú銈夋偤閵娾晛钃?${safeNumber(shard.updatedCount)} / 闂備礁鎲￠敋妞ゎ厾鍏樺畷?${safeNumber(shard.duplicateCount)}`
}

function parseRealtimeShardsFromLogs(items, taskId) {
  if (!Array.isArray(items) || !items.length || !taskId) {
    return { activeShards: [], completedShards: [] }
  }

  const startPattern = /闁诲孩顔栭崰鎺楀磻閹炬枼鏀芥い鏃傗拡閸庡繑銇勯敂瑙勬珚闁诡喖鐖煎畷閬嶅即閻斿嘲绨ラ梻浣告啞閺岋繝鍩€椤掆偓閸熷潡鎮楁繝姘厽?\s*shard_id=([^,\s]+),\s*keyword=([^,]+),\s*city=([^,]+),\s*page=(\d+)/i
  const donePattern = /濠电偛顕慨楣冾敋瑜庨幈銊╂偄閻撳海顦梺绯曞墲閻熴儵銆傛繝姘€甸柣鐔哄濠€浼存煕?\s*shard_id=([^,\s]+),\s*闂傚倷鐒﹁ぐ鍐洪鐐╂灃闁挎洖鍊搁弸渚€鏌ｅΔ鈧悧鍡欑矈?\d+)闂?i
  const shardMap = new Map()

  const sortedItems = [...items]
    .filter((item) => !item?.taskId || item.taskId === taskId)
    .sort((a, b) => parseTaskTimestamp(a?.createTime) - parseTaskTimestamp(b?.createTime))

  sortedItems.forEach((item) => {
    const message = String(item?.message || item?.logMessage || item?.content || '').trim()
    if (!message) return

    const startMatch = message.match(startPattern)
    if (startMatch) {
      const [, shardId, keyword, city, page] = startMatch
      const current = shardMap.get(shardId) || { shardId }
      shardMap.set(shardId, {
        ...current,
        shardId,
        keyword: keyword?.trim(),
        city: resolveCityDisplayName(city?.trim()),
        page: safeNumber(page, null),
        workerId: current.workerId || item?.workerId || item?.worker || '',
        status: 1,
        startTime: current.startTime || item?.createTime || current.startTime,
        updateTime: item?.createTime || current.updateTime
      })
      return
    }

    const doneMatch = message.match(donePattern)
    if (doneMatch) {
      const [, shardId, collectedCount] = doneMatch
      const current = shardMap.get(shardId) || { shardId }
      shardMap.set(shardId, {
        ...current,
        shardId,
        status: 2,
        collectedCount: safeNumber(collectedCount),
        endTime: item?.createTime || current.endTime,
        updateTime: item?.createTime || current.updateTime
      })
    }
  })

  const allShards = [...shardMap.values()]
  const activeShards = allShards
    .filter((item) => item.startTime && !item.endTime)
    .sort((a, b) => parseTaskTimestamp(b?.startTime || b?.updateTime) - parseTaskTimestamp(a?.startTime || a?.updateTime))
    .slice(0, 6)
  const completedShards = allShards
    .filter((item) => item.endTime)
    .sort((a, b) => parseTaskTimestamp(b?.endTime || b?.updateTime) - parseTaskTimestamp(a?.endTime || a?.updateTime))
    .slice(0, 6)

  return { activeShards, completedShards }
}

function schedulerApiBase() {
  if (typeof window === 'undefined') return ''
  return `${window.location.protocol}//${window.location.hostname}:8001/api`
}

function normalizeSchedulerShard(item) {
  return {
    shardId: item?.shard_id || item?.shardId || '',
    taskId: item?.task_id || item?.taskId || '',
    page: item?.page,
    keyword: item?.keyword || '',
    city: resolveCityDisplayName(item?.city || ''),
    categoryCode: item?.category_code || item?.categoryCode || '',
    status: item?.status,
    retryCount: item?.retry_count || item?.retryCount || 0,
    stopReason: item?.stop_reason || item?.stopReason || '',
    newCount: safeNumber(item?.new_count ?? item?.newCount),
    updatedCount: safeNumber(item?.updated_count ?? item?.updatedCount),
    duplicateCount: safeNumber(item?.duplicate_count ?? item?.duplicateCount),
    workerId: item?.worker_id || item?.workerId || '',
    startTime: item?.start_time || item?.startTime || '',
    endTime: item?.end_time || item?.endTime || '',
    collectedCount: safeNumber(item?.collected_count ?? item?.collectedCount ?? item?.new_count ?? item?.updatedCount ?? item?.updated_count)
  }
}

function resolveFreshWorkerId(items) {
  const now = Date.now()
  const freshWorkers = (Array.isArray(items) ? items : []).filter((item) => {
    if (!item?.worker_id) return false
    if (Number(item?.status) !== 1) return false
    const rawHeartbeat = item?.last_heartbeat || item?.lastHeartbeat
    const heartbeatAt = String(rawHeartbeat || '').match(/Z|[+-]\d{2}:\d{2}$/)
      ? parseTaskTimestamp(rawHeartbeat)
      : Date.parse(String(rawHeartbeat || '').replace(' ', 'T') + 'Z')
    return heartbeatAt > 0 && now - heartbeatAt <= 2 * 60 * 1000
  })
  if (freshWorkers.length !== 1) return ''
  return freshWorkers[0].worker_id || ''
}

async function syncSchedulerShardFallback(force = false) {
  const taskId = realtimeTask.value?.taskId
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (!taskId) {
    schedulerShardState.value = {
      taskId: '',
      loading: false,
      lastLoadedAt: 0,
      freshWorkerId: '',
      activeShards: [],
      completedShards: []
    }
    return
  }
  if (runtimeStatus !== 0 && runtimeStatus !== 1 && !force) {
    return
  }

  const now = Date.now()
  if (!force && schedulerShardState.value.taskId === taskId && now - schedulerShardState.value.lastLoadedAt < SCHEDULER_FALLBACK_SYNC_MS) {
    return
  }
  if (schedulerShardState.value.loading) {
    return
  }

  schedulerShardState.value = {
    ...schedulerShardState.value,
    taskId,
    loading: true
  }

  try {
    const base = schedulerApiBase()
    const [activeRes, completedRes, workersRes] = await Promise.all([
      fetchWithTimeout(`${base}/tasks/${taskId}/shards?page=1&size=6&status=1`, SCHEDULER_REQUEST_TIMEOUT_MS),
      fetchWithTimeout(`${base}/tasks/${taskId}/shards?page=1&size=6&status=2`, SCHEDULER_REQUEST_TIMEOUT_MS),
      fetchWithTimeout(`${base}/workers?page=1&size=50`, SCHEDULER_REQUEST_TIMEOUT_MS)
    ])
    const [activeJson, completedJson, workersJson] = await Promise.all([activeRes.json(), completedRes.json(), workersRes.json()])
    const freshWorkerId = resolveFreshWorkerId(workersJson?.data?.items)
    const activeShards = Array.isArray(activeJson?.data?.items) ? activeJson.data.items.map(normalizeSchedulerShard) : []
    const completedShards = Array.isArray(completedJson?.data?.items) ? completedJson.data.items.map(normalizeSchedulerShard) : []
    schedulerShardState.value = {
      taskId,
      loading: false,
      lastLoadedAt: now,
      freshWorkerId,
      activeShards: activeShards.map((item) => ({ ...item, workerId: item.workerId || freshWorkerId })),
      completedShards: completedShards.map((item) => ({ ...item, workerId: item.workerId || freshWorkerId }))
    }
  } catch (e) {
    schedulerShardState.value = {
      ...schedulerShardState.value,
      taskId,
      loading: false,
      lastLoadedAt: now
    }
  }
}

function latestSignalTimestamp(task, latestLog, activeShards, completedShards) {
  let latest = 0
  const candidates = [
    task?.startTime,
    task?.endTime,
    latestLog?.createTime
  ]
  activeShards.forEach((item) => {
    candidates.push(item?.startTime, item?.endTime)
  })
  completedShards.forEach((item) => {
    candidates.push(item?.startTime, item?.endTime)
  })
  candidates.forEach((value) => {
    latest = Math.max(latest, parseTaskTimestamp(value))
  })
  return latest
}

function hasRealtimeProgressSignal(task, latestLog, activeShards, completedShards) {
  if (crawledCount(task) > 0) return true
  if (activeShards.length > 0 || completedShards.length > 0) return true
  const message = String(latestLog?.message || latestLog?.logMessage || latestLog?.content || '').trim()
  return !!message
}

function buildWatchdogSignature(task, latestLog, activeShards, completedShards) {
  return JSON.stringify({
    taskId: task?.taskId || '',
    status: deriveTaskRuntimeStatus(task),
    crawled: crawledCount(task),
    total: targetCount(task),
    latestLogTime: latestLog?.createTime || '',
    latestLogMessage: latestLog?.message || '',
    active: activeShards.map((item) => [item.shardId, item.page, item.status, item.workerId, item.startTime, item.endTime]),
    completed: completedShards.map((item) => [item.shardId, item.page, item.status, item.newCount, item.updatedCount, item.duplicateCount, item.endTime])
  })
}

function resetWatchdogState(taskId = '', overrides = {}) {
  watchdogState.value = {
    taskId,
    createdAt: taskId ? Date.now() : 0,
    lastSignalAt: 0,
    lastSignalSourceAt: 0,
    lastSignature: '',
    armed: false,
    restartCount: 0,
    lastRestartAt: 0,
    restarting: false,
    message: '',
    ...overrides
  }
}

function scheduleTaskPanelRefresh(taskId, options = {}) {
  const id = taskId || activeTaskId.value || tasks.value[0]?.taskId || ''
  if (!id) return

  window.setTimeout(() => {
    void loadTrackedTask(id, true, { withShardSync: false })
    if (options.includeLogs !== false) {
      void loadLogs(id, true, { withShardSync: false })
    }
    void syncSchedulerShardFallback(true)
  }, 0)
}

function scheduleDashboardRefresh(options = {}) {
  window.setTimeout(() => {
    void loadDashboard({
      silent: options.silent ?? true,
      backgroundDetails: true,
      focusTaskId: options.focusTaskId || activeTaskId.value || ''
    })
  }, 0)
}

async function fetchWithTimeout(url, timeoutMs) {
  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null
  const timer = controller && timeoutMs
    ? window.setTimeout(() => controller.abort(), timeoutMs)
    : null
  try {
    return await fetch(url, {
      signal: controller?.signal
    })
  } finally {
    if (timer) {
      window.clearTimeout(timer)
    }
  }
}

function canStartTask(task) {
  const status = deriveTaskRuntimeStatus(task)
  return status !== 1 && status !== 2
}

function canPauseTask(task) {
  return deriveTaskRuntimeStatus(task) === 1
}

function canFinishTask(task) {
  const status = deriveTaskRuntimeStatus(task)
  return status !== 2 && status !== 3
}

function startActionLabel(task) {
  return Number(task?.status) === 3 ? '闂傚倷鐒﹁ぐ鍐矓妞嬪海鐜? : '闂備礁鎲￠崙褰掑垂閻楀牊鍙?
}

function optimisticStatusPatch(status, task = {}) {
  const now = new Date().toISOString()
  if (status === 1) {
    return { status, startTime: task.startTime || now, endTime: null, updateTime: now }
  }
  if (status === 0) {
    return { status, updateTime: now }
  }
  if (status === 3) {
    return { status, endTime: now, updateTime: now }
  }
  return { status, updateTime: now }
}

function patchTaskState(taskId, patch) {
  tasks.value = tasks.value.map((item) => (item.taskId === taskId ? { ...item, ...patch } : item))
  if (trackedTask.value?.taskId === taskId) {
    trackedTask.value = { ...trackedTask.value, ...patch }
  }
  if (detailTask.value?.taskId === taskId) {
    detailTask.value = { ...detailTask.value, ...patch }
  }
}

function pinTaskToTop(task) {
  if (!task?.taskId) return
  const next = [task, ...tasks.value.filter((item) => item.taskId !== task.taskId)]
  tasks.value = next
  totalTasks.value = Math.max(totalTasks.value || 0, next.length)
}

async function loadDashboard(options = {}) {
  if (!authStore.token) return

  loading.value = true
  if (!options.silent) {
    error.value = ''
  }

  try {
    const [taskState, liveState, qualityState] = await Promise.allSettled([
      fetchCrawlTasks(authStore.token, {
        channel: filters.value.channel,
        status: filters.value.status,
        page: taskPage.value,
        pageSize: taskPageSize.value,
        timeoutMs: DASHBOARD_REQUEST_TIMEOUT_MS
      }),
      fetchCrawlLiveOverview(authStore.token, {
        timeoutMs: DASHBOARD_REQUEST_TIMEOUT_MS
      }),
      fetchCrawlQuality(authStore.token, {
        timeoutMs: DASHBOARD_REQUEST_TIMEOUT_MS
      })
    ])

    if (taskState.status === 'fulfilled') {
      tasks.value = taskState.value.data || []
      totalTasks.value = taskState.value.total || 0
    }
    if (liveState.status === 'fulfilled') {
      liveOverview.value = liveState.value || {}
    }
    if (qualityState.status === 'fulfilled') {
      quality.value = qualityState.value || {}
    }

    const failures = [taskState, liveState, qualityState].filter((item) => item.status === 'rejected')
    if (failures.length === 3) {
      throw failures[0].reason
    }
    if (failures.length > 0 && !options.silent) {
      const labels = [
        taskState.status === 'rejected' ? '濠电偛顕慨楣冾敋瑜庨幈銊╂偄閸忓皷鎸€闂佺粯鏌ㄩ崲鏌ユ倶? : '',
        liveState.status === 'rejected' ? '闂佽楠稿﹢閬嶅磻閻愬樊娓婚柛灞剧⊕娴溿倖绻涢幋鐏活亪顢? : '',
        qualityState.status === 'rejected' ? '闂備浇妗ㄩ懗鑸垫櫠濡も偓閻ｅ灚鎷呴悜妯侯伕闂侀潧艌閺呮稑鈻? : ''
      ].filter(Boolean)
      setFeedback('error', `${labels.join('闂?)} 闂備礁鎲＄敮锟犲绩闁秴钃熷┑鐘插閹儵鏌涘☉鍗炴灍妞は佸洦鐓ユ繛鎴烆焽閻掓悂鏌曢崱妤婃█婵☆偄鍟存慨鈧柣妯诲絻濞堛儲绻涢敐鍛缂佽瀚板鎶藉焵椤掑嫭鐓曟繛鍡樏悘锝夋煛娴ｉ潧鈧妲愰幒妤€绠婚悗鐢告櫜閸戠禇)
    }

    if (!tasks.value.length && taskPage.value > 1) {
      taskPage.value = Math.max(1, taskPage.value - 1)
      await loadDashboard(options)
      return
    }

    const focusTaskId = options.focusTaskId || activeTaskId.value || tasks.value[0]?.taskId || ''

    if (!options.backgroundDetails) {
      if (focusTaskId) {
        await loadTrackedTask(focusTaskId, true)
        if (trackedTask.value?.taskId === focusTaskId) {
          pinTaskToTop(trackedTask.value)
        }
        await loadLogs(focusTaskId, true)
      }
      syncRealtimeWatchdog()
      await syncSchedulerShardFallback(true)
    } else {
      if (focusTaskId) {
        activeTaskId.value = focusTaskId
      }
      syncRealtimeWatchdog()
      scheduleTaskPanelRefresh(focusTaskId)
    }
  } catch (e) {
    setFeedback('error', normalizeError(e))
  } finally {
    loading.value = false
  }
}

function stopPolling() {
  if (pollTimer.value) {
    window.clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

function startClock() {
  if (clockTimer.value) return
  clockTimer.value = window.setInterval(() => {
    clockNow.value = Date.now()
    syncRealtimeWatchdog()
    void syncSchedulerShardFallback()
  }, 1000)
}

function stopClock() {
  if (clockTimer.value) {
    window.clearInterval(clockTimer.value)
    clockTimer.value = null
  }
}

function startPolling() {
  stopPolling()
  const interval = Date.now() < fastPollingUntil.value ? 2500 : 6000
  pollTimer.value = window.setInterval(async () => {
    if (!loading.value && !submitting.value && !detailLoading.value) {
      await loadDashboard({ silent: true, backgroundDetails: true })
    }
    if (Date.now() >= fastPollingUntil.value && interval !== 4000) {
      startPolling()
    }
  }, interval)
}

function boostPolling(durationMs = 45000) {
  fastPollingUntil.value = Date.now() + durationMs
  startPolling()
}

function applyFilters() {
  taskPage.value = 1
  void loadDashboard()
}

function goToPage(n) {
  const next = Math.max(1, Math.min(taskTotalPages.value, n))
  if (next === taskPage.value) return
  taskPage.value = next
  void loadDashboard()
}

async function loadLogs(taskId, silent = false, options = {}) {
  if (!taskId || !authStore.token) return

  activeTaskId.value = taskId
  logsLoading.value = true
  try {
    const result = await fetchCrawlTaskLogs(authStore.token, taskId, {
      page: 1,
      pageSize: 20
    })
    logs.value = result.data || []
    syncRealtimeWatchdog()
    if (options.withShardSync !== false) {
      await syncSchedulerShardFallback(true)
    }
  } catch (e) {
    if (!silent) {
      setFeedback('error', normalizeError(e))
    }
  } finally {
    logsLoading.value = false
  }
}

async function loadTrackedTask(taskId, silent = false, options = {}) {
  if (!taskId || !authStore.token) return

  try {
    trackedTask.value = await fetchCrawlTask(authStore.token, taskId)
    if (trackedTask.value?.taskId === taskId) {
      pinTaskToTop(trackedTask.value)
    }
    syncRealtimeWatchdog()
    if (options.withShardSync !== false) {
      await syncSchedulerShardFallback(true)
    }
  } catch (e) {
    if (!silent) {
      setFeedback('error', normalizeError(e))
    }
  }
}

async function handleCreateTask() {
  if (!authStore.token || submitting.value) return

  submitting.value = true
  error.value = ''
  const formSnapshot = {
    taskName: taskForm.value.taskName,
    channel: SOURCE_CHANNEL,
    keywords: taskForm.value.keywords,
    city: taskForm.value.city,
    targetCount: Number(taskForm.value.targetCount) || 20,
    priority: Number(taskForm.value.priority) || 5
  }
  try {
    const result = await createCrawlTask(authStore.token, {
      taskName: formSnapshot.taskName,
      channel: formSnapshot.channel,
      keywords: formSnapshot.keywords,
      city: formSnapshot.city,
      targetCount: formSnapshot.targetCount,
      priority: formSnapshot.priority
    })

    const createdTaskId = result?.taskId || ''
    taskForm.value = defaultTaskForm()
    createFormOpen.value = false
    taskPage.value = 1
    if (createdTaskId) {
      const nowIso = new Date().toISOString()
      activeTaskId.value = createdTaskId
      trackedTask.value = {
        taskId: createdTaskId,
        taskName: formSnapshot.taskName,
        channel: formSnapshot.channel,
        keywords: formSnapshot.keywords,
        city: formSnapshot.city,
        targetCount: formSnapshot.targetCount,
        priority: formSnapshot.priority,
        status: 0,
        crawledCount: 0,
        finishedCount: 0,
        duplicateCount: 0,
        newCount: 0,
        updatedCount: 0,
        createTime: nowIso,
        updateTime: nowIso,
        startTime: ''
      }
      pinTaskToTop(trackedTask.value)
      resetWatchdogState(createdTaskId, {
        message: '\u4efb\u52a1\u5df2\u521b\u5efa\uff0c\u7b49\u5f85\u70b9\u51fb\u8fd0\u884c\u3002\u8fd0\u884c\u540e\u9875\u9762\u4f1a\u7ee7\u7eed\u5c55\u793a\u9274\u6743\u3001\u5206\u53d1\u548c\u91c7\u96c6\u8fdb\u5ea6\u3002'
      })
      scheduleTaskPanelRefresh(createdTaskId)
    }
    setFeedback('success', createdTaskId ? ('\u4efb\u52a1\u5df2\u521b\u5efa\uff1a' + createdTaskId + '\uff0c\u8bf7\u70b9\u51fb\u8fd0\u884c\u5f00\u59cb\u91c7\u96c6') : '\u4efb\u52a1\u5df2\u521b\u5efa')
    boostPolling()
    scheduleDashboardRefresh({ focusTaskId: createdTaskId })
  } catch (e) {
    setFeedback('error', normalizeError(e))
  } finally {
    submitting.value = false
  }
}

async function handleTaskStatus(task, status) {
  if (!authStore.token) return

  const taskId = task.taskId
  const previousTask = tasks.value.find((item) => item.taskId === taskId)
  const previousDetailTask = detailTask.value?.taskId === taskId ? detailTask.value : null
  statusUpdating.value = `${task.taskId}:${status}`
  error.value = ''
  patchTaskState(taskId, optimisticStatusPatch(status, task))
  if (status === 1) {
    resetWatchdogState(taskId, {
      restartCount: watchdogState.value.taskId === taskId ? watchdogState.value.restartCount : 0,
      message: '濠电偛顕慨楣冾敋瑜庨幈銊╂偄婵傚鏅犻梺鍦帛鐢帞寰婂ú顏呪拺妞ゆ劧绱曢ˇ锕傛煕閳轰胶鐏遍柟濂夊亰瀹曟﹢濡搁妷顔兼暯濠电偞鍨堕幖鈺呭储閼测晝绱﹀Δ锝呭暞閺咁剟鎮橀悙浣冩闁告柡鍋撻梻渚€娼荤拹鐔煎礉鎼淬劍鍋ら柟瀛樼箥閸ゆ鏌涘☉鍗炴灍闁绘挴鍋撻梻浣圭湽閸斿瞼鈧凹鍨抽幑銏犖熺紒妯哄妳闂佹寧娲嶉崑鎾寸箾閸欏澧悗闈涖偢閹晠顢欓悷棰佸?
    })
  } else if (status === 3 && watchdogState.value.taskId === taskId) {
    watchdogState.value = {
      ...watchdogState.value,
      armed: false,
      restarting: false,
      message: '濠电偛顕慨楣冾敋瑜庨幈銊╂偄婵傚鏅犻梺鍛婄懃椤︻垶路娓氣偓閺屾盯寮拠鎻掝瀷缂備緡鍠撻崝鎴濐嚕閸洘鍋嗛柛灞剧矌椤︻噣姊洪悡搴疇濞存粍绮嶉幈銊╁煛閸涱喚鍘掗悗骞垮劚閹冲繘宕曞▎鎾村€甸柣鐔稿婢ф稑鈹戦鍝勭仼妞ゆ柨绻橀獮鎾诲箳瀹ュ洣绗夋繝娈垮枟缁诲秴顭囬崸妤€鐒?
    }
  }
  try {
    await updateCrawlTaskStatus(authStore.token, taskId, { status }, { timeoutMs: TASK_ACTION_TIMEOUT_MS })
    setFeedback('success', `${task.taskName || taskId} 闂備胶绮…鍫ュ春閺嶎厼鐒垫い鎴ｆ硶椤︼箓鏌涢幋顖滅瘈鐎殿喖顕埀顒佺⊕钃遍柣鎾亾`)
    boostPolling()
    scheduleDashboardRefresh({ focusTaskId: taskId })
    scheduleTaskPanelRefresh(taskId)
  } catch (e) {
    if (previousTask) {
      patchTaskState(taskId, previousTask)
    }
    if (previousDetailTask) {
      detailTask.value = previousDetailTask
    }
    if (status === 1 || status === 3) {
      resetWatchdogState(watchdogState.value.taskId === taskId ? taskId : '')
    }
    setFeedback('error', normalizeError(e))
  } finally {
    statusUpdating.value = ''
  }
}

async function restartTaskByWatchdog(task) {
  const taskId = task?.taskId
  if (!taskId || !authStore.token || watchdogState.value.restarting) return

  watchdogState.value = {
    ...watchdogState.value,
    taskId,
    createdAt: watchdogState.value.taskId === taskId ? watchdogState.value.createdAt || Date.now() : Date.now(),
    armed: true,
    restarting: true,
    restartCount: watchdogState.value.restartCount + 1,
    lastRestartAt: Date.now(),
    message: '闁荤喐绮庢晶妤呭箰閸涘﹥娅犻柣妯虹－椤╃兘鎮归崶銊ョ祷妞ゎ偁鍊濋弻娑㈡晜閸濆嫬顬嬪┑鐐村絻閸熸挳鐛幒鏇ㄦЬ闂佺粯绋忛崕闈涚暦閿濆鏅查柛娑卞幗閺嗐儱鈹戦悙鎻掝槵闁告挻绻傞敃銏＄瑹閳ь剙顕ｉ鍕鐎光偓閳ь剛绮绘繝姘閺夊牊宕橀铏圭磼鏉堛劎绠橀柛鏍ㄧ墵閹筹繝濡堕崶褏鍘锋繝娈垮枟缁绘劗寰婇挊澶涜€挎い蹇撶墛閸ゅ﹥銇勮箛鎾愁仼鐞氱喓绱撻崒娆戝妽闁规瓕顕ч…鍥敃閿旀儳绁﹂梺鍓插亝濞叉牕鈻嶉姀銈嗙厱婵鍘ч悘娑㈡煃?
  }
  statusUpdating.value = `${taskId}:watchdog`

  try {
    await updateCrawlTaskStatus(authStore.token, taskId, { status: 0 }, { timeoutMs: TASK_ACTION_TIMEOUT_MS })
    await new Promise((resolve) => window.setTimeout(resolve, 600))
    await updateCrawlTaskStatus(authStore.token, taskId, { status: 1 }, { timeoutMs: TASK_ACTION_TIMEOUT_MS })
    setFeedback('success', `${task.taskName || taskId} 闂傚倸鍊甸崑鎾绘煙缁嬪灝顒㈡い蟻鍥ㄢ拻闁稿本姘ㄩ幗鐘充繆椤愮姴鈧牕顭囩拠娴嬫婵☆垯璀﹂崬娲⒑閹稿海鈽夐柤娲诲灦瀹曟瑩鏁撻悩鍐叉疁濡炪倕绻愮€氼剝顤勭紓鍌氬€烽悞锕傚箰鐠囧樊鐒芥い鎰跺瀹撲線鏌涢锝嗙婵炲懌鍨介弻娑橆潩鏉堫煈妫?
    watchdogState.value = {
      ...watchdogState.value,
      createdAt: Date.now(),
      armed: false,
      restarting: false,
      lastSignalAt: 0,
      lastSignalSourceAt: 0,
      lastSignature: '',
      message: `闂備礁鎼悧鍐磻閹捐绾ч柍鍝勫€搁悘锝囩磼閺冣偓濞兼瑩鍩㈡惔銊︻€愰梺鎼炲€栫划鎾崇暦濠靛惟闁宠桨鐒﹂鐔兼⒑閸涘﹤鍤柛銊ュ船椤啴宕掗悙绮规嫽闁哄鐗嗘晶鐣岀玻?{formatTime(new Date().toISOString())}`
    }
    boostPolling(60000)
    scheduleDashboardRefresh({ focusTaskId: taskId })
    scheduleTaskPanelRefresh(taskId)
  } catch (e) {
    watchdogState.value = {
      ...watchdogState.value,
      restarting: false,
      message: `闂備胶鍘ч〃搴㈢濠婂嫭鍙忛柍鍝勬噺閻撳倻鈧箍鍎遍幊蹇涘磿濞嗗浚鐔嗛柟顖涘缁ㄥ潡鎮峰▎娆戠暤闁?{normalizeError(e)}`
    }
    setFeedback('error', normalizeError(e))
  } finally {
    statusUpdating.value = ''
  }
}

function syncRealtimeWatchdog() {
  const task = realtimeTask.value
  const runtimeStatus = deriveTaskRuntimeStatus(task)
  if (!task?.taskId) {
    resetWatchdogState()
    return
  }

  if (watchdogState.value.taskId !== task.taskId) {
    resetWatchdogState(task.taskId)
  }

  if (runtimeStatus !== 0 && runtimeStatus !== 1) {
    watchdogState.value = {
      ...watchdogState.value,
      armed: false,
      restarting: false
    }
    return
  }

  const latestLog = realtimeLatestLog.value
  const activeShards = realtimeActiveShards.value
  const completedShards = realtimeCompletedShards.value
  const latestSignal = latestSignalTimestamp(task, latestLog, activeShards, completedShards)
  const hasProgressSignal = hasRealtimeProgressSignal(task, latestLog, activeShards, completedShards)
  const signature = buildWatchdogSignature(task, latestLog, activeShards, completedShards)
  const signatureChanged = signature !== watchdogState.value.lastSignature
  const sourceAdvanced = latestSignal > watchdogState.value.lastSignalSourceAt

  if (hasProgressSignal && (signatureChanged || sourceAdvanced || !watchdogState.value.lastSignalAt || !watchdogState.value.armed)) {
    watchdogState.value = {
      ...watchdogState.value,
      armed: true,
      lastSignalAt: Date.now(),
      lastSignalSourceAt: Math.max(latestSignal, watchdogState.value.lastSignalSourceAt),
      lastSignature: signature,
      restarting: false,
      message: watchdogState.value.restartCount > 0
        ? `闁诲骸婀遍…鍫濐嚕閼哥數顩锋い鏃囨缁剁偟鈧箍鍎遍悧蹇曠不婵犳艾绠归弶鍫熷礃椤撹櫣绱掓潏銊х疄鐎殿喚鏁婚崺鈧い鎺嶇劍娴溿倝鏌熸潏鍓у埌婵炲牅鍗抽弻娑㈠棘鐠囨彃顫囬梺闈╃稻閹倸鐣?${watchdogState.value.restartCount} 婵犵數鍋涢弸鎾箥閸愯弓澹曟繛?
        : ''
    }
    return
  }

  if (!watchdogState.value.armed) {
    const elapsed = Date.now() - (watchdogState.value.createdAt || Date.now())
    watchdogState.value = {
      ...watchdogState.value,
      message: elapsed < WATCHDOG_INITIAL_GRACE_MS
        ? '濠电偛顕慨楣冾敋瑜庨幈銊╂偄婵傚妗ㄩ梺鎸庣箓閹冲繘鐓鍌滅＜婵炴垶锕╁Σ鍝ョ磼閸撲礁鏋涢柡灞芥捣閳ь剚绋掕摫缂傚秮鍋撻梻渚€娼уΛ鏃傜矆娓氣偓瀹曡鎯旈妸銉х厬闂佺懓顕崑娑㈡倶濡や胶绠鹃柡澶嬪灩缁犵儤銇勯銏⑿х€规洘濞婃俊鎼佸煛婵犲啰顩ㄩ梻浣告惈椤ワ繝濡堕崶鈺婂晪闂佺澹堥幓顏嗙不閹存緷娲冀閵娿儺鍤ら梺缁樼懃閹虫劗绮堟径鎰€甸梺顐ｇ〒閻瑦淇婇悙顒併仢鐎殿喚鏁婚幃銏犵暋閻楀牊娈藉┑鐐村灦閹稿摜绮斿畷鍥潟濞寸厧鐡ㄩ崵濠冦亜韫囨挸顏╃悮鐔兼⒒娴ｇ懓绲荤紒澶嬫尦楠炲棙鎯旈妸銉?
        : '濠电偛顕慨楣冾敋瑜庨幈銊╂偄閻撳孩宓嶉梺闈浥堥弲鈺伱归弴鐔虹闁割偁鍎插☉褎銇勯弮鈧Λ鍐潖娴犲绠涙い鎾跺枎閻掓悂姊洪崨濠傚闁瑰啿绉堕崚鎺戔槈閵忕姴鐝樺銈呯箰鐎氼厾绮堥埀顒佺箾绾惧浜瑰┑顔煎⒔閹广垹螣缂佹ê鍔呴梺鎸庢閸嬫劗绮堟径宀€纾兼い鏍ㄧ箓閸氬湱绱掗鑺ャ仢鐎规洘鐟╁畷鍗炍旀繝鍐冿綁姊洪悡搴疇濞存粍绮嶉幈銊╁煛閸涱喚鍘掗悗骞垮劚閹冲繘宕曞▎鎾寸厪?
    }
    if (elapsed < WATCHDOG_INITIAL_GRACE_MS) {
      return
    }
  }

  if (watchdogState.value.restarting) return
  if (Date.now() - watchdogState.value.lastRestartAt < WATCHDOG_RESTART_COOLDOWN_MS) return

  const lastActivityAt = watchdogState.value.armed
    ? (watchdogState.value.lastSignalAt || watchdogState.value.createdAt)
    : watchdogState.value.createdAt
  const timeoutMs = watchdogState.value.armed ? WATCHDOG_TIMEOUT_MS : WATCHDOG_INITIAL_GRACE_MS

  if (Date.now() - lastActivityAt >= timeoutMs) {
    void restartTaskByWatchdog(task)
  }
}

async function openTaskDetail(task) {
  const id = task?.taskId || task?.id
  if (!id || !authStore.token) return

  detailTaskId.value = String(id)
  detailLoading.value = true
  detailTask.value = null
  try {
    detailTask.value = await fetchCrawlTask(authStore.token, id)
    trackedTask.value = detailTask.value
    pinTaskToTop(detailTask.value)
    await loadLogs(id, true)
  } catch (e) {
    setFeedback('error', normalizeError(e))
  } finally {
    detailLoading.value = false
  }
}

function closeTaskDetail() {
  detailTaskId.value = ''
  detailTask.value = null
}

onMounted(() => {
  void loadDashboard({ backgroundDetails: true })
  startClock()
  startPolling()
})

onUnmounted(() => {
  stopClock()
  stopPolling()
})
</script>

<template>
  <div class="collector-page page-animate">
    <section class="collector-hero">
      <div>
        <h1 class="collector-title">Distributed Crawl Console</h1>
        <p class="collector-subtitle">Create tasks, start or stop collection, inspect live shard progress, and auto-restart stalled jobs after the configured grace window.</p>
      </div>
      <div class="collector-hero-actions">
        <GlowButton variant="ghost" @click="loadDashboard">
          <RefreshCw :size="14" /> Refresh
        </GlowButton>
        <GlowButton variant="ghost" @click="router.push('/reports')">
          <FileText :size="14" /> Reports
        </GlowButton>
        <GlowButton variant="ghost" @click="router.push('/openapi')">
          <Info :size="14" /> OpenAPI
        </GlowButton>
        <GlowButton variant="primary" @click="createFormOpen = !createFormOpen">
          <Plus :size="14" /> {{ createFormOpen ? 'Hide form' : 'Create task' }}
        </GlowButton>
      </div>
    </section>

    <div v-if="error" class="error-banner">{{ error }}</div>
    <div v-else-if="successMsg" class="success-banner">{{ successMsg }}</div>

    <article v-if="createFormOpen" class="collector-panel create-panel">
      <header class="collector-panel-head">
        <div class="collector-panel-copy">
          <h2 class="collector-panel-title"><Plus :size="15" /> New task</h2>
          <p class="collector-panel-sub">Task creation only saves the job. Collection starts after clicking the run action.</p>
        </div>
      </header>
      <div class="collector-panel-body">
        <div class="task-form">
          <div class="form-row">
            <label class="field field-grow">
              <span class="field-label">Task name</span>
              <input v-model.trim="taskForm.taskName" class="collector-input" placeholder="Example: Chengdu Java crawl" />
            </label>
            <label class="field">
              <span class="field-label">Keyword</span>
              <input v-model.trim="taskForm.keywords" class="collector-input" placeholder="Example: Java" />
            </label>
          </div>
          <div class="form-row">
            <label class="field">
              <span class="field-label">City</span>
              <select v-model="taskForm.city" class="collector-input">
                <option v-for="city in CITY_OPTIONS" :key="city.code" :value="city.code">{{ city.label }}</option>
              </select>
            </label>
            <label class="field">
              <span class="field-label">Target rows</span>
              <select v-model="taskForm.targetCount" class="collector-input">
                <option v-for="count in TARGET_COUNT_OPTIONS" :key="count" :value="count">{{ count }}</option>
              </select>
            </label>
            <label class="field field-priority">
              <span class="field-label">Priority</span>
              <input v-model.number="taskForm.priority" type="number" min="1" max="9" class="collector-input slim" />
            </label>
            <GlowButton variant="primary" :loading="submitting" @click="handleCreateTask" class="form-submit">
              <Plus :size="15" /> Create
            </GlowButton>
          </div>
        </div>
      </div>
    </article>

    <section class="metrics-grid">
      <article v-for="item in qualityCards" :key="item.label" class="collector-metric-card">
        <div class="collector-metric-head">
          <span class="collector-metric-label">{{ item.label }}</span>
          <span class="collector-metric-dot" aria-hidden="true"></span>
        </div>
        <div class="collector-metric-value">{{ item.value }}</div>
        <div class="collector-metric-note">{{ item.note }}</div>
      </article>
    </section>

    <article class="collector-panel live-panel">
      <header class="collector-panel-head">
        <div class="collector-panel-copy">
          <h2 class="collector-panel-title"><Activity :size="15" /> Live status</h2>
          <p class="collector-panel-sub">The panel updates task state, shard assignment, logs, and watchdog actions in near real time.</p>
        </div>
        <span class="collector-panel-badge">{{ realtimeStageLabel }}</span>
      </header>
      <div class="collector-panel-body">
        <div v-if="realtimeTask" class="live-overview">
          <div class="live-main">
            <div>
              <div class="live-title-row">
                <strong class="live-title">{{ realtimeTask.taskName || realtimeTask.taskId }}</strong>
                <span class="pill" :class="`pill-${getStatusMeta(deriveTaskRuntimeStatus(realtimeTask)).tone}`">
                  <component :is="getStatusMeta(deriveTaskRuntimeStatus(realtimeTask)).icon" :size="12" />
                  {{ getStatusMeta(deriveTaskRuntimeStatus(realtimeTask)).label }}
                </span>
              </div>
              <div class="live-subtitle">Task ID: {{ realtimeTask.taskId || '--' }}</div>
              <p class="live-detail">{{ realtimeStageDetail }}</p>
            </div>
            <div class="live-tags">
              <span class="meta-chip">City: {{ formatCityList(realtimeTask.city, 'All') }}</span>
              <span class="meta-chip">Keyword: {{ formatList(realtimeTask.keywords, 'None') }}</span>
              <span class="meta-chip">Channel: {{ formatChannel(realtimeTask.channel) }}</span>
            </div>
          </div>

          <div class="live-progress-card">
            <div class="task-progress">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: `${progressPercent(realtimeTask)}%` }" />
              </div>
              <span class="progress-count">{{ progressText(realtimeTask) }}</span>
            </div>
            <div class="live-stats">
              <div class="live-stat"><span>Total</span><strong>{{ realtimeShardStats.total }}</strong></div>
              <div class="live-stat"><span>Queued</span><strong>{{ realtimeShardStats.pending }}</strong></div>
              <div class="live-stat"><span>Running</span><strong>{{ realtimeShardStats.running }}</strong></div>
              <div class="live-stat"><span>Done</span><strong>{{ realtimeShardStats.completed }}</strong></div>
              <div class="live-stat"><span>Failed</span><strong>{{ realtimeShardStats.failed }}</strong></div>
            </div>
          </div>

          <div v-if="realtimeWatchdogVisible" class="watchdog-card" :class="{ 'is-restarting': watchdogState.restarting }">
            <div class="watchdog-head">
              <strong>Watchdog</strong>
              <span class="watchdog-badge">{{ watchdogState.restarting ? 'Restarting' : `T-${realtimeWatchdogCountdown ?? '--'}s` }}</span>
            </div>
            <div class="watchdog-body">{{ realtimeWatchdogText }}</div>
          </div>

          <div class="live-data-grid">
            <article v-for="card in realtimeDataCards" :key="card.label" class="live-data-card">
              <span class="live-data-label">{{ card.label }}</span>
              <strong class="live-data-value">{{ card.value }}</strong>
              <span class="live-data-note">{{ card.note }}</span>
            </article>
          </div>

          <div class="live-shard-section">
            <div class="live-shard-card">
              <div class="live-shard-head">
                <strong>Active shards</strong>
                <span>{{ realtimeActiveShards.length }}</span>
              </div>
              <div v-if="realtimeActiveShards.length" class="shard-list">
                <article v-for="item in realtimeActiveShards" :key="item.shardId || `${item.page}-${item.keyword}-${item.city}`" class="shard-item shard-item-active">
                  <div class="shard-title-row">
                    <strong>{{ formatShardSummary(item) }}</strong>
                    <span class="shard-status">Node: {{ item.workerId || 'pending' }}</span>
                  </div>
                  <div class="shard-meta-row">
                    <span>{{ formatShardResult(item) }}</span>
                    <span>{{ formatTime(item.startTime || realtimeTask.startTime) }}</span>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">No active shards</div>
            </div>

            <div class="live-shard-card">
              <div class="live-shard-head">
                <strong>Completed shards</strong>
                <span>{{ realtimeCompletedShards.length }}</span>
              </div>
              <div v-if="realtimeCompletedShards.length" class="shard-list">
                <article v-for="item in realtimeCompletedShards" :key="item.shardId || `${item.page}-${item.keyword}-${item.city}`" class="shard-item">
                  <div class="shard-title-row">
                    <strong>{{ formatShardSummary(item) }}</strong>
                    <span class="shard-status">Node: {{ item.workerId || 'reported' }}</span>
                  </div>
                  <div class="shard-meta-row">
                    <span>{{ formatShardResult(item) }}</span>
                    <span>{{ formatTime(item.endTime || item.startTime) }}</span>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">No completed shards</div>
            </div>
          </div>

          <div class="live-log-card">
            <div class="live-log-head">
              <strong>Latest log</strong>
              <span>{{ realtimeLatestLog ? formatTime(realtimeLatestLog.createTime) : 'none' }}</span>
            </div>
            <div class="live-log-body">{{ realtimeLatestLog?.message || realtimeLatestLog?.logMessage || 'No logs yet' }}</div>
          </div>
        </div>
        <div v-else class="empty-block">No task is available for live preview.</div>
      </div>
    </article>

    <div class="collector-bottom">
      <article class="collector-panel">
        <header class="collector-panel-head">
          <div class="collector-panel-copy">
            <h2 class="collector-panel-title"><TerminalSquare :size="15" /> Tasks</h2>
            <p class="collector-panel-sub">Select a task to inspect logs. Run, pause, and finish actions apply immediately.</p>
          </div>
          <div class="collector-panel-tools">
            <select v-model="filters.status" class="collector-input slim" @change="applyFilters">
              <option value="">All status</option>
              <option value="0">Queued</option>
              <option value="1">Running</option>
              <option value="2">Done</option>
              <option value="3">Stopped</option>
            </select>
          </div>
        </header>
        <div class="collector-panel-body">
          <div v-if="showInitialTaskLoading" class="empty-block">Loading tasks...</div>
          <div v-else-if="tasks.length === 0" class="empty-block">No tasks found.</div>
          <div v-else class="task-list">
            <article
              v-for="task in tasks"
              :key="task.taskId"
              class="task-row"
              :class="{ active: activeTaskId === task.taskId }"
              tabindex="0"
              @click="loadLogs(task.taskId)"
              @keydown.enter.prevent="loadLogs(task.taskId)"
              @keydown.space.prevent="loadLogs(task.taskId)"
            >
              <div class="task-head">
                <div class="task-main">
                  <h3 class="task-title">{{ task.taskName }}</h3>
                  <p class="task-subtitle">{{ formatChannel(task.channel) }} · {{ formatCityList(task.city, 'All') }} · {{ formatList(task.keywords, 'No keyword') }}</p>
                </div>
                <span class="pill" :class="`pill-${getStatusMeta(deriveTaskRuntimeStatus(task)).tone}`">
                  <component :is="getStatusMeta(deriveTaskRuntimeStatus(task)).icon" :size="12" />
                  {{ getStatusMeta(deriveTaskRuntimeStatus(task)).label }}
                </span>
              </div>
              <div class="task-progress">
                <div class="progress-track">
                  <div class="progress-fill" :style="{ width: `${progressPercent(task)}%` }" />
                </div>
                <span class="progress-count">{{ progressText(task) }}</span>
              </div>
              <div class="task-meta">
                <span>Created: {{ formatTime(task.createTime) }}</span>
                <span>Updated: {{ formatTime(task.updateTime) }}</span>
                <span>New: {{ safeNumber(task.newCount) }}</span>
                <span>Updated rows: {{ safeNumber(task.updatedCount) }}</span>
                <span>Deduped: {{ safeNumber(task.duplicateCount) }}</span>
              </div>
              <div class="task-actions">
                <button class="mini-action" :disabled="!canStartTask(task) || statusUpdating === `${task.taskId}:1`" @click.stop="handleTaskStatus(task, 1)">
                  <PlayCircle :size="13" /> {{ startActionLabel(task) }}
                </button>
                <button class="mini-action" :disabled="!canPauseTask(task) || statusUpdating === `${task.taskId}:0`" @click.stop="handleTaskStatus(task, 0)">
                  <PauseCircle :size="13" /> Pause
                </button>
                <button class="mini-action danger" :disabled="!canFinishTask(task) || statusUpdating === `${task.taskId}:3`" @click.stop="handleTaskStatus(task, 3)">
                  <SquareX :size="13" /> Finish
                </button>
                <button class="mini-action" @click.stop="openTaskDetail(task)">
                  <ShieldCheck :size="13" /> Detail
                </button>
              </div>
            </article>
          </div>

          <div class="task-pager">
            <button class="pager-btn" :disabled="taskPage <= 1" @click="goToPage(taskPage - 1)">
              <ChevronLeft :size="14" /> Prev
            </button>
            <span class="pager-info">Page <strong>{{ taskPage }}</strong> / {{ taskTotalPages }} · {{ totalTasks }} items</span>
            <button class="pager-btn" :disabled="taskPage >= taskTotalPages" @click="goToPage(taskPage + 1)">
              Next <ChevronRight :size="14" />
            </button>
          </div>
        </div>
      </article>

      <div class="quality-body">
        <article class="collector-panel">
          <header class="collector-panel-head">
            <div class="collector-panel-copy">
              <h2 class="collector-panel-title"><ShieldCheck :size="15" /> Quality</h2>
              <p class="collector-panel-sub">Snapshot quality indicators for the currently synced crawl data.</p>
            </div>
          </header>
          <div class="collector-panel-body">
            <div class="quality-section">
              <p class="quality-label">Freshness</p>
              <div v-if="freshnessRows.length" class="freshness-list">
                <div v-for="row in freshnessRows" :key="row.label || row.name" class="freshness-row">
                  <span class="freshness-label">{{ row.label || row.name }}</span>
                  <div class="freshness-bar">
                    <div class="freshness-fill" :style="{ width: `${safeNumber(row.percent || row.ratio)}%` }"></div>
                  </div>
                  <span class="freshness-count">{{ row.value ?? row.count ?? '--' }}</span>
                </div>
              </div>
              <div v-else class="empty-inline">No quality summary</div>
            </div>
          </div>
        </article>

        <article class="collector-panel log-body">
          <header class="collector-panel-head">
            <div class="collector-panel-copy">
              <h2 class="collector-panel-title"><Clock3 :size="15" /> Logs</h2>
              <p class="collector-panel-sub">Shows the latest messages for the currently selected task.</p>
            </div>
            <span class="collector-panel-badge">{{ activeTaskId || 'none' }}</span>
          </header>
          <div class="collector-panel-body">
            <div v-if="logsLoading" class="empty-block">Loading logs...</div>
            <div v-else-if="logs.length === 0" class="empty-block">No logs</div>
            <div v-else class="log-stream">
              <article v-for="item in logs" :key="item.logId || `${item.createTime}-${item.message}`" class="log-line">
                <div class="log-meta-line">
                  <span class="log-level" :class="String(item.level || 'info').toLowerCase()">{{ item.level || 'INFO' }}</span>
                  <span class="log-worker">{{ item.workerId || 'system' }}</span>
                  <span class="log-time">{{ formatTime(item.createTime) }}</span>
                </div>
                <div class="log-message">{{ item.message || item.logMessage || '--' }}</div>
              </article>
            </div>
          </div>
        </article>
      </div>
    </div>

    <Transition name="drawer-fade">
      <div v-if="detailTaskId" class="task-detail-mask" @click.self="closeTaskDetail">
        <aside class="task-detail-drawer">
          <header class="task-detail-head">
            <div>
              <h3 class="task-detail-title">Task detail</h3>
              <p class="task-detail-sub">{{ detailTask?.taskId || detailTaskId }}</p>
            </div>
            <button class="icon-close" type="button" @click="closeTaskDetail">
              <CloseIcon :size="16" />
            </button>
          </header>
          <div class="task-detail-body">
            <div v-if="detailLoading" class="empty-block">Loading detail...</div>
            <template v-else-if="detailTask">
              <section class="detail-section">
                <h4 class="detail-section-title">Meta</h4>
                <dl class="detail-grid">
                  <div v-for="row in detailMetaRows" :key="row.label" class="detail-row">
                    <dt>{{ row.label }}</dt>
                    <dd>{{ row.value }}</dd>
                  </div>
                </dl>
              </section>
              <section class="detail-section">
                <h4 class="detail-section-title">Recent logs</h4>
                <pre class="detail-pre">{{ logs.map(item => `${formatTime(item.createTime)} [${item.level || 'INFO'}] ${item.message || item.logMessage || ''}`).join('\n') || 'No logs' }}</pre>
              </section>
            </template>
            <div v-else class="empty-block">No detail data</div>
          </div>
        </aside>
      </div>
    </Transition>
  </div>
</template>
            <div v-else class="empty-block">閺嗗倹妫ょ拠锔藉剰</div>
          </div>
        </aside>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.collector-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.collector-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.collector-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.2vw, 30px);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--c-text-primary);
}

.collector-subtitle {
  margin: 4px 0 0;
  color: var(--c-text-muted);
  font-size: 13px;
}

.collector-hero-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.error-banner,
.success-banner {
  padding: 12px 16px;
  border-radius: 12px;
  font-family: var(--font-sans);
  font-size: 13px;
}

.error-banner {
  border: 1px solid rgba(178, 59, 46, 0.22);
  background: rgba(254, 242, 240, 0.92);
  color: #b23b2e;
}

.success-banner {
  border: 1px solid rgba(30, 138, 91, 0.22);
  background: rgba(236, 253, 245, 0.92);
  color: #1e8a5b;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.collector-metric-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 148px;
  padding: 18px 20px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-quiet);
}

.collector-metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.collector-metric-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.collector-metric-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--c-accent-primary);
  opacity: 0.75;
}

.collector-metric-value {
  margin-top: auto;
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.2vw, 28px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.1;
  color: var(--c-text-primary);
  font-variant-numeric: tabular-nums;
}

.collector-metric-note {
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-secondary);
}

.collector-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: var(--shadow-card-quiet);
  overflow: hidden;
}

.collector-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--c-border-glass);
}

.collector-panel-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.collector-panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--c-text-primary);
}

.collector-panel-title :deep(svg) {
  color: var(--c-accent-primary);
  flex: none;
}

.collector-panel-sub {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-muted);
}

.collector-panel-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
  white-space: nowrap;
}

.collector-panel-body {
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.live-overview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.live-main,
.live-progress-card,
.live-log-card,
.watchdog-card,
.live-shard-card,
.live-data-card {
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
  padding: 14px 16px;
}

.live-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.live-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.live-title {
  font-family: var(--font-serif);
  font-size: 18px;
  color: var(--c-text-primary);
}

.live-subtitle {
  margin-top: 4px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-muted);
  word-break: break-all;
}

.live-detail {
  margin: 10px 0 0;
  color: var(--c-text-secondary);
  line-height: 1.6;
  font-size: 13px;
}

.live-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-size: 12px;
}

.live-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.watchdog-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-color: rgba(192, 122, 47, 0.2);
  background: linear-gradient(135deg, rgba(255, 248, 235, 0.92), rgba(255, 255, 255, 0.96));
}

.watchdog-card.is-restarting {
  border-color: rgba(191, 84, 20, 0.28);
  background: linear-gradient(135deg, rgba(255, 239, 232, 0.96), rgba(255, 250, 246, 0.98));
}

.watchdog-head,
.live-shard-head,
.shard-title-row,
.shard-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.watchdog-head strong,
.live-shard-head strong {
  color: var(--c-text-primary);
  font-size: 13px;
}

.watchdog-badge,
.shard-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(191, 84, 20, 0.08);
  color: #b85a23;
  font-size: 12px;
  font-weight: 600;
}

.watchdog-body {
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.live-data-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.live-data-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 110px;
}

.live-data-label,
.live-data-note,
.live-shard-head span,
.empty-inline {
  color: var(--c-text-muted);
  font-size: 12px;
}

.live-data-value {
  font-family: var(--font-serif);
  font-size: 26px;
  line-height: 1.1;
  color: var(--c-text-primary);
}

.live-data-note {
  line-height: 1.5;
}

.live-shard-section {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.live-shard-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.shard-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.shard-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.shard-item-active {
  border-color: rgba(26, 118, 210, 0.18);
  background: linear-gradient(135deg, rgba(241, 247, 255, 0.96), rgba(255, 255, 255, 0.98));
}

.shard-title-row strong,
.shard-meta-row span {
  font-size: 12.5px;
}

.shard-title-row strong {
  color: var(--c-text-primary);
}

.shard-meta-row span {
  color: var(--c-text-secondary);
}

.live-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  border: 1px solid var(--c-border-glass);
}

.live-stat span {
  font-size: 11px;
  color: var(--c-text-muted);
}

.live-stat strong {
  font-size: 18px;
  font-family: var(--font-serif);
  color: var(--c-text-primary);
}

.live-log-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: var(--c-text-secondary);
  font-size: 12px;
}

.live-log-body {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  color: var(--c-text-primary);
  word-break: break-word;
}

.create-panel {
  border-left: 3px solid var(--c-accent-primary);
}

.task-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.field-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.field-help {
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.form-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.form-row .field {
  flex: 1 1 180px;
  min-width: 140px;
}

.form-row .field-grow {
  flex: 2 1 260px;
}

.form-row .field-priority {
  flex: 0 0 140px;
  max-width: 160px;
}

.form-row .form-submit,
.form-row :deep(.glow-button.form-submit) {
  align-self: flex-end;
  flex-shrink: 0;
}

.collector-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.4;
  transition: border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.collector-input::placeholder {
  color: var(--c-text-faint);
}

.collector-input:hover {
  border-color: var(--c-border-glass-hover);
}

.collector-input:focus,
.collector-input:focus-visible {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
  outline: none;
}

.collector-input.slim {
  max-width: 180px;
}

.collector-panel-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.empty-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
  padding: 20px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 13px;
  text-align: center;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-row {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.task-row::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: var(--c-accent-primary);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.task-row:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
}

.task-row.active {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  box-shadow: 0 4px 14px var(--c-accent-primary-glow);
}

.task-row.active::before {
  opacity: 1;
}

.task-row.active .task-title {
  color: var(--c-accent-primary);
}

.task-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.task-main {
  min-width: 0;
  flex: 1;
}

.task-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--c-text-primary);
}

.task-subtitle {
  margin: 3px 0 0;
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-muted);
}

.task-progress {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-track {
  position: relative;
  flex: 1;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--c-accent-primary);
  transition: width var(--duration-normal) var(--ease-out);
}

.progress-count {
  font-family: var(--font-mono);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  color: var(--c-text-secondary);
  flex-shrink: 0;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--c-text-muted);
}

.task-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 2px;
}

.mini-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.mini-action:hover:not(:disabled) {
  border-color: var(--c-accent-primary);
  color: var(--c-accent-primary);
  background: var(--c-accent-primary-glow);
}

.mini-action.danger {
  color: #b23b2e;
}

.mini-action.danger:hover:not(:disabled) {
  border-color: #e8b7b0;
  color: #8f2c22;
  background: rgba(178, 59, 46, 0.04);
}

.mini-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
  white-space: nowrap;
}

.pill-idle {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
}

.pill-running {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.pill-running :deep(svg) {
  animation: spin 1.2s linear infinite;
}

.pill-paused {
  background: rgba(164, 94, 5, 0.12);
  color: #a45e05;
}

.pill-done {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.task-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  padding: 10px 0 2px;
}

.pager-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-size: 13px;
  cursor: pointer;
}

.pager-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pager-info {
  color: var(--c-text-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.pager-info strong {
  color: var(--c-accent-primary);
}

.collector-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: 20px;
  align-items: stretch;
}

.quality-body {
  gap: 18px;
}

.quality-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quality-label {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.quality-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
}

.quality-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
}

.quality-row span {
  font-size: 12px;
  color: var(--c-text-muted);
}

.quality-row strong {
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--c-text-primary);
}

.freshness-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.freshness-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.freshness-label {
  font-size: 12px;
  color: var(--c-text-secondary);
}

.freshness-bar {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
}

.freshness-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--c-accent-primary);
}

.freshness-count {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-primary);
  min-width: 40px;
  text-align: right;
}

.quality-foot {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.quality-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
}

.quality-stat span {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.quality-stat strong {
  font-family: var(--font-serif);
  font-size: 18px;
  color: var(--c-text-primary);
}

.log-body {
  padding-bottom: 16px;
}

.log-stream {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 6px 4px 14px;
  border-left: 2px solid var(--c-border-glass);
}

.log-line {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
}

.log-meta-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-level {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 700;
}

.log-level.info {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.log-level.warn {
  background: rgba(164, 94, 5, 0.12);
  color: #a45e05;
}

.log-level.error {
  background: rgba(178, 59, 46, 0.1);
  color: #b23b2e;
}

.log-worker,
.log-time,
.log-chip {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-secondary);
}

.log-time {
  margin-left: auto;
}

.log-message {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.55;
  color: var(--c-text-primary);
  word-break: break-word;
}

.task-detail-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(2px);
}

.task-detail-drawer {
  width: min(520px, 100%);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--c-bg-base-elevated);
  border-left: 1px solid var(--c-border-glass);
  box-shadow: -12px 0 32px rgba(15, 23, 42, 0.18);
}

.task-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--c-border-glass);
}

.task-detail-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.task-detail-sub {
  margin: 3px 0 0;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-muted);
  word-break: break-all;
}

.icon-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  cursor: pointer;
}

.task-detail-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-section-title {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin: 0;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  border: 1px solid var(--c-border-glass);
}

.detail-row dt {
  font-size: 11px;
  color: var(--c-text-muted);
}

.detail-row dd {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-primary);
  word-break: break-all;
}

.detail-pre {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}

.detail-pre.error {
  background: rgba(178, 59, 46, 0.08);
  color: #b23b2e;
  border-color: rgba(178, 59, 46, 0.22);
}

.detail-pre.subtle {
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
}

.detail-timeline {
  margin: 0;
  padding: 0 0 0 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--c-text-secondary);
  font-size: 12.5px;
}

.detail-timeline li {
  display: grid;
  grid-template-columns: 110px auto 1fr;
  gap: 8px;
  align-items: baseline;
}

.timeline-time {
  font-family: var(--font-mono);
  color: var(--c-text-muted);
  font-size: 11.5px;
}

.timeline-label {
  color: var(--c-text-primary);
  font-weight: 600;
}

.timeline-detail {
  color: var(--c-text-muted);
}

.collapse-enter-active,
.collapse-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 180ms ease;
}

.drawer-fade-enter-active .task-detail-drawer,
.drawer-fade-leave-active .task-detail-drawer {
  transition: transform 220ms ease;
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-fade-enter-from .task-detail-drawer,
.drawer-fade-leave-to .task-detail-drawer {
  transform: translateX(24px);
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1279px) {
  .collector-bottom {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .live-data-grid,
  .live-shard-section,
  .live-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .metrics-grid,
  .live-data-grid,
  .live-shard-section,
  .quality-list,
  .quality-foot,
  .live-stats {
    grid-template-columns: 1fr;
  }

  .collector-panel-head,
  .form-row,
  .collector-panel-tools,
  .task-actions,
  .task-head {
    flex-direction: column;
    align-items: stretch;
  }

  .collector-panel-body {
    padding: 14px 16px 16px;
  }

  .collector-panel-head,
  .task-detail-head {
    padding: 16px;
  }

  .task-detail-body {
    padding: 16px;
  }

  .detail-grid,
  .detail-timeline li {
    grid-template-columns: 1fr;
  }
}
</style>
