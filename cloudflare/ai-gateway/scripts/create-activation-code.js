const gatewayUrl = cleanUrl(process.env.AI_GATEWAY_BASE_URL || process.env.OLDLAUNCHER_AI_GATEWAY_URL || "https://oldlauncher-ai-gateway.2632507193.workers.dev");
const adminToken = process.env.PRO_ADMIN_SECRET || process.env.OLDLAUNCHER_ADMIN_TOKEN || process.env.APP_SHARED_SECRET || "";
const note = process.argv.slice(2).join(" ");

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});

async function main() {
  if (!adminToken) {
    throw new Error("请先设置 PRO_ADMIN_SECRET 或 OLDLAUNCHER_ADMIN_TOKEN");
  }
  const response = await fetch(`${gatewayUrl}/pro/code/create`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": adminToken
    },
    body: JSON.stringify({
      plan: "activation",
      note: note || "激活码开通",
      features: {
        "call-risk": true,
        "wechat-step": true
      }
    })
  });
  const body = await readJson(response);
  if (!response.ok || !body.available) {
    throw new Error(`生成失败：HTTP ${response.status} ${JSON.stringify(body)}`);
  }
  console.log(body.activation_code);
}

function cleanUrl(value) {
  return String(value || "").trim().replace(/\/+$/, "");
}

async function readJson(response) {
  const text = await response.text();
  return text ? JSON.parse(text) : {};
}
