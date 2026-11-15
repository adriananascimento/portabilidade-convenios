import {registerHooks} from 'node:module'
import {existsSync, readFileSync} from 'node:fs'
import ts from 'typescript'

// Executa o código de produção nos testes node:test, incluindo componentes TSX.
registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier.startsWith('.') && context.parentURL?.includes('/src/')) {
      for (const extensao of ['', '.ts', '.tsx']) {
        const url = new URL(specifier + extensao, context.parentURL)
        if (/\.tsx?$/.test(url.pathname) && existsSync(url)) return {url: url.href, shortCircuit: true}
      }
    }
    return nextResolve(specifier, context)
  },
  load(url, context, nextLoad) {
    if (/\.tsx?$/.test(url) && url.includes('/src/')) return {
      format: 'module', shortCircuit: true,
      source: ts.transpileModule(readFileSync(new URL(url), 'utf8'), {
        compilerOptions: {module: ts.ModuleKind.ESNext, jsx: ts.JsxEmit.ReactJSX},
      }).outputText,
    }
    return nextLoad(url, context)
  },
})
