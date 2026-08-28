export const PAGE_SIZE = 5

const TAG_PATTERN = /^v\d+\.\d+\.\d+$/

function normalizePageNumber(value) {
  const page = Number.parseInt(String(value), 10)
  return Number.isFinite(page) && page > 0 ? page : 1
}

export function paginateReleases(releases, requestedPage, pageSize = PAGE_SIZE) {
  const safePageSize = Math.max(1, Number.parseInt(String(pageSize), 10) || PAGE_SIZE)
  const totalPages = Math.max(1, Math.ceil(releases.length / safePageSize))
  const page = Math.min(normalizePageNumber(requestedPage), totalPages)
  const start = (page - 1) * safePageSize

  return {
    page,
    totalPages,
    items: releases.slice(start, start + safePageSize),
  }
}

export function paginationTokens(totalPages, currentPage) {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1)
  }

  const pages = new Set([1, totalPages, currentPage - 1, currentPage, currentPage + 1])
  const ordered = [...pages].filter(page => page >= 1 && page <= totalPages).sort((a, b) => a - b)
  const tokens = []

  ordered.forEach((page, index) => {
    if (index > 0 && page - ordered[index - 1] > 1) tokens.push("ellipsis")
    tokens.push(page)
  })

  return tokens
}

function stripMarkdown(value) {
  return value
    .replace(/^[-*+]\s+/, "")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/\[([^\]]+)\]\([^\)]+\)/g, "$1")
    .replace(/^[^\p{L}\p{N}]+/u, "")
    .trim()
}

export function extractReleaseSummary(body) {
  if (typeof body !== "string") return "查看本次版本的完整发布说明。"

  const summary = body
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line && !line.startsWith("#"))
    .map(stripMarkdown)
    .find(line => (
      line &&
      !/^银杏 v\d+\.\d+\.\d+ 发布$/i.test(line) &&
      !/^(发布信息|更新内容|主要更新|兼容性说明|验证|版本|版本号|系统要求|APK)[:：]?/i.test(line)
    ))

  if (!summary) return "查看本次版本的完整发布说明。"
  return summary.length > 96 ? `${summary.slice(0, 95)}…` : summary
}

function releaseTitle(name, tag) {
  if (typeof name !== "string") return "版本更新"

  const cleaned = name
    .replace(tag, "")
    .replace(/银杏|发布/gu, "")
    .replace(/[^\p{L}\p{N}\s]/gu, "")
    .trim()

  return cleaned || "版本更新"
}

function formatReleaseDate(value) {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}/.test(value)
    ? value.slice(0, 10).replaceAll("-", ".")
    : "日期待确认"
}

export function normalizeGitHubReleases(payload) {
  if (!Array.isArray(payload)) return []

  return payload
    .filter(release => (
      release &&
      release.draft !== true &&
      release.prerelease !== true &&
      TAG_PATTERN.test(release.tag_name) &&
      Array.isArray(release.assets) &&
      release.assets.some(asset => asset?.name === "app-release.apk") &&
      typeof release.published_at === "string"
    ))
    .map(release => {
      const tag = release.tag_name
      return {
        tag,
        title: releaseTitle(release.name, tag),
        summary: extractReleaseSummary(release.body),
        publishedAt: release.published_at,
        displayDate: formatReleaseDate(release.published_at),
        downloadUrl: `https://github.com/bjfwan/yinxing/releases/download/${tag}/app-release.apk`,
        detailUrl: `https://github.com/bjfwan/yinxing/releases/tag/${tag}`,
      }
    })
    .sort((left, right) => Date.parse(right.publishedAt) - Date.parse(left.publishedAt))
}

function collectFallbackReleases(list) {
  return [...list.children].map(element => {
    const tag = element.querySelector(".release-content h3 span")?.textContent?.trim() || ""
    const time = element.querySelector("time")
    return {
      tag,
      publishedAt: time?.dateTime || "",
      displayDate: time?.textContent?.trim() || "",
      element,
    }
  }).filter(release => TAG_PATTERN.test(release.tag))
}

function makeElement(tagName, className, text) {
  const element = document.createElement(tagName)
  if (className) element.className = className
  if (text) element.textContent = text
  return element
}

function createReleaseElement(release) {
  const item = document.createElement("li")
  const article = makeElement("article", "release-entry")
  const meta = makeElement("div", "release-meta")
  const time = makeElement("time", "", release.displayDate)
  time.dateTime = release.publishedAt.slice(0, 10)
  meta.append(time)

  const content = makeElement("div", "release-content")
  const heading = document.createElement("h3")
  heading.append(makeElement("span", "", release.tag), document.createTextNode(` ${release.title}`))
  content.append(heading, makeElement("p", "", release.summary))

  const actions = makeElement("div", "release-actions")
  const download = makeElement("a", "release-download", "下载 APK")
  download.href = release.downloadUrl
  const details = makeElement("a", "", "完整说明")
  details.href = release.detailUrl
  details.target = "_blank"
  details.rel = "noopener noreferrer"
  actions.append(download, details)

  article.append(meta, content, actions)
  item.append(article)
  return item
}

