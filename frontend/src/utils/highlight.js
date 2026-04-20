// Minimal highlight.js setup. The full `highlight.js` package ships every
// language grammar (150+), totaling ~970 kB gzipped. In this project we only
// render code blocks in AI chat markdown, where the LLM realistically emits
// a handful of common languages. By using `highlight.js/lib/core` and
// registering just what we need, the chunk shrinks to roughly 100 kB.
//
// If you need another language, register it here with the same pattern.

import hljs from 'highlight.js/lib/core'

import bash from 'highlight.js/lib/languages/bash'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import json from 'highlight.js/lib/languages/json'
import sql from 'highlight.js/lib/languages/sql'
import java from 'highlight.js/lib/languages/java'
import xml from 'highlight.js/lib/languages/xml' // also covers html/svg
import css from 'highlight.js/lib/languages/css'
import markdown from 'highlight.js/lib/languages/markdown'
import yaml from 'highlight.js/lib/languages/yaml'
import go from 'highlight.js/lib/languages/go'
import plaintext from 'highlight.js/lib/languages/plaintext'

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('sh', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('json', json)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('java', java)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('md', markdown)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('go', go)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('text', plaintext)

export default hljs
