import assert from "node:assert/strict"
import { createHash } from "node:crypto"
import { readFile } from "node:fs/promises"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import path from "node:path"

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")

const expectedIconHashes = new Map([
  ["docs/icon.png", "fbedd697c30754db5b7abe58f2caf52e3136201cdcf236b09f28a8610d90f467"],
  ["docs/favicon.ico", "6b701a7a3f18bf65f48b3749f03f882a52b9ba3c69af7929f4e6febca2e98c4d"],
  ["docs/favicon-32x32.png", "08f69e8c15e55c9394bbe9192be0c7d5f7f402c0ff160e8fef1f6420deb5f3ee"],
  ["docs/apple-touch-icon.png", "6c72ab222bf081c413eee02e51294a75fab9ab2bd67c12ea885a8ba9ce771de3"],
  ["docs/icon-192.png", "d7b1379595c1d838e67b751074ba867bd1c128e87ee448e7240bd1277582f5be"],
  ["docs/icon-512.png", "58cf82b21502de579f735365c3fdf5e208bdbde4434ce22a9047fd6fe09336f2"],
  ["app/src/main/res/mipmap-mdpi/ic_launcher.png", "d99f737b271f645d475c5c88d65179cbd2e7e3decd17ea4012adf79ec43eb5ad"],
  ["app/src/main/res/mipmap-hdpi/ic_launcher.png", "2c3f63d1c496484af5a8e11c01b437a06ef262e79e5ddaf62468397afa518bb2"],
  ["app/src/main/res/mipmap-xhdpi/ic_launcher.png", "34da0c2e8bd8af16dc319dffc39fea04ae50c8b8e0af5a2ca41575683c6af2ad"],
  ["app/src/main/res/mipmap-xxhdpi/ic_launcher.png", "b4fdc1387f482119c54f09bff6a1e17ec7fc1c70d9e493ba721f589bf17511a2"],
  ["app/src/main/res/mipmap-xxxhdpi/ic_launcher.png", "affa67fc2efbd1467eb5215d7ca179b8c6e0c294ddcc52083fd36dacc0157591"],
  ["app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png", "1fa30da0c723051ffeeb2b7b2ae41d1e5c48c97eac9fd6783fef754c7219ef7a"],
  ["app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png", "bae12e36cc342e04ebaac430fcb41cb70ba8877e0a43fa33f24060f0342f5cde"],
  ["app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png", "fa647b893b38a2396a7d3b63137737d417d2e580331d5563b0ffbcbd3a9e2b33"],
  ["app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png", "5412df890df45ed62c1754f3fc97883116f52db976c2c839fb80411484625246"],
  ["app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png", "711d1ba914e4c9a93c8a8885d94b29e425ca778d682f76069a2c5f17e2ed93fb"],
  ["app/src/main/res/mipmap-mdpi/ic_launcher_round.png", "8e669218e0e5da447e99026632e15f22938a181cb0229695c952f82cf1159411"],
  ["app/src/main/res/mipmap-hdpi/ic_launcher_round.png", "c7551fd2e6895a7a65f23b22f33ac3f51c751898343799bc7115cbbe49c146e9"],
  ["app/src/main/res/mipmap-xhdpi/ic_launcher_round.png", "93c56da3a2d30addbc5534314f94ffbc5dae59e1dc87033227663147cf616e9c"],
  ["app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png", "a43526d135e3be95bd603787de2ad176b0ed4fe868bdd878dd93e9cd1585a73e"],
  ["app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png", "6d28cd7bc73eb25ba66a233b954c6626c05376c5776de1b9d18eaa545870480d"],
])

test("all Android and website brand assets use the approved new icon", async () => {
  for (const [relativePath, expectedHash] of expectedIconHashes) {
    const bytes = await readFile(path.join(projectRoot, relativePath))
    const actualHash = createHash("sha256").update(bytes).digest("hex")
    assert.equal(actualHash, expectedHash, relativePath)
  }
})

test("README and every website page reference the current icon set", async () => {
  const cacheVersion = "20260901"
  const readme = await readFile(path.join(projectRoot, "README.md"), "utf8")
  const pageNames = ["index.html", "privacy.html", "terms.html", "versions.html"]
  const pages = await Promise.all(
    pageNames.map(name => readFile(path.join(projectRoot, "docs", name), "utf8"))
  )

  assert.match(readme, new RegExp(`docs/icon\\.png\\?v=${cacheVersion}`, "g"))
  assert.doesNotMatch(readme, /docs\/icon\.png\?v=20260828b/)

  for (const [index, html] of pages.entries()) {
    assert.match(html, new RegExp(`favicon\\.ico\\?v=${cacheVersion}`), pageNames[index])
    assert.match(html, new RegExp(`favicon-32x32\\.png\\?v=${cacheVersion}`), pageNames[index])
    assert.match(html, new RegExp(`apple-touch-icon\\.png\\?v=${cacheVersion}`), pageNames[index])
    assert.match(html, new RegExp(`site\\.webmanifest\\?v=${cacheVersion}`), pageNames[index])
    assert.doesNotMatch(html, /(?:icon\.png|favicon[^"']*|apple-touch-icon\.png|site\.webmanifest)\?v=20260828b/, pageNames[index])
  }

  const manifest = await readFile(path.join(projectRoot, "docs", "site.webmanifest"), "utf8")
  assert.match(manifest, new RegExp(`icon-192\\.png\\?v=${cacheVersion}`))
  assert.match(manifest, new RegExp(`icon-512\\.png\\?v=${cacheVersion}`))
})