function syncReleaseElement(element, release, isCurrent) {
  const article = element.querySelector(".release-entry")
  const meta = element.querySelector(".release-meta")
  const time = element.querySelector("time")
  const download = element.querySelector(".release-download")
  const details = element.querySelector(".release-actions a:last-child")

  article?.classList.toggle("release-entry-current", isCurrent)
  meta?.querySelector(".release-status")?.remove()

  if (time) {
    time.dateTime = release.publishedAt.slice(0, 10)
    time.textContent = release.displayDate
  }

  if (isCurrent && meta) meta.append(makeElement("span", "release-status", "当前版本"))
  if (download) download.href = release.downloadUrl
  if (details) details.href = release.detailUrl
}

function updateLatestRelease(release) {
  document.querySelectorAll("[data-latest-version]").forEach(element => {
    element.textContent = release.tag
  })

  document.querySelectorAll("[data-latest-date]").forEach(element => {
    element.textContent = release.displayDate
    element.dateTime = release.publishedAt.slice(0, 10)
  })

  document.querySelectorAll("[data-latest-download]").forEach(element => {
    element.href = release.downloadUrl
    element.setAttribute("download", `银杏-${release.tag}.apk`)
  })
}

function requestedPage() {
  return normalizePageNumber(new URL(window.location.href).searchParams.get("page"))
}

function writePageToUrl(page) {
  const url = new URL(window.location.href)
  if (page === 1) url.searchParams.delete("page")
  else url.searchParams.set("page", String(page))
  url.hash = "all-releases"
  window.history.pushState({ page }, "", url)
}

function paginationButton(label, page, currentPage, onSelect, disabled = false) {
  const button = makeElement("button", "pagination-button", label)
  button.type = "button"
  button.disabled = disabled
  if (page === currentPage) button.setAttribute("aria-current", "page")
  button.addEventListener("click", () => onSelect(page))
  return button
}

function initReleaseHistory() {
  const list = document.querySelector("[data-release-list]")
  const pagination = document.querySelector("[data-release-pagination]")
  const paginationShell = document.querySelector("[data-pagination-shell]")
  const pageStatus = document.querySelector("[data-release-page-status]")
  const releaseCount = document.querySelector("[data-release-count]")
  const syncStatus = document.querySelector("[data-release-sync]")

  if (!list || !pagination || !paginationShell || !pageStatus) return

  const state = {
    releases: collectFallbackReleases(list),
    page: requestedPage(),
  }

  const render = ({ moveToList = false } = {}) => {
    const result = paginateReleases(state.releases, state.page)
    state.page = result.page
    list.replaceChildren(...result.items.map(release => release.element))
    pageStatus.textContent = `第 ${result.page} 页，共 ${result.totalPages} 页`
    paginationShell.hidden = false
    pagination.replaceChildren()

    const selectPage = page => {
      state.page = page
      writePageToUrl(page)
      render({ moveToList: true })
    }

    pagination.append(paginationButton("上一页", result.page - 1, result.page, selectPage, result.page === 1))
    paginationTokens(result.totalPages, result.page).forEach(token => {
      if (token === "ellipsis") {
        pagination.append(makeElement("span", "pagination-ellipsis", "…"))
      } else {
        pagination.append(paginationButton(String(token), token, result.page, selectPage))
      }
    })
    pagination.append(paginationButton("下一页", result.page + 1, result.page, selectPage, result.page === result.totalPages))

    if (moveToList) {
      document.getElementById("all-releases")?.scrollIntoView({ block: "start" })
      const firstEntry = list.querySelector(".release-entry")
      firstEntry?.setAttribute("tabindex", "-1")
      firstEntry?.focus({ preventScroll: true })
    }
  }

  render()

  window.addEventListener("popstate", () => {
    state.page = requestedPage()
    render()
  })

  fetch("./releases.json", { cache: "no-cache" })
    .then(response => {
      if (!response.ok) throw new Error(`Release manifest request failed: ${response.status}`)
      return response.json()
    })
    .then(payload => {
      const remoteReleases = normalizeGitHubReleases(payload)
      if (!remoteReleases.length) throw new Error("GitHub releases response was empty")

      const fallbackByTag = new Map(state.releases.map(release => [release.tag, release.element]))
      state.releases = remoteReleases.map((release, index) => {
        const element = fallbackByTag.get(release.tag) || createReleaseElement(release)
        syncReleaseElement(element, release, index === 0)
        return { ...release, element }
      })

      updateLatestRelease(state.releases[0])
      if (releaseCount) releaseCount.textContent = String(state.releases.length)
      if (syncStatus) {
        syncStatus.textContent = "已自动同步 GitHub 发布记录。"
        delete syncStatus.dataset.syncError
      }
      render()
    })
    .catch(error => {
      if (syncStatus) {
        syncStatus.textContent = "当前显示网站内保存的发布记录。"
        syncStatus.dataset.syncError = error instanceof Error ? error.message : "unknown_error"
      }
    })
}

if (typeof document !== "undefined") initReleaseHistory()
