import { execFileSync } from "node:child_process"
import { writeFile } from "node:fs/promises"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), "..")
const outputPath = join(projectRoot, "docs", "releases.json")
const tagPattern = /^v\d+\.\d+\.\d+$/

const response = execFileSync(
  "gh",
  ["api", "repos/bjfwan/yinxing/releases?per_page=100"],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 }
)

const releases = JSON.parse(response)
  .filter(release => (
    release &&
    release.draft !== true &&
    release.prerelease !== true &&
    tagPattern.test(release.tag_name) &&
    Array.isArray(release.assets) &&
    release.assets.some(asset => asset?.name === "app-release.apk")
  ))
  .map(release => ({
    tag_name: release.tag_name,
    name: release.name,
    published_at: release.published_at,
    body: release.body,
    draft: false,
    prerelease: false,
    assets: [{ name: "app-release.apk" }],
  }))
  .sort((left, right) => Date.parse(right.published_at) - Date.parse(left.published_at))

if (!releases.length) throw new Error("No public APK releases were returned by GitHub")

await writeFile(outputPath, `${JSON.stringify(releases, null, 2)}\n`, "utf8")
process.stdout.write(`Updated docs/releases.json with ${releases.length} releases.\n`)
