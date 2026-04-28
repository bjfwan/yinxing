import assert from "node:assert/strict";
import test from "node:test";
import { handleRequest } from "../src/index.js";

class MemoryKv {
  constructor() {
    this.map = new Map();
  }

  async get(key) {
    return this.map.get(key) || null;
  }

  async put(key, value) {
    this.map.set(key, String(value));
  }

  async delete(key) {
    this.map.delete(key);
  }

  async list(options = {}) {
    const prefix = options.prefix || "";
    return {
      keys: [...this.map.keys()]
        .filter((key) => key.startsWith(prefix))
        .sort()
        .map((name) => ({ name })),
      list_complete: true
    };
  }
}

function env(extra = {}) {
  return {
    PRO_DEVICES: new MemoryKv(),
    APP_SHARED_SECRET: "admin-secret",
    DEEPSEEK_API_KEY: "deepseek-key",
    DEEPSEEK_MODEL: "deepseek-v4-flash",
    ...extra
  };
}

function request(path, init = {}) {
  return new Request(`https://oldlauncher.test${path}`, init);
}

async function json(response) {
  return response.json();
}

async function activate(runtime, deviceId = "device-123456", deviceToken = "token-1234567890") {
  await handleRequest(request("/pro/activate", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      device_id: deviceId,
      device_token: deviceToken
    })
  }), runtime);
  return {
    "content-type": "application/json",
    "x-oldlauncher-device-id": deviceId,
    "x-oldlauncher-device-token": deviceToken
  };
}

function wechatBody(mode = "cache_only") {
  return JSON.stringify({
    mode,
    step: "WAITING_VIDEO_OPTIONS",
    current_class: "com.tencent.mm.ui.widget.dialog.x3",
    target_alias: "wan.",
    failure_reason: "guard_video_options",
    nodes: [
      {
        text: "视频通话",
        content_description: "",
        view_id: "com.tencent.mm:id/video",
        class_name: "android.widget.TextView",
        clickable: true,
        editable: false
      }
    ]
  });
}

test("health exposes pro endpoints", async () => {
  const response = await handleRequest(request("/health"), env());
  const body = await json(response);
  assert.equal(body.ok, true);
  assert.equal(body.pro_configured, true);
  assert.ok(body.endpoints.includes("/pro/status"));
  assert.ok(body.endpoints.includes("/pro/redeem"));
  assert.ok(body.endpoints.includes("/pro/code/create"));
});

test("admin renders enhanced dashboard", async () => {
  const response = await handleRequest(request("/admin"), env());
  const html = await response.text();
  assert.equal(response.status, 200);
  assert.match(html, /授权运营看板/);
  assert.match(html, /AI 网关/);
  assert.match(html, /v1 地址/);
  assert.match(html, /拉取模型/);
  assert.match(html, /测试连接/);
  assert.match(html, /deviceDetail/);
  assert.match(html, /月套餐/);
  assert.match(html, /清理吊销码/);
  assert.match(html, /设备可用率/);
  assert.match(html, /生成并复制/);
  assert.match(html, /codeFilters/);
});

test("status requires device credentials", async () => {
  const response = await handleRequest(request("/pro/status"), env());
  const body = await json(response);
  assert.equal(body.available, false);
  assert.equal(body.error, "pro_required");
});

test("activate binds a device and status returns quotas", async () => {
  const runtime = env();
  const activate = await handleRequest(request("/pro/activate", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      device_id: "device-123456",
      device_token: "token-1234567890"
    })
  }), runtime);
  const activated = await json(activate);
  assert.equal(activated.available, true);
  const status = await handleRequest(request("/pro/status", {
    headers: {
      "x-oldlauncher-device-id": "device-123456",
      "x-oldlauncher-device-token": "token-1234567890"
    }
  }), runtime);
  const body = await json(status);
  assert.equal(body.available, true);
  assert.equal(body.quotas["call-risk"].remaining, 300);
  assert.equal(body.quotas["wechat-step"].remaining, 80);
});

