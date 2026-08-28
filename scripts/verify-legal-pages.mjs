import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const read = (path) => readFileSync(resolve(root, path), "utf8");
const errors = [];

const requireText = (file, text, label = text) => {
  let content = "";
  try {
    content = read(file);
  } catch {
    errors.push(`${file}: 文件不存在`);
    return;
  }
  if (!content.includes(text)) errors.push(`${file}: 缺少 ${label}`);
};

requireText("docs/privacy.html", "<h1>隐私政策</h1>", "隐私政策标题");
requireText("docs/privacy.html", "./assets/css/legal.css", "法律页面样式");
requireText("docs/terms.html", "<h1>服务条款</h1>", "服务条款标题");
requireText("docs/terms.html", "./assets/css/legal.css", "法律页面样式");
requireText("docs/assets/css/legal.css", ".legal-main", "法律页面主布局");
requireText("docs/index.html", "./privacy.html", "隐私政策入口");
requireText("docs/index.html", "./terms.html", "服务条款入口");
requireText("docs/versions.html", "./privacy.html", "隐私政策入口");
requireText("docs/versions.html", "./terms.html", "服务条款入口");
requireText("docs/sitemap.xml", "/privacy", "隐私政策地址");
requireText("docs/sitemap.xml", "/terms", "服务条款地址");

if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exit(1);
}

console.log("法律页面与官网入口检查通过");
