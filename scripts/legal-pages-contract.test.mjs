import assert from "node:assert/strict"
import { readFile } from "node:fs/promises"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import path from "node:path"

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const docsRoot = path.join(projectRoot, "docs")
const privacyPath = path.join(docsRoot, "privacy.html")
const termsPath = path.join(docsRoot, "terms.html")
const legalCssPath = path.join(docsRoot, "assets", "css", "legal.css")
const licensePath = path.join(projectRoot, "LICENSE")
const licensingPath = path.join(projectRoot, "LICENSING.md")
const readmePath = path.join(projectRoot, "README.md")

test("legal pages expose accessible section navigation without JavaScript", async () => {
  const privacy = await readFile(privacyPath, "utf8")
  const terms = await readFile(termsPath, "utf8")

  for (const [html, ids] of [
    [privacy, ["information", "permissions", "third-parties", "retention", "protection", "contact"]],
    [terms, ["service", "permissions", "safety", "reasonable-use", "third-parties", "software-license", "liability", "changes", "contact"]],
  ]) {
    assert.match(html, /<nav class="legal-nav" aria-label="本页导航">/)
    for (const id of ids) {
      assert.match(html, new RegExp(`href="#${id}"`))
      assert.match(html, new RegExp(`<section id="${id}"`))
    }
    assert.doesNotMatch(html, /<script\b/i)
  }
})

test("contact email stays readable when the app WebView disables JavaScript", async () => {
  for (const pagePath of [privacyPath, termsPath]) {
    const html = await readFile(pagePath, "utf8")
    const emailLinkCount = (html.match(/href="mailto:2632507193@qq\.com"/g) || []).length
    const bypassCount = (html.match(/<!--email_off-->/g) || []).length

    assert.ok(emailLinkCount >= 1)
    assert.equal(bypassCount, emailLinkCount)
    assert.equal((html.match(/<!--\/email_off-->/g) || []).length, emailLinkCount)
  }
})

test("legal pages use the current icon with an explicit cache version", async () => {
  for (const pagePath of [privacyPath, termsPath]) {
    const html = await readFile(pagePath, "utf8")
    assert.match(html, /rel="icon" href="\.\/favicon\.ico\?v=20260901" sizes="any"/)
    assert.match(html, /rel="icon" type="image\/png" sizes="32x32" href="\.\/favicon-32x32\.png\?v=20260901"/)
    assert.match(html, /rel="apple-touch-icon" sizes="180x180" href="\.\/apple-touch-icon\.png\?v=20260901"/)
    assert.match(html, /rel="manifest" href="\.\/site\.webmanifest\?v=20260901"/)
    assert.match(html, /class="brand-logo" src="\.\/icon\.png\?v=20260901"/)
    assert.match(html, /href="\.\/assets\/css\/legal\.css\?v=20260828b"/)
  }
})

test("service terms summarize GNU GPL v3 obligations without an external license jump", async () => {
  const terms = await readFile(termsPath, "utf8")

  assert.match(terms, /id="software-license"/)
  assert.match(terms, /GNU General Public License v3\.0/)
  assert.match(terms, /允许商业使用/)
  assert.match(terms, /分发.*对应源代码/)
  assert.match(terms, /全部历史原创代码.*GPL-3\.0-only/)
  assert.match(terms, /已经取得的旧许可证权利仍然有效/)
  assert.doesNotMatch(terms, /PolyForm Noncommercial|Affero|付费书面授权/)
  assert.doesNotMatch(terms, /href="https:\/\/(?:github\.com|gnu\.org)[^"]*(?:LICENSE|license|gpl)/i)
})

test("repository declares GPL-3.0-only consistently", async () => {
  const [license, licensing, readme] = await Promise.all([
    readFile(licensePath, "utf8"),
    readFile(licensingPath, "utf8"),
    readFile(readmePath, "utf8"),
  ])

  assert.match(license, /^\s*GNU GENERAL PUBLIC LICENSE/m)
  assert.match(license, /Version 3, 29 June 2007/)
  assert.doesNotMatch(license, /Remote Network Interaction/)
  assert.match(licensing, /GPL-3\.0-only/)
  assert.match(licensing, /允许商业使用/)
  assert.match(licensing, /分发.*对应源代码/)
  assert.match(licensing, /全部历史原创代码.*GPL-3\.0-only/)
  assert.match(licensing, /已经取得的旧许可证权利仍然有效/)
  assert.match(readme, /license-GPL--3\.0/)
  assert.match(readme, /GNU General Public License v3\.0/)
  assert.match(readme, /全部历史原创代码.*GPL-3\.0-only/)
  assert.doesNotMatch(`${licensing}\n${readme}`, /PolyForm Noncommercial|Affero|商业用途需要.*授权/)
})

test("legal layout provides a responsive sticky navigation and avoids side-stripe callouts", async () => {
  const css = await readFile(legalCssPath, "utf8")

  assert.match(css, /\.legal-nav\s*\{[\s\S]*position:\s*sticky;/)
  assert.match(css, /@media \(max-width:\s*760px\)/)
  assert.match(css, /scroll-margin-top:/)
  assert.doesNotMatch(css, /border-left:\s*[2-9]/)
})

test("wechat teaching disclosure states purpose scope retention withdrawal and no cross-device sharing", async () => {
  const privacy = await readFile(privacyPath, "utf8")
  const terms = await readFile(termsPath, "utf8")

  assert.match(privacy, /微信视频示教/)
  assert.match(privacy, /控件类名、资源 ID、固定语义标签、相对位置、页面类名和状态指纹/)
  assert.match(privacy, /不包含联系人姓名、搜索文字、聊天内容、头像、图片、截图、录音或视频/)
  assert.match(privacy, /普通日志保存 30 天/)
  assert.match(privacy, /撤回.*同意/)
  assert.match(terms, /上传本次匿名演示数据.*默认勾选/)
  assert.match(terms, /开始前取消/)
  assert.match(terms, /不影响本地保存、验证和使用/)
  assert.match(terms, /不会自动下发或跨设备共享/)
})
