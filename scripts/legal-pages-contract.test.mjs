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
    assert.match(html, /rel="icon" type="image\/png" href="\.\/icon\.png\?v=20260828b"/)
    assert.match(html, /class="brand-logo" src="\.\/icon\.png\?v=20260828b"/)
    assert.match(html, /href="\.\/assets\/css\/legal\.css\?v=20260828b"/)
  }
})

test("service terms summarize the current repository license without an external license jump", async () => {
  const terms = await readFile(termsPath, "utf8")

  assert.match(terms, /id="software-license"/)
  assert.match(terms, /PolyForm Noncommercial License 1\.0\.0/)
  assert.match(terms, /非商业(?:用途|目的)/)
  assert.match(terms, /修改和分发/)
  assert.match(terms, /保留许可条款和 Required Notice/)
  assert.match(terms, /付费书面授权/)
  assert.match(terms, /历史版本继续适用其原有许可/)
  assert.doesNotMatch(terms, /href="https:\/\/(?:github\.com|polyformproject\.org)[^"]*(?:LICENSE|license|noncommercial)/i)
})

test("legal layout provides a responsive sticky navigation and avoids side-stripe callouts", async () => {
  const css = await readFile(legalCssPath, "utf8")

  assert.match(css, /\.legal-nav\s*\{[\s\S]*position:\s*sticky;/)
  assert.match(css, /@media \(max-width:\s*760px\)/)
  assert.match(css, /scroll-margin-top:/)
  assert.doesNotMatch(css, /border-left:\s*[2-9]/)
})
