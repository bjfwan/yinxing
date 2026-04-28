const gatewayUrl = cleanUrl(process.env.AI_GATEWAY_BASE_URL || process.env.OLDLAUNCHER_AI_GATEWAY_URL || "https://oldlauncher-ai-gateway.2632507193.workers.dev");
const adminToken = process.env.PRO_ADMIN_SECRET || process.env.OLDLAUNCHER_ADMIN_TOKEN || process.env.APP_SHARED_SECRET || "";
const deviceId = process.argv[2] || "";

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});

async function main() {
  if (!adminToken) {
    throw new Error("请先设置 PRO_ADMIN_SECRET 或 OLDLAUNCHER_ADMIN_TOKEN");
  }
  if (!deviceId.trim()) {
    console.log("用法：npm run pro:revoke -- <device_id>");
    process.exit(1);
  }
  const response = await fetch(`${gatewayUrl}/pro/revoke`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": adminToken
    },
    body: JSON.stringify({
      device_id: deviceId.trim()
    })
  });
  const body = await readJson(response);
  if (!response.ok || !body.available) {
    throw new Error(`吊销失败：HTTP ${response.status} ${JSON.stringify(body)}`);
  }
  console.log(`已吊销：${body.device_id}`);
}

function cleanUrl(value) {
  return String(value || "").trim().replace(/\/+$/, "");
}

async function readJson(response) {
  const text = await response.text();
  return text ? JSON.parse(text) : {};
}