test("activation code can be created and redeemed once", async () => {
  const runtime = env();
  const created = await handleRequest(request("/pro/code/create", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      note: "family activation"
    })
  }), runtime);
  const codeBody = await json(created);
  assert.equal(codeBody.available, true);
  assert.ok(codeBody.activation_code.startsWith("YX-"));
  const redeemed = await handleRequest(request("/pro/redeem", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-device-id": "device-redeem",
      "x-oldlauncher-device-token": "token-1234567890"
    },
    body: JSON.stringify({
      activation_code: codeBody.activation_code,
      device_name: "客厅手机"
    })
  }), runtime);
  const redeemBody = await json(redeemed);
  assert.equal(redeemBody.available, true);
  assert.equal(redeemBody.device_id, "device-redeem");
  const reused = await handleRequest(request("/pro/redeem", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-device-id": "device-redeem-2",
      "x-oldlauncher-device-token": "token-1234567890"
    },
    body: JSON.stringify({
      activation_code: codeBody.activation_code,
      device_name: "客厅手机"
    })
  }), runtime);
  const reuseBody = await json(reused);
  assert.equal(reuseBody.available, false);
  assert.equal(reuseBody.error, "activation_code_used");
});

test("activation code plans set device expiry", async () => {
  const runtime = env();
  const yearly = await handleRequest(request("/admin/api/codes", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      plan: "year"
    })
  }), runtime);
  const body = await json(yearly);
  assert.equal(body.available, true);
  assert.equal(body.plan, "年套餐");
  assert.ok(body.device_expires_at);
  const lifetime = await handleRequest(request("/admin/api/codes", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      plan: "lifetime"
    })
  }), runtime);
  const lifetimeBody = await json(lifetime);
  assert.equal(lifetimeBody.plan, "长期套餐");
  assert.equal(lifetimeBody.device_expires_at, "");
});

test("admin can clean revoked activation codes", async () => {
  const runtime = env();
  const created = await handleRequest(request("/admin/api/codes", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      note: "cleanup target"
    })
  }), runtime);
  const codeBody = await json(created);
  await handleRequest(request("/admin/api/code/revoke", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      activation_code: codeBody.activation_code
    })
  }), runtime);
  const cleaned = await handleRequest(request("/admin/api/codes/cleanup", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: "{}"
  }), runtime);
  const cleanBody = await json(cleaned);
  assert.equal(cleanBody.available, true);
  assert.equal(cleanBody.deleted, 1);
  const codes = await handleRequest(request("/admin/api/codes", {
    headers: {
      "x-oldlauncher-admin-token": "admin-secret"
    }
  }), runtime);
  const codesBody = await json(codes);
  assert.equal(codesBody.codes.length, 0);
});

test("admin lists generated codes and redeemed devices", async () => {
  const runtime = env();
  const created = await handleRequest(request("/admin/api/codes", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      note: "visual admin"
    })
  }), runtime);
  const codeBody = await json(created);
  await handleRequest(request("/pro/redeem", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-device-id": "device-admin",
      "x-oldlauncher-device-token": "token-1234567890"
    },
    body: JSON.stringify({
      activation_code: codeBody.activation_code,
      device_name: "客厅手机"
    })
  }), runtime);
  const devices = await handleRequest(request("/admin/api/devices", {
    headers: {
      "x-oldlauncher-admin-token": "admin-secret"
    }
  }), runtime);
  const deviceBody = await json(devices);
  assert.equal(deviceBody.available, true);
  assert.equal(deviceBody.devices[0].device_id, "device-admin");
  assert.equal(deviceBody.devices[0].device_name, "客厅手机");
  assert.equal(deviceBody.devices[0].plan, "月套餐");
  const codes = await handleRequest(request("/admin/api/codes", {
    headers: {
      "x-oldlauncher-admin-token": "admin-secret"
    }
  }), runtime);
  const codesBody = await json(codes);
  assert.equal(codesBody.available, true);
  assert.equal(codesBody.codes[0].redeemed, true);
  assert.equal(codesBody.codes[0].redeemed_device_id, "device-admin");
  assert.ok(codesBody.codes[0].created_at);
  assert.ok(codesBody.codes[0].redeemed_at);
});

