import assert from "node:assert/strict"
import { createHash } from "node:crypto"
import { readFile } from "node:fs/promises"
import test from "node:test"

const root = new URL("../", import.meta.url)
const read = path => readFile(new URL(path, root))

test("release version and downloadable APK stay synchronized", async () => {
  const gradle = await read("app/build.gradle.kts").then(buffer => buffer.toString("utf8"))
  const versionCode = Number(gradle.match(/versionCode\s*=\s*(\d+)/)?.[1])
  const versionName = gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1]
  assert.ok(versionCode)
  assert.ok(versionName)

  const [readme, index, versions, update, releases, builtApk, websiteApk] = await Promise.all([
    read("README.md").then(buffer => buffer.toString("utf8")),
    read("docs/index.html").then(buffer => buffer.toString("utf8")),
    read("docs/versions.html").then(buffer => buffer.toString("utf8")),
    read("docs/update.json").then(buffer => JSON.parse(buffer.toString("utf8"))),
    read("docs/releases.json").then(buffer => JSON.parse(buffer.toString("utf8"))),
    read("app/build/outputs/apk/release/app-release.apk"),
    read("docs/app-release.apk"),
  ])
  const tag = `v${versionName}`
  const escapedTag = tag.replaceAll(".", "\\.")

  assert.match(readme, new RegExp(`source-${escapedTag}`))
  assert.match(readme, new RegExp("当前源码版本：`" + escapedTag + "`"))
  assert.match(index, new RegExp(`"softwareVersion": "${versionName.replaceAll(".", "\\.")}"`))
  assert.match(versions, new RegExp(`data-latest-version>${tag.replaceAll(".", "\\.")}<`))
  assert.equal(update.versionCode, versionCode)
  assert.equal(update.versionName, versionName)
  assert.equal(releases[0]?.tag_name, tag)
  assert.equal(releases[0]?.draft, false)
  assert.ok(releases[0]?.assets?.some(asset => asset.name === "app-release.apk"))

  const digest = buffer => createHash("sha256").update(buffer).digest("hex")
  assert.equal(digest(websiteApk), digest(builtApk))
})
