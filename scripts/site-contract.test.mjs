import assert from "node:assert/strict"
import { access, readFile } from "node:fs/promises"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import path from "node:path"

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const docsRoot = path.join(projectRoot, "docs")
const htmlPath = path.join(docsRoot, "index.html")
const versionsPath = path.join(docsRoot, "versions.html")
const releasesPath = path.join(docsRoot, "releases.json")
const cssPath = path.join(docsRoot, "assets", "css", "style.css")
const jsPath = path.join(docsRoot, "assets", "js", "main.js")
const versionsJsPath = path.join(docsRoot, "assets", "js", "versions.js")
const robotsPath = path.join(docsRoot, "robots.txt")
const sitemapPath = path.join(docsRoot, "sitemap.xml")
const releaseSyncPath = path.join(projectRoot, "scripts", "sync-release-history.mjs")
const releaseWorkflowPath = path.join(projectRoot, ".github", "workflows", "sync-release-history.yml")

test("footer routes to the site-owned complete release history", async () => {
  const html = await readFile(htmlPath, "utf8")
  const versions = await readFile(versionsPath, "utf8")

  assert.match(html, /href="\.\/versions\.html">历史版本<\/a>/)
  assert.match(html, /class="hero-history-link" href="\.\/versions\.html">历史发布版本/)
  assert.doesNotMatch(html, />全部发布<\/a>/)
  assert.match(versions, /<title>历史版本 \| 银杏 Yinxing<\/title>/)
  assert.match(versions, /仅支持 Android 7\+/)
  assert.match(versions, /href="\.\/app-release\.apk"/)
  assert.match(versions, /href="\.\/index\.html"/)
  assert.equal((versions.match(/class="release-entry/g) || []).length, 16)

  for (const version of ["v2.0.0", "v1.9.0", "v1.0.0"]) {
    assert.match(versions, new RegExp(`>${version.replaceAll(".", "\\.")}<`))
  }
  assert.doesNotMatch(versions, /v2\.0\.1/)

  await access(path.join(docsRoot, "app-release.apk"))
  await access(path.join(docsRoot, "assets", "css", "style.css"))
})

test("release history stays compact, paginates five entries, and follows GitHub Releases", async () => {
  const versions = await readFile(versionsPath, "utf8")

  assert.match(versions, /<h1[^>]*>历史版本<\/h1>/)
  assert.doesNotMatch(versions, /每一次更新|都留在这里/)
  assert.match(versions, /data-release-list/)
  assert.match(versions, /data-release-pagination/)
  assert.match(versions, /data-latest-version/)
  assert.match(versions, /type="module" src="\.\/assets\/js\/versions\.js"/)

  const moduleSource = await readFile(versionsJsPath, "utf8")
  const moduleUrl = `data:text/javascript;base64,${Buffer.from(moduleSource).toString("base64")}#test=${Date.now()}`
  const { PAGE_SIZE, normalizeGitHubReleases, paginateReleases } = await import(moduleUrl)
  const releases = Array.from({ length: 17 }, (_, index) => ({ tag: `v${17 - index}` }))

  assert.equal(PAGE_SIZE, 5)
  assert.deepEqual(paginateReleases(releases, 1).items.map(item => item.tag), ["v17", "v16", "v15", "v14", "v13"])
  assert.deepEqual(paginateReleases(releases, 4).items.map(item => item.tag), ["v2", "v1"])
  assert.equal(paginateReleases(releases, 99).page, 4)
  assert.equal(paginateReleases(releases, 0).page, 1)

  const normalized = normalizeGitHubReleases([
    {
      tag_name: "v3.0.0",
      published_at: "2026-09-01T08:00:00Z",
      body: "## 更新内容\n\n- 新版本自动出现",
      draft: false,
      assets: [{ name: "app-release.apk" }],
    },
    { tag_name: "unsafe-tag", published_at: "2026-09-01T08:00:00Z", draft: false, assets: [] },
  ])

  assert.equal(normalized.length, 1)
  assert.equal(normalized[0].tag, "v3.0.0")
  assert.equal(normalized[0].downloadUrl, "https://github.com/bjfwan/yinxing/releases/download/v3.0.0/app-release.apk")
})

test("release history reads a same-origin manifest that is refreshed after GitHub releases", async () => {
  const javascript = await readFile(versionsJsPath, "utf8")
  const manifest = JSON.parse(await readFile(releasesPath, "utf8"))
  const syncScript = await readFile(releaseSyncPath, "utf8")
  const workflow = await readFile(releaseWorkflowPath, "utf8")

  assert.match(javascript, /fetch\(["']\.\/releases\.json["']/)
  assert.doesNotMatch(javascript, /fetch\(RELEASES_API/)
  assert.ok(manifest.length >= 1)
  assert.match(manifest[0].tag_name, /^v\d+\.\d+\.\d+$/)
  assert.equal(manifest.at(-1).tag_name, "v1.0.0")
  assert.ok(manifest.every(release => release.assets.some(asset => asset.name === "app-release.apk")))
  assert.match(syncScript, /docs["'],\s*["']releases\.json/)
  assert.match(workflow, /release:\s*\n\s+types:\s*\[published, edited, deleted\]/)
  assert.match(workflow, /node scripts\/sync-release-history\.mjs/)
  assert.match(workflow, /contents:\s*write/)
})

test("official site keeps its download and visual assets local", async () => {
  const html = await readFile(htmlPath, "utf8")
  const localAssets = [...html.matchAll(/(?:src|href)="\.\/([^"#?]+)"/g)].map(match => match[1])

  assert.ok(localAssets.includes("app-release.apk"))
  assert.ok(localAssets.includes("assets/vendor/gsap.min.js"))
  assert.ok(localAssets.includes("assets/vendor/ScrollTrigger.min.js"))

  await Promise.all(localAssets.map(relativePath => access(path.join(docsRoot, relativePath))))
  await access(path.join(docsRoot, "assets", "vendor", "README.md"))
})

test("GSAP motion has responsive and reduced-motion fallbacks", async () => {
  const javascript = await readFile(jsPath, "utf8")
  const html = await readFile(htmlPath, "utf8")
  const gsapIndex = html.indexOf("assets/vendor/gsap.min.js")
  const scrollTriggerIndex = html.indexOf("assets/vendor/ScrollTrigger.min.js")
  const mainIndex = html.indexOf("assets/js/main.js")

  assert.ok(gsapIndex >= 0 && scrollTriggerIndex > gsapIndex && mainIndex > scrollTriggerIndex)
  assert.match(javascript, /gsap\.registerPlugin\(ScrollTrigger\)/)
  assert.match(javascript, /gsap\.matchMedia\(\)/)
  assert.match(javascript, /prefers-reduced-motion: reduce/)
  assert.match(javascript, /IntersectionObserver/)
  assert.match(javascript, /!\(\"IntersectionObserver\" in window\)/)
  assert.match(javascript, /gsap\.quickTo\(/)
  assert.match(javascript, /pointermove/)
  assert.doesNotMatch(javascript, /addEventListener\(["']scroll["']/)
})

test("desktop story starts directly with three crisp product chapters", async () => {
  const html = await readFile(htmlPath, "utf8")
  const stylesheet = await readFile(cssPath, "utf8")
  const javascript = await readFile(jsPath, "utf8")
  const experienceMarkup = html.match(/<section class="experience section"[\s\S]*?<\/section>/)?.[0] ?? ""

  assert.match(html, /class="experience section"[^>]*data-scroll-story/)
  assert.doesNotMatch(experienceMarkup, /data-story-opening|yinxing-family-hero\.webp/)
  assert.match(html, /data-story-product/)
  assert.equal((html.match(/data-story-copy/g) || []).length, 3)
  assert.equal((html.match(/data-story-screen/g) || []).length, 3)
  assert.doesNotMatch(stylesheet, /story-opening/)
  assert.match(stylesheet, /\.experience\.is-scroll-story\s*\{[^}]*height:\s*100dvh;/s)
  assert.match(stylesheet, /\.experience\.is-scroll-story \.experience-product\s*\{[^}]*position:\s*absolute;[^}]*inset:\s*0;/s)
  assert.match(stylesheet, /\.experience\.is-scroll-story \.screen-card\s*\{[^}]*position:\s*absolute;/s)
  assert.match(stylesheet, /\.experience\.is-scroll-story \.screen-card img\s*\{[^}]*height:\s*min\(78dvh,\s*660px\);/s)
  assert.match(javascript, /gsap\.timeline\(\{[\s\S]*scrollTrigger:\s*\{/)
  assert.match(javascript, /start:\s*["']top top["']/)
  assert.match(javascript, /end:\s*["']\+=480%["']/)
  assert.match(javascript, /pin:\s*true/)
  assert.match(javascript, /scrub:\s*1\.15/)
  assert.match(javascript, /invalidateOnRefresh:\s*true/)
  assert.match(javascript, /const storyProduct =/)
  assert.match(javascript, /gsap\.set\(storyProduct,\s*\{[^}]*xPercent:\s*0/s)
  assert.doesNotMatch(javascript, /storyOpening|storyOpeningImage/)
  assert.match(javascript, /clipPath:\s*["']inset\(0% 0% 0% 0%\)["']/)
  assert.match(javascript, /\.set\(homeCopy,\s*\{\s*autoAlpha:\s*0\s*\}/s)
  assert.match(javascript, /\.set\(settingsCopy,\s*\{\s*autoAlpha:\s*1\s*\}/s)
  assert.match(javascript, /\.set\(contactsCopy,\s*\{\s*autoAlpha:\s*1\s*\}/s)
  assert.doesNotMatch(javascript, /\.to\((?:homeCopy|settingsCopy|contactsCopy),\s*\{[^}]*autoAlpha:/s)
  assert.match(javascript, /addLabel\(["']home["'],\s*0\)/)
  assert.match(javascript, /addLabel\(["']settings["']/)
  assert.match(javascript, /addLabel\(["']contacts["']/)
  assert.match(javascript, /isDesktop\s*&&\s*finePointer\s*&&\s*!hasScrollStory/)
  assert.doesNotMatch(javascript, /blur\(/)
  assert.doesNotMatch(javascript, /scrollStoryTimeline\.scrollTrigger\?\.kill/)
})

test("editorial hero keeps photography unobstructed and device screenshots carry the interaction", async () => {
  const html = await readFile(htmlPath, "utf8")
  const stylesheet = await readFile(cssPath, "utf8")
  const javascript = await readFile(jsPath, "utf8")

  assert.doesNotMatch(html, /hero-phone/)
  assert.match(html, /class="hero-intro"/)
  assert.match(html, /class="hero-media"/)
  assert.match(html, /class="hero-line hero-line-primary"/)
  assert.match(html, /class="hero-line hero-line-accent"/)
  assert.match(html, /data-hero-photo/)
  assert.match(stylesheet, /ginkgo-atmosphere-v2\.webp/)
  assert.match(stylesheet, /\.hero-line-primary\s*\{[^}]*font-weight:\s*480/s)
  assert.match(stylesheet, /\.hero-line-accent\s*\{[^}]*margin-left:\s*clamp\(/s)
  assert.doesNotMatch(stylesheet, /\.hero-media::after/)
  assert.match(stylesheet, /\.hero-media\s*\{[^}]*;\s*height:\s*clamp\(290px,\s*40vh,\s*500px\)/s)
  assert.doesNotMatch(stylesheet, /\.hero-media\s*\{[^}]*min-height:/s)
  assert.match(javascript, /\[data-hero-photo\]/)
  assert.equal((html.match(/data-tilt-card/g) || []).length, 3)

  await access(path.join(docsRoot, "assets", "images", "ginkgo-atmosphere-v2.webp"))
})

test("product screenshot gallery is deliberately asymmetric", async () => {
  const html = await readFile(htmlPath, "utf8")
  const stylesheet = await readFile(cssPath, "utf8")

  assert.match(html, /screen-card-featured/)
  assert.match(html, /screen-card-secondary/)
  assert.match(stylesheet, /grid-template-columns:\s*minmax\(0,\s*1\.35fr\)\s+minmax\(210px,\s*0\.82fr\)\s+minmax\(210px,\s*0\.82fr\)/)
})

test("platform requirements and contact stay explicit without the retired spirit section", async () => {
  const html = await readFile(htmlPath, "utf8")

  assert.match(html, /仅支持 Android/)
  assert.match(html, /mailto:2632507193@qq\.com/)
  assert.doesNotMatch(html, /技术向前，也要有人不被落下|project-note|href="#spirit"/)
})

test("support scope separates ready, verified, partial, and unsupported capabilities", async () => {
  const html = await readFile(htmlPath, "utf8")

  assert.match(html, /id="support-scope"/)
  assert.match(html, /可以直接使用/)
  assert.match(html, /指定设备已验证/)
  assert.match(html, /真实 SIM/)
  assert.match(html, /微信视频来电自动接听/)
  assert.match(html, /远程协助、云同步和 iOS/)
})

test("typography uses explicit display and body roles", async () => {
  const html = await readFile(htmlPath, "utf8")
  const stylesheet = await readFile(cssPath, "utf8")

  assert.match(stylesheet, /--font-display:/)
  assert.match(stylesheet, /--font-body:/)
  assert.match(stylesheet, /text-wrap:\s*balance/)
  assert.match(stylesheet, /font-variant-numeric:\s*tabular-nums/)
  assert.ok((html.match(/class="heading-light"/g) || []).length >= 4)
  assert.ok((html.match(/class="heading-strong"/g) || []).length >= 4)
  assert.match(stylesheet, /\.heading-light\s*\{[^}]*font-weight:\s*480/s)
  assert.match(stylesheet, /\.heading-strong\s*\{[^}]*font-weight:\s*800/s)
})

test("real phone screenshots keep their intrinsic aspect ratio", async () => {
  const stylesheet = await readFile(cssPath, "utf8")

  assert.match(
    stylesheet,
    /\.screen-card img,\s*\.family-visual img\s*\{[^}]*height:\s*auto;/s
  )
})

test("editorial surfaces avoid generic rounded card containers", async () => {
  const stylesheet = await readFile(cssPath, "utf8")

  assert.match(stylesheet, /\.hero-intro\s*\{[^}]*border-radius:\s*0;/s)
  assert.match(stylesheet, /\.experience\s*\{[^}]*border-radius:\s*0;/s)
  assert.match(stylesheet, /\.screen-card\s*\{[^}]*border-radius:\s*0;/s)
  assert.match(stylesheet, /\.screen-card\s*\{[^}]*background:\s*transparent;/s)
  assert.match(stylesheet, /\.screen-card\s*\{[^}]*box-shadow:\s*none;/s)
  assert.doesNotMatch(stylesheet, /\.screen-card::before\s*\{/)
  assert.match(stylesheet, /\.support-scope-list\s*\{[^}]*gap:\s*0;/s)
  assert.match(stylesheet, /\.support-scope-list article\s*\{[^}]*border-radius:\s*0;/s)
  assert.match(stylesheet, /\.support-scope-list article\s*\{[^}]*box-shadow:\s*none;/s)
  assert.match(stylesheet, /\.faq details\s*\{[^}]*border-radius:\s*0;/s)
  assert.match(stylesheet, /\.faq details\s*\{[^}]*box-shadow:\s*none;/s)
  assert.match(stylesheet, /\.open-source\s*\{[^}]*border-radius:\s*0;/s)
  assert.match(stylesheet, /\.download\s*\{[^}]*border-radius:\s*0;/s)
  assert.doesNotMatch(stylesheet, /\.hero-media::after/)
})

test("marketing copy avoids retired design-document patterns", async () => {
  const html = await readFile(htmlPath, "utf8")

  assert.doesNotMatch(html, /[—–]/)
  assert.equal((html.match(/class="eyebrow"/g) || []).length, 1)
  assert.doesNotMatch(html, /设计 Token|section-kicker|Designed for elders/)
  assert.equal((html.match(/>免费下载 APK</g) || []).length, 3)
})

test("search engines receive canonical URLs, crawl rules, sitemap, and site identity", async () => {
  const html = await readFile(htmlPath, "utf8")
  const versions = await readFile(versionsPath, "utf8")
  const robots = await readFile(robotsPath, "utf8")
  const sitemap = await readFile(sitemapPath, "utf8")

  assert.match(html, /<link rel="canonical" href="https:\/\/yinxing\.722688\.xyz\/" \/>/)
  assert.match(versions, /<link rel="canonical" href="https:\/\/yinxing\.722688\.xyz\/versions" \/>/)
  assert.match(versions, /<meta property="og:url" content="https:\/\/yinxing\.722688\.xyz\/versions" \/>/)

  const structuredData = [...html.matchAll(/<script type="application\/ld\+json">\s*([\s\S]*?)\s*<\/script>/g)]
    .map((match) => JSON.parse(match[1]))
  const graph = structuredData.flatMap((entry) => entry["@graph"] || [entry])
  const website = graph.find((entry) => entry["@type"] === "WebSite")
  const application = graph.find((entry) => entry["@type"] === "SoftwareApplication")
  const versionsStructuredData = [...versions.matchAll(/<script type="application\/ld\+json">\s*([\s\S]*?)\s*<\/script>/g)]
    .map((match) => JSON.parse(match[1]))
  const collectionPage = versionsStructuredData.find((entry) => entry["@type"] === "CollectionPage")

  assert.deepEqual(
    {
      name: website?.name,
      alternateName: website?.alternateName,
      url: website?.url,
      language: website?.inLanguage,
    },
    {
      name: "银杏",
      alternateName: ["银杏 Yinxing", "Yinxing Launcher"],
      url: "https://yinxing.722688.xyz/",
      language: "zh-CN",
    }
  )
  assert.equal(application?.operatingSystem, "Android 7.0 or later")
  assert.equal(application?.isPartOf?.["@id"], "https://yinxing.722688.xyz/#website")
  assert.equal(collectionPage?.url, "https://yinxing.722688.xyz/versions")
  assert.equal(collectionPage?.about?.["@id"], "https://yinxing.722688.xyz/#software")

  assert.match(robots, /^User-agent: \*\r?\nAllow: \/\r?\n\r?\nSitemap: https:\/\/yinxing\.722688\.xyz\/sitemap\.xml\r?\n?$/)
  assert.deepEqual(
    [...sitemap.matchAll(/<loc>(.*?)<\/loc>/g)].map((match) => match[1]),
    [
      "https://yinxing.722688.xyz/",
      "https://yinxing.722688.xyz/versions",
      "https://yinxing.722688.xyz/privacy",
      "https://yinxing.722688.xyz/terms",
    ]
  )
  assert.doesNotMatch(sitemap, /pages\.dev|likeyou\.qzz\.io|versions\.html/)
})