test("admin ai overview returns balance and recorded usage", async () => {
  const runtime = env();
  const month = new Date().toISOString().slice(0, 7).replace("-", "");
  await runtime.PRO_DEVICES.put(`ai-usage:${month}:call-risk:requests`, "2");
  await runtime.PRO_DEVICES.put(`ai-usage:${month}:call-risk:total_tokens`, "123");
  await runtime.PRO_DEVICES.put(`ai-usage:${month}:wechat-step:requests`, "3");
  await runtime.PRO_DEVICES.put(`ai-usage:${month}:wechat-step:total_tokens`, "456");
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    is_available: true,
    balance_infos: [
      {
        currency: "CNY",
        total_balance: "9.80",
        granted_balance: "1.00",
        topped_up_balance: "8.80"
      }
    ]
  }), { status: 200, headers: { "content-type": "application/json" } });
  try {
    const response = await handleRequest(request("/admin/api/ai", {
      headers: {
        "x-oldlauncher-admin-token": "admin-secret"
      }
    }), runtime);
    const body = await json(response);
    assert.equal(body.available, true);
    assert.equal(body.key_configured, true);
    assert.equal(body.balance.available, true);
    assert.equal(body.balance.balances[0].total_balance, "9.80");
    assert.equal(body.usage.totals.requests, 5);
    assert.equal(body.usage.totals.total_tokens, 579);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("admin can configure ai provider and test models", async () => {
  const runtime = env({ DEEPSEEK_API_KEY: "" });
  const saved = await handleRequest(request("/admin/api/ai/config", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      base_url: "https://relay.example.com/v1",
      api_key: "relay-key",
      model: "relay-chat"
    })
  }), runtime);
  const savedBody = await json(saved);
  assert.equal(savedBody.available, true);
  assert.equal(savedBody.base_url, "https://relay.example.com/v1");
  assert.equal(savedBody.model, "relay-chat");
  assert.equal(savedBody.key_source, "saved");
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, init = {}) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith("/models")) {
      return new Response(JSON.stringify({
        data: [
          { id: "relay-chat", owned_by: "relay" },
          { id: "relay-fast", owned_by: "relay" }
        ]
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response(JSON.stringify({
      choices: [
        {
          message: {
            content: "pong"
          }
        }
      ]
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const models = await handleRequest(request("/admin/api/ai/models", {
      headers: {
        "x-oldlauncher-admin-token": "admin-secret"
      }
    }), runtime);
    const modelsBody = await json(models);
    assert.equal(modelsBody.available, true);
    assert.equal(modelsBody.models.length, 2);
    const tested = await handleRequest(request("/admin/api/ai/test", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-oldlauncher-admin-token": "admin-secret"
      },
      body: JSON.stringify({
        base_url: "https://relay.example.com/v1",
        model: "relay-fast"
      })
    }), runtime);
    const testedBody = await json(tested);
    assert.equal(testedBody.available, true);
    assert.equal(testedBody.tested_model, "relay-fast");
    assert.ok(calls.some((call) => call.url === "https://relay.example.com/v1/models"));
    assert.ok(calls.some((call) => call.url === "https://relay.example.com/v1/chat/completions"));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("ai calls use saved provider config and update device activity", async () => {
  const runtime = env({ DEEPSEEK_API_KEY: "" });
  const headers = await activate(runtime, "device-provider", "token-1234567890");
  await handleRequest(request("/admin/api/ai/config", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      base_url: "https://relay.example.com/v1",
      api_key: "relay-key",
      model: "relay-chat"
    })
  }), runtime);
  const originalFetch = globalThis.fetch;
  let calledUrl = "";
  let calledModel = "";
  globalThis.fetch = async (url, init = {}) => {
    calledUrl = String(url);
    calledModel = JSON.parse(init.body).model;
    return new Response(JSON.stringify({
      choices: [
        {
          message: {
            content: JSON.stringify({
              risk_level: "low",
              label: "风险较低",
              should_silence: false,
              confidence: 0.9,
              reason: "测试"
            })
          }
        }
      ],
      usage: {
        prompt_tokens: 11,
        completion_tokens: 7,
        total_tokens: 18
      }
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const result = await handleRequest(request("/ai/call-risk", {
      method: "POST",
      headers,
      body: JSON.stringify({
        incoming_number: "13800138000"
      })
    }), runtime);
    const body = await json(result);
    assert.equal(body.available, true);
    assert.equal(calledUrl, "https://relay.example.com/v1/chat/completions");
    assert.equal(calledModel, "relay-chat");
    const devices = await handleRequest(request("/admin/api/devices", {
      headers: {
        "x-oldlauncher-admin-token": "admin-secret"
      }
    }), runtime);
    const deviceBody = await json(devices);
    assert.ok(deviceBody.devices[0].last_active_at);
    assert.equal(deviceBody.devices[0].last_feature, "call-risk");
    assert.equal(deviceBody.devices[0].quotas["call-risk"].used, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("revoked device loses pro access", async () => {
  const runtime = env();
  await handleRequest(request("/pro/activate", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      device_id: "device-abcdef",
      device_token: "token-1234567890"
    })
  }), runtime);
  await handleRequest(request("/pro/revoke", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-oldlauncher-admin-token": "admin-secret"
    },
    body: JSON.stringify({
      device_id: "device-abcdef"
    })
  }), runtime);
  const status = await handleRequest(request("/pro/status", {
    headers: {
      "x-oldlauncher-device-id": "device-abcdef",
      "x-oldlauncher-device-token": "token-1234567890"
    }
  }), runtime);
  const body = await json(status);
  assert.equal(body.available, false);
  assert.equal(body.error, "pro_required");
});

test("wechat cache only miss does not consume model quota", async () => {
  const runtime = env();
  const headers = await activate(runtime);
  const response = await handleRequest(request("/ai/wechat-step", {
    method: "POST",
    headers,
    body: wechatBody("cache_only")
  }), runtime);
  const body = await json(response);
  assert.equal(body.available, false);
  assert.equal(body.error, "cache_miss");
  const status = await handleRequest(request("/pro/status", { headers }), runtime);
  const pro = await json(status);
  assert.equal(pro.quotas["wechat-step"].remaining, 80);
});

test("wechat resolve stores reusable cache", async () => {
  const runtime = env();
  const headers = await activate(runtime);
  const originalFetch = globalThis.fetch;
  let fetchCount = 0;
  globalThis.fetch = async () => {
    fetchCount++;
    return new Response(JSON.stringify({
      choices: [
        {
          message: {
            content: JSON.stringify({
              page: "video_options",
              next_action: "tap_video_option",
              target_text: "视频通话",
              confidence: 0.92,
              reason: "找到视频通话选项"
            })
          }
        }
      ]
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  try {
    const resolved = await handleRequest(request("/ai/wechat-step", {
      method: "POST",
      headers,
      body: wechatBody("resolve")
    }), runtime);
    const first = await json(resolved);
    assert.equal(first.available, true);
    assert.equal(first.model_called, true);
    assert.equal(first.cache_hit, false);
    assert.equal(fetchCount, 1);
    const cached = await handleRequest(request("/ai/wechat-step", {
      method: "POST",
      headers,
      body: wechatBody("cache_only")
    }), runtime);
    const second = await json(cached);
    assert.equal(second.available, true);
    assert.equal(second.cache_hit, true);
    assert.equal(second.model_called, false);
    assert.equal(second.next_action, "tap_video_option");
    assert.equal(fetchCount, 1);
    const status = await handleRequest(request("/pro/status", { headers }), runtime);
    const pro = await json(status);
    assert.equal(pro.quotas["wechat-step"].remaining, 79);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
