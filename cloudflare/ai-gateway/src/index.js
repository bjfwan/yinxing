const defaultAiBaseUrl = "https://api.deepseek.com";
const aiConfigKey = "config:ai";
const defaultModel = "deepseek-v4-flash";
const maxBodyBytes = 32000;
const wechatCacheTtl = 30 * 24 * 60 * 60;
const allowedWechatActions = new Set(["wait", "tap_search", "input_contact", "tap_contact", "tap_video_call", "tap_video_option", "fail"]);
const allowedWechatPages = new Set(["unknown", "wechat_home", "search_page", "search_result", "contact_detail", "video_options", "permission_dialog", "error_dialog", "chat_page"]);
const allowedRiskLevels = new Set(["low", "medium", "high"]);
const featureLimits = {
  "call-risk": 300,
  "wechat-step": 80
};
const minuteRateLimits = {
  "call-risk": 20,
  "wechat-step": 12,
  "wechat-cache": 120
};
const featureByPath = {
  "/ai/call-risk": "call-risk",
  "/ai/wechat-step": "wechat-step"
};

export default {
  async fetch(request, env, ctx) {
    return handleRequest(request, env, ctx);
  }
};

export async function handleRequest(request, env) {
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/favicon.ico") {
    return new Response(null, { status: 204, headers: { "cache-control": "public, max-age=86400" } });
  }
  if (request.method === "GET" && url.pathname === "/admin") {
    return sendHtml(adminPage());
  }
  if (request.method === "GET" && url.pathname === "/admin/api/summary") {
    return handleAdminSummary(request, env);
  }
  if (request.method === "GET" && url.pathname === "/admin/api/ai") {
    return handleAdminAi(request, env);
  }
  if (request.method === "GET" && url.pathname === "/admin/api/ai/models") {
    return handleAdminAiModels(request, env);
  }
  if (request.method === "POST" && url.pathname === "/admin/api/ai/config") {
    return handleAdminAiConfig(request, env);
  }
  if (request.method === "POST" && url.pathname === "/admin/api/ai/test") {
    return handleAdminAiTest(request, env);
  }
  if (request.method === "GET" && url.pathname === "/admin/api/devices") {
    return handleAdminDevices(request, env);
  }
  if (request.method === "GET" && url.pathname === "/admin/api/codes") {
    return handleAdminCodes(request, env);
  }
  if (request.method === "POST" && url.pathname === "/admin/api/codes") {
    return handleProCodeCreate(request, env);
  }
  if (request.method === "POST" && url.pathname === "/admin/api/codes/cleanup") {
    return handleAdminCodeCleanup(request, env);
  }
  if (request.method === "POST" && url.pathname === "/admin/api/code/revoke") {
    return handleProCodeRevoke(request, env);
  }
  if (request.method === "POST" && url.pathname === "/admin/api/device/revoke") {
    return handleProRevoke(request, env);
  }
  if (request.method === "GET" && url.pathname === "/health") {
    const ai = await readAiConfig(env);
    return send({
      ok: true,
      service: "oldlauncher-ai-gateway",
      model: ai.model,
      ai_base_url: ai.base_url,
      ai_configured: Boolean(ai.api_key),
      deepseek_configured: Boolean(ai.api_key),
      pro_configured: Boolean(env.PRO_DEVICES),
      endpoints: ["/health", "/admin", "/pro/status", "/pro/redeem", "/pro/code/create", "/pro/code/revoke", "/pro/activate", "/pro/revoke", "/ai/call-risk", "/ai/wechat-step"]
    });
  }
  if (request.method === "GET" && url.pathname === "/pro/status") {
    return handleProStatus(request, env);
  }
  if (request.method === "POST" && url.pathname === "/pro/redeem") {
    return handleProRedeem(request, env);
  }
  if (request.method === "POST" && url.pathname === "/pro/code/create") {
    return handleProCodeCreate(request, env);
  }
  if (request.method === "POST" && url.pathname === "/pro/code/revoke") {
    return handleProCodeRevoke(request, env);
  }
  if (request.method === "POST" && url.pathname === "/pro/activate") {
    return handleProActivate(request, env);
  }
  if (request.method === "POST" && url.pathname === "/pro/revoke") {
    return handleProRevoke(request, env);
  }
  const feature = featureByPath[url.pathname];
  if (!feature) {
    return send({ error: "not_found" }, 404);
  }
  if (request.method !== "POST") {
    return send({ error: "method_not_allowed" }, 405);
  }
  try {
    if (url.pathname === "/ai/wechat-step") {
      const guard = await authorizeFeaturePlan(request, env, "wechat-step");
      if (!guard.available) {
        return send(guard);
      }
      const rate = await consumeRate(env, guard.device.device_id, "wechat-cache");
      if (!rate.available) {
        return send(rate, 429);
      }
      return handleWechatStep(request, env, guard);
    }
    const guard = await authorizeFeaturePlan(request, env, feature);
    if (!guard.available) {
      return send(guard, guard.error === "rate_limited" ? 429 : 200);
    }
    const rate = await consumeRate(env, guard.device.device_id, feature);
    if (!rate.available) {
      return send(rate, 429);
    }
    const ai = await readAiConfig(env);
    if (!ai.api_key) {
      return send({ available: false, error: "ai_not_configured" }, 503);
    }
    const quota = await consumeQuota(env, guard.device.device_id, feature);
    if (!quota.available) {
      return send(quota);
    }
    if (url.pathname === "/ai/call-risk") {
      return handleCallRisk(request, env, ai);
    }
    return handleWechatStep(request, env, guard, ai);
  } catch (error) {
    return send({ available: false, error: "gateway_error" }, 500);
  }
}

async function handleProStatus(request, env) {
  const auth = await authorizeDevice(request, env);
  if (!auth.available) {
    return send(auth);
  }
  return send(await buildStatusPayload(env, auth.device));
}

async function handleAdminSummary(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  const devices = await listDevices(env);
  const codes = await listActivationCodes(env);
  return send({
    available: true,
    devices: devices.length,
    active_devices: devices.filter((device) => device.active && !device.revoked && !isExpired(device.expires_at)).length,
    codes: codes.length,
    usable_codes: codes.filter((code) => code.active && !code.redeemed && !isExpired(code.expires_at)).length,
    revoked_codes: codes.filter((code) => code.revoked).length
  });
}

async function handleAdminAi(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  const config = await readAiConfig(env);
  return send({
    available: true,
    ...publicAiConfig(config),
    balance: await readProviderBalance(config),
    usage: await readAiUsage(env)
  });
}

async function handleAdminAiModels(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  const result = await fetchAiModels(await readAiConfig(env));
  return send(result, result.available ? 200 : 502);
}

async function handleAdminAiConfig(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  const body = await readJson(request);
  const current = await readStoredAiConfig(env);
  const baseUrl = normalizeAiBaseUrl(body.base_url || current.base_url || env.AI_BASE_URL || env.DEEPSEEK_BASE_URL || defaultAiBaseUrl);
  if (!baseUrl) {
    return send({ available: false, error: "invalid_ai_base_url" }, 400);
  }
  const apiKey = normalizeApiKey(body.api_key);
  const next = {
    base_url: baseUrl,
    model: shortText(body.model || current.model || env.AI_MODEL || env.DEEPSEEK_MODEL || defaultModel, 100) || defaultModel,
    updated_at: new Date().toISOString()
  };
  if (!body.clear_key && apiKey) {
    next.api_key = apiKey;
  } else if (!body.clear_key && current.api_key) {
    next.api_key = current.api_key;
  }
  await env.PRO_DEVICES.put(aiConfigKey, JSON.stringify(next));
  const config = await readAiConfig(env);
  return send({
    available: true,
    ...publicAiConfig(config),
    balance: await readProviderBalance(config)
  });
}

async function handleAdminAiTest(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  const body = await readJson(request);
  if (body.base_url && !normalizeAiBaseUrl(body.base_url)) {
    return send({ available: false, error: "invalid_ai_base_url" }, 400);
  }
  const override = {
    base_url: body.base_url,
    model: body.model
  };
  const apiKey = normalizeApiKey(body.api_key);
  if (apiKey) {
    override.api_key = apiKey;
  }
  const result = await testAiConnection(await readAiConfig(env, override));
  return send(result, result.available ? 200 : 502);
}

async function handleAdminDevices(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  return send({
    available: true,
    devices: await listDevices(env)
  });
}

async function handleAdminCodes(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  return send({
    available: true,
    codes: await listActivationCodes(env)
  });
}

async function handleAdminCodeCleanup(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  return send({
    available: true,
    ...(await cleanupRevokedActivationCodes(env))
  });
}

async function handleProRedeem(request, env) {
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  const body = await readJson(request);
  const code = normalizeActivationCode(body.activation_code || body.code);
  if (!code) {
    return send({ available: false, error: "invalid_activation_code" }, 400);
  }
  const deviceId = normalizeDeviceId(request.headers.get("x-oldlauncher-device-id"));
  const deviceToken = normalizeDeviceToken(request.headers.get("x-oldlauncher-device-token"));
  if (!deviceId || !deviceToken) {
    return send({ available: false, error: "invalid_device_credentials" }, 400);
  }
  const activation = await readActivationCode(env, code);
  if (!activation) {
    return send({ available: false, error: "invalid_activation_code" }, 404);
  }
  if (activation.redeemed) {
    return send({ available: false, error: "activation_code_used" }, 409);
  }
  if (!activation.active || isExpired(activation.expires_at)) {
    return send({ available: false, error: "activation_code_expired" }, 410);
  }
  const existing = await readDevice(env, deviceId);
  const now = new Date().toISOString();
  const device = {
    device_id: deviceId,
    token_hash: await hashToken(deviceId, deviceToken),
    active: true,
    revoked: false,
    device_name: shortText(body.device_name || body.name || existing?.device_name || "", 48),
    plan: shortText(activation.plan || "activation", 24),
    plan_code: activation.plan_code || "",
    note: shortText(activation.note || "activation_code", 80),
    features: normalizeFeatures(activation.features),
    activated_at: existing?.activated_at || now,
    updated_at: now,
    last_active_at: existing?.last_active_at || "",
    last_feature: existing?.last_feature || "",
    expires_at: normalizeOptionalIsoDate(activation.device_expires_at || "")
  };
  await writeDevice(env, device);
  await writeActivationCode(env, {
    ...activation,
    active: false,
    redeemed: true,
    redeemed_at: now,
    redeemed_device_id: deviceId,
    updated_at: now
  });
  return send(await buildStatusPayload(env, device));
}

async function handleProCodeCreate(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  const body = await readJson(request);
  const activationCode = normalizeActivationCode(body.activation_code || body.code) || createActivationCode();
  const now = new Date().toISOString();
  const planCode = normalizePlanCode(body.plan || body.plan_code);
  const activation = {
    code_hash: await activationCodeHash(activationCode),
    active: true,
    redeemed: false,
    plan: planLabel(planCode),
    plan_code: planCode,
    note: shortText(body.note || "", 80),
    features: normalizeFeatures(body.features),
    created_at: now,
    updated_at: now,
    expires_at: normalizeOptionalIsoDate(body.expires_at || ""),
    device_expires_at: normalizeOptionalIsoDate(body.device_expires_at || "") || planDeviceExpiresAt(planCode, now)
  };
  await writeActivationCode(env, activation);
  return send({
    available: true,
    activation_code: activationCode,
    plan: activation.plan,
    plan_code: activation.plan_code,
    features: activation.features,
    expires_at: activation.expires_at,
    device_expires_at: activation.device_expires_at
  });
}

async function handleProCodeRevoke(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  const body = await readJson(request);
  const code = normalizeActivationCode(body.activation_code || body.code);
  if (!code) {
    return send({ available: false, error: "invalid_activation_code" }, 400);
  }
  const activation = await readActivationCode(env, code);
  if (!activation) {
    return send({ available: false, error: "activation_code_not_found" }, 404);
  }
  await writeActivationCode(env, {
    ...activation,
    active: false,
    updated_at: new Date().toISOString()
  });
  return send({ available: true });
}

async function handleProActivate(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  const body = await readJson(request);
  const deviceId = normalizeDeviceId(body.device_id);
  if (!deviceId) {
    return send({ available: false, error: "invalid_device_id" }, 400);
  }
  const deviceToken = normalizeDeviceToken(body.device_token) || createToken();
  const existing = await readDevice(env, deviceId);
  const now = new Date().toISOString();
  const planCode = normalizePlanCode(body.plan || body.plan_code || existing?.plan_code || existing?.plan);
  const device = {
    device_id: deviceId,
    token_hash: await hashToken(deviceId, deviceToken),
    active: true,
    revoked: false,
    device_name: shortText(body.device_name || body.name || existing?.device_name || "", 48),
    plan: planLabel(planCode),
    plan_code: planCode,
    note: shortText(body.note || existing?.note || "", 80),
    features: normalizeFeatures(body.features || existing?.features),
    activated_at: existing?.activated_at || now,
    updated_at: now,
    last_active_at: existing?.last_active_at || "",
    last_feature: existing?.last_feature || "",
    expires_at: normalizeOptionalIsoDate(body.expires_at || existing?.expires_at || "") || planDeviceExpiresAt(planCode, now)
  };
  await writeDevice(env, device);
  return send({
    available: true,
    active: true,
    device_id: deviceId,
    device_token: deviceToken,
    plan: device.plan,
    features: device.features,
    quotas: await readQuotas(env, device)
  });
}

async function handleProRevoke(request, env) {
  const admin = await authorizeAdmin(request, env);
  if (!admin.available) {
    return send(admin, admin.error === "admin_unauthorized" ? 401 : 503);
  }
  if (!env.PRO_DEVICES) {
    return send({ available: false, error: "pro_store_not_configured" }, 503);
  }
  const body = await readJson(request);
  const deviceId = normalizeDeviceId(body.device_id);
  if (!deviceId) {
    return send({ available: false, error: "invalid_device_id" }, 400);
  }
  const existing = await readDevice(env, deviceId);
  if (!existing) {
    return send({ available: false, error: "device_not_found" }, 404);
  }
  const device = {
    ...existing,
    active: false,
    revoked: true,
    updated_at: new Date().toISOString(),
    revoked_at: new Date().toISOString()
  };
  await writeDevice(env, device);
  return send({ available: true, active: false, device_id: deviceId });
}

async function handleCallRisk(request, env, aiConfig) {
  const body = await readJson(request);
  const input = buildCallRiskInput(body);
  const result = await askJson(env, [
    { role: "system", content: "你是老人手机的来电风险分析器。你不是号码库，不能编造归属地或精确机构。只能依据输入特征判断风险。只输出JSON。" },
    { role: "user", content: JSON.stringify({ task: "call_risk", schema: { risk_level: "low|medium|high", label: "short chinese label", should_silence: true, confidence: 0.8, reason: "short chinese reason" }, input }) }
  ], 350, "call-risk", aiConfig);
  return send(normalizeCallRisk(result));
}

async function handleWechatStep(request, env, guard, aiConfig) {
  const body = await readJson(request);
  const input = buildWechatInput(body);
  const mode = body.mode === "cache_only" ? "cache_only" : "resolve";
  const cacheKey = await buildWechatCacheKey(input);
  const cached = await readWechatCache(env, cacheKey);
  if (cached) {
    return send({ ...cached, cache_hit: true, model_called: false });
  }
  if (mode === "cache_only") {
    return send({ available: false, error: "cache_miss", cache_hit: false, model_called: false });
  }
  const config = aiConfig || await readAiConfig(env);
  if (!config.api_key) {
    return send({ available: false, error: "ai_not_configured" }, 503);
  }
  const rate = await consumeRate(env, guard.device.device_id, "wechat-step");
  if (!rate.available) {
    return send(rate, 429);
  }
  const quota = await consumeQuota(env, guard.device.device_id, "wechat-step");
  if (!quota.available) {
    return send(quota);
  }
  const result = await askJson(env, [
    { role: "system", content: "你是安卓微信无障碍流程分析器。你的任务是帮助老人手机的视频通话自动流程判断当前页面和下一步安全动作。禁止建议支付、转账、删除、授权、修改系统设置。只输出JSON。" },
    { role: "user", content: JSON.stringify({ task: "wechat_video_step", schema: { page: "unknown|wechat_home|search_page|search_result|contact_detail|video_options|permission_dialog|error_dialog|chat_page", next_action: "wait|tap_search|input_contact|tap_contact|tap_video_call|tap_video_option|fail", target_text: "visible text to act on", confidence: 0.8, reason: "short chinese reason" }, input }) }
  ], 450, "wechat-step", config);
  const normalized = normalizeWechatStep(result);
  if (normalized.available && normalized.next_action !== "fail") {
    await writeWechatCache(env, cacheKey, normalized);
  }
  return send({ ...normalized, cache_hit: false, model_called: true, quota_remaining: await remainingQuota(env, guard.device, "wechat-step") });
}

async function authorizeFeaturePlan(request, env, feature) {
  const auth = await authorizeDevice(request, env);
  if (!auth.available) {
    return auth;
  }
  if (!auth.device.features?.[feature]) {
    return { available: false, error: "pro_required" };
  }
  if (isExpired(auth.device.expires_at)) {
    return { available: false, error: "pro_required" };
  }
  return { available: true, device: auth.device };
}

async function authorizeFeature(request, env, feature) {
  const auth = await authorizeFeaturePlan(request, env, feature);
  if (!auth.available) {
    return auth;
  }
  const rate = await consumeRate(env, auth.device.device_id, feature);
  if (!rate.available) {
    return rate;
  }
  const quota = await consumeQuota(env, auth.device.device_id, feature);
  if (!quota.available) {
    return quota;
  }
  return { available: true, device: auth.device };
}

async function authorizeDevice(request, env) {
  if (!env.PRO_DEVICES) {
    return { available: false, error: "pro_required" };
  }
  const deviceId = normalizeDeviceId(request.headers.get("x-oldlauncher-device-id"));
  const deviceToken = normalizeDeviceToken(request.headers.get("x-oldlauncher-device-token"));
  if (!deviceId || !deviceToken) {
    return { available: false, error: "pro_required" };
  }
  const device = await readDevice(env, deviceId);
  if (!device || !device.active || device.revoked) {
    return { available: false, error: "pro_required" };
  }
  const expected = String(device.token_hash || "");
  const actual = await hashToken(deviceId, deviceToken);
  if (!constantTimeEqual(expected, actual)) {
    return { available: false, error: "pro_required" };
  }
  return { available: true, device };
}

async function authorizeAdmin(request, env) {
  const secret = env.PRO_ADMIN_SECRET || env.APP_SHARED_SECRET || "";
  if (!secret) {
    return { available: false, error: "admin_not_configured" };
  }
  const header = request.headers.get("x-oldlauncher-admin-token") || bearerToken(request.headers.get("authorization"));
  if (!header) {
    return { available: false, error: "admin_unauthorized" };
  }
  const expected = await sha256Hex(secret);
  const actual = await sha256Hex(header);
  if (!constantTimeEqual(expected, actual)) {
    return { available: false, error: "admin_unauthorized" };
  }
  return { available: true };
}

async function remainingQuota(env, device, feature) {
  const limit = featureLimits[feature] || 0;
  const month = monthKey();
  const key = `usage:${month}:${device.device_id}:${feature}`;
  const used = clampInt(await env.PRO_DEVICES.get(key), 0, limit);
  return Math.max(0, limit - used);
}

async function buildStatusPayload(env, device) {
  return {
    available: true,
    active: Boolean(device.active && !device.revoked && !isExpired(device.expires_at)),
    device_id: device.device_id,
    device_name: device.device_name || "",
    plan: device.plan || "pro",
    plan_code: device.plan_code || "",
    expires_at: device.expires_at || "",
    last_active_at: device.last_active_at || "",
    features: normalizeFeatures(device.features),
    quotas: await readQuotas(env, device)
  };
}

async function readQuotas(env, device) {
  const month = monthKey();
  const quotas = {};
  for (const [feature, limit] of Object.entries(featureLimits)) {
    const key = `usage:${month}:${device.device_id}:${feature}`;
    const used = clampInt(await env.PRO_DEVICES.get(key), 0, limit);
    quotas[feature] = {
      limit,
      used,
      remaining: Math.max(0, limit - used),
      enabled: Boolean(device.features?.[feature])
    };
  }
  return quotas;
}

async function consumeQuota(env, deviceId, feature) {
  const limit = featureLimits[feature] || 0;
  const month = monthKey();
  const key = `usage:${month}:${deviceId}:${feature}`;
  const current = clampInt(await env.PRO_DEVICES.get(key), 0, Number.MAX_SAFE_INTEGER);
  if (current >= limit) {
    return { available: false, error: "quota_exceeded" };
  }
  await env.PRO_DEVICES.put(key, String(current + 1), { expirationTtl: 70 * 24 * 60 * 60 });
  await touchDevice(env, deviceId, feature);
  return { available: true };
}

async function touchDevice(env, deviceId, feature) {
  const device = await readDevice(env, deviceId);
  if (!device) {
    return;
  }
  await writeDevice(env, {
    ...device,
    last_active_at: new Date().toISOString(),
    last_feature: feature
  });
}

async function consumeRate(env, deviceId, feature) {
  const limit = minuteRateLimits[feature] || 10;
  const key = `rate:${minuteKey()}:${deviceId}:${feature}`;
  const current = clampInt(await env.PRO_DEVICES.get(key), 0, Number.MAX_SAFE_INTEGER);
  if (current >= limit) {
    return { available: false, error: "rate_limited" };
  }
  await env.PRO_DEVICES.put(key, String(current + 1), { expirationTtl: 120 });
  return { available: true };
}

async function readDevice(env, deviceId) {
  const text = await env.PRO_DEVICES.get(`device:${deviceId}`);
  if (!text) {
    return null;
  }
  return JSON.parse(text);
}

async function writeDevice(env, device) {
  await env.PRO_DEVICES.put(`device:${device.device_id}`, JSON.stringify(device));
}

async function readActivationCode(env, code) {
  const text = await env.PRO_DEVICES.get(await activationCodeKey(code));
  if (!text) {
    return null;
  }
  return JSON.parse(text);
}

async function writeActivationCode(env, activation) {
  await env.PRO_DEVICES.put(`activation:${activation.code_hash}`, JSON.stringify(activation));
}

async function listDevices(env) {
  const keys = await listKeys(env, "device:");
  const devices = [];
  for (const key of keys) {
    const text = await env.PRO_DEVICES.get(key);
    if (!text) {
      continue;
    }
    try {
      const device = JSON.parse(text);
      devices.push({
        device_id: device.device_id,
        device_name: device.device_name || "",
        active: Boolean(device.active),
        revoked: Boolean(device.revoked),
        plan: device.plan || "activation",
        plan_code: device.plan_code || "",
        note: device.note || "",
        features: normalizeFeatures(device.features),
        activated_at: device.activated_at || "",
        updated_at: device.updated_at || "",
        last_active_at: device.last_active_at || "",
        last_feature: device.last_feature || "",
        expires_at: device.expires_at || "",
        revoked_at: device.revoked_at || "",
        quotas: await readQuotas(env, device)
      });
    } catch (error) {
      continue;
    }
  }
  return devices.sort((a, b) => String(b.updated_at).localeCompare(String(a.updated_at)));
}

async function listActivationCodes(env) {
  const keys = await listKeys(env, "activation:");
  const codes = [];
  for (const key of keys) {
    const text = await env.PRO_DEVICES.get(key);
    if (!text) {
      continue;
    }
    try {
      const code = JSON.parse(text);
      const active = Boolean(code.active);
      const redeemed = Boolean(code.redeemed);
      codes.push({
        code_hash: String(code.code_hash || key.slice("activation:".length)).slice(0, 16),
        active,
        redeemed,
        revoked: !active && !redeemed,
        plan: code.plan || "activation",
        plan_code: code.plan_code || "",
        note: code.note || "",
        created_at: code.created_at || "",
        updated_at: code.updated_at || "",
        expires_at: code.expires_at || "",
        device_expires_at: code.device_expires_at || "",
        redeemed_at: code.redeemed_at || "",
        redeemed_device_id: code.redeemed_device_id || ""
      });
    } catch (error) {
      continue;
    }
  }
  return codes.sort((a, b) => String(b.updated_at).localeCompare(String(a.updated_at)));
}

async function cleanupRevokedActivationCodes(env) {
  const keys = await listKeys(env, "activation:");
  let deleted = 0;
  let scanned = 0;
  for (const key of keys) {
    const text = await env.PRO_DEVICES.get(key);
    if (!text) {
      continue;
    }
    scanned++;
    try {
      const code = JSON.parse(text);
      if (!code.active && !code.redeemed) {
        await env.PRO_DEVICES.delete(key);
        deleted++;
      }
    } catch (error) {
      continue;
    }
  }
  return { deleted, scanned };
}

async function readStoredAiConfig(env) {
  if (!env.PRO_DEVICES) {
    return {};
  }
  const text = await env.PRO_DEVICES.get(aiConfigKey);
  if (!text) {
    return {};
  }
  try {
    const data = JSON.parse(text);
    return data && typeof data === "object" ? data : {};
  } catch (error) {
    return {};
  }
}

async function readAiConfig(env, override = {}) {
  const saved = await readStoredAiConfig(env);
  const hasInputKey = Object.prototype.hasOwnProperty.call(override, "api_key") && normalizeApiKey(override.api_key);
  const apiKey = hasInputKey ? normalizeApiKey(override.api_key) : normalizeApiKey(saved.api_key || env.AI_API_KEY || env.DEEPSEEK_API_KEY || "");
  const baseUrl = normalizeAiBaseUrl(override.base_url || saved.base_url || env.AI_BASE_URL || env.DEEPSEEK_BASE_URL || defaultAiBaseUrl) || defaultAiBaseUrl;
  const model = shortText(override.model || saved.model || env.AI_MODEL || env.DEEPSEEK_MODEL || defaultModel, 100) || defaultModel;
  const keySource = apiKey ? hasInputKey ? "input" : saved.api_key ? "saved" : "env" : "none";
  return {
    base_url: baseUrl,
    model,
    api_key: apiKey,
    key_source: keySource,
    saved: Boolean(saved.base_url || saved.model || saved.api_key),
    updated_at: saved.updated_at || ""
  };
}

function publicAiConfig(config) {
  return {
    base_url: config.base_url,
    model: config.model,
    key_configured: Boolean(config.api_key),
    key_source: config.key_source,
    key_preview: maskSecret(config.api_key),
    config_saved: Boolean(config.saved),
    updated_at: config.updated_at || ""
  };
}

async function readProviderBalance(config) {
  if (!config.api_key) {
    return { available: false, error: "ai_not_configured", balances: [] };
  }
  const url = providerBalanceUrl(config);
  if (!url) {
    return { available: false, error: "balance_unsupported", balances: [] };
  }
  try {
    const response = await fetch(url, {
      method: "GET",
      headers: { authorization: "Bearer " + config.api_key, accept: "application/json" }
    });
    if (!response.ok) {
      return { available: false, error: "balance_error", balances: [] };
    }
    const data = await response.json();
    return {
      available: Boolean(data.is_available),
      balances: Array.isArray(data.balance_infos) ? data.balance_infos.map((item) => ({
        currency: shortText(item.currency, 12),
        total_balance: shortText(item.total_balance, 24),
        granted_balance: shortText(item.granted_balance, 24),
        topped_up_balance: shortText(item.topped_up_balance, 24)
      })) : []
    };
  } catch (error) {
    return { available: false, error: "balance_unavailable", balances: [] };
  }
}

async function fetchAiModels(config) {
  if (!config.api_key) {
    return { available: false, error: "ai_not_configured", models: [] };
  }
  try {
    const response = await fetch(aiApiUrl(config, "/models"), {
      method: "GET",
      headers: { authorization: "Bearer " + config.api_key, accept: "application/json" }
    });
    if (!response.ok) {
      return { available: false, error: "models_error", status: response.status, models: [] };
    }
    const data = await response.json();
    const raw = Array.isArray(data.data) ? data.data : Array.isArray(data.models) ? data.models : Array.isArray(data) ? data : [];
    const models = raw.map((item) => typeof item === "string" ? { id: item } : { id: shortText(item.id || item.name || item.model, 100), owned_by: shortText(item.owned_by || item.owner || "", 60) }).filter((item) => item.id);
    return { available: true, models };
  } catch (error) {
    return { available: false, error: "models_unavailable", models: [] };
  }
}

async function testAiConnection(config) {
  if (!config.api_key) {
    return { available: false, error: "ai_not_configured", models: [] };
  }
  const models = await fetchAiModels(config);
  const testedModel = config.model || models.models?.[0]?.id || defaultModel;
  try {
    const response = await fetch(aiApiUrl(config, "/chat/completions"), {
      method: "POST",
      headers: { authorization: "Bearer " + config.api_key, "content-type": "application/json" },
      body: JSON.stringify({ model: testedModel, messages: [{ role: "user", content: "ping" }], temperature: 0, max_tokens: 8 })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      return { available: false, error: "chat_error", status: response.status, models_available: models.available, model_count: models.models.length, tested_model: testedModel };
    }
    const content = data?.choices?.[0]?.message?.content || data?.choices?.[0]?.text || "";
    return { available: true, models_available: models.available, model_count: models.models.length, tested_model: testedModel, reply: shortText(content, 80) };
  } catch (error) {
    return { available: false, error: "chat_unavailable", models_available: models.available, model_count: models.models.length, tested_model: testedModel };
  }
}

async function readAiUsage(env) {
  const month = monthKey();
  const fields = ["requests", "prompt_tokens", "completion_tokens", "total_tokens", "cache_hit_tokens", "cache_miss_tokens"];
  const features = {};
  const totals = Object.fromEntries(fields.map((field) => [field, 0]));
  if (!env.PRO_DEVICES) {
    return { month, features, totals };
  }
  for (const feature of Object.keys(featureLimits)) {
    const item = {};
    for (const field of fields) {
      const value = clampInt(await env.PRO_DEVICES.get(`ai-usage:${month}:${feature}:${field}`), 0, Number.MAX_SAFE_INTEGER);
      item[field] = value;
      totals[field] += value;
    }
    features[feature] = item;
  }
  return { month, features, totals };
}

async function recordAiUsage(env, feature, usage) {
  if (!env.PRO_DEVICES || !feature) {
    return;
  }
  const month = monthKey();
  const values = {
    requests: 1,
    prompt_tokens: clampInt(usage?.prompt_tokens, 0, Number.MAX_SAFE_INTEGER),
    completion_tokens: clampInt(usage?.completion_tokens, 0, Number.MAX_SAFE_INTEGER),
    total_tokens: clampInt(usage?.total_tokens, 0, Number.MAX_SAFE_INTEGER),
    cache_hit_tokens: clampInt(usage?.prompt_cache_hit_tokens, 0, Number.MAX_SAFE_INTEGER),
    cache_miss_tokens: clampInt(usage?.prompt_cache_miss_tokens, 0, Number.MAX_SAFE_INTEGER)
  };
  for (const [field, value] of Object.entries(values)) {
    if (value > 0) {
      await incrementKvNumber(env, `ai-usage:${month}:${feature}:${field}`, value, 400 * 24 * 60 * 60);
    }
  }
}

async function incrementKvNumber(env, key, amount, ttl) {
  const current = clampInt(await env.PRO_DEVICES.get(key), 0, Number.MAX_SAFE_INTEGER);
  await env.PRO_DEVICES.put(key, String(current + amount), { expirationTtl: ttl });
}

async function listKeys(env, prefix) {
  const keys = [];
  let cursor = undefined;
  for (;;) {
    const page = await env.PRO_DEVICES.list({ prefix, cursor, limit: 100 });
    keys.push(...page.keys.map((key) => key.name));
    if (page.list_complete || !page.cursor) {
      break;
    }
    cursor = page.cursor;
  }
  return keys;
}

async function readJson(request) {
  const text = await request.text();
  if (text.length > maxBodyBytes) {
    throw new Error("body_too_large");
  }
  return JSON.parse(text || "{}");
}

async function askJson(env, messages, maxTokens, feature, aiConfig) {
  const config = aiConfig || await readAiConfig(env);
  if (!config.api_key) {
    return { available: false, error: "ai_not_configured" };
  }
  const response = await fetch(aiApiUrl(config, "/chat/completions"), {
    method: "POST",
    headers: { authorization: "Bearer " + config.api_key, "content-type": "application/json" },
    body: JSON.stringify({ model: config.model, messages, response_format: { type: "json_object" }, temperature: 0, max_tokens: maxTokens })
  });
  if (!response.ok) {
    return { available: false, error: "ai_error" };
  }
  const data = await response.json();
  await recordAiUsage(env, feature, data.usage || {});
  const content = data && data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content;
  if (!content) {
    return { available: false, error: "empty_response" };
  }
  try {
    const parsed = JSON.parse(content);
    parsed.available = true;
    return parsed;
  } catch (error) {
    return { available: false, error: "invalid_json" };
  }
}

function buildCallRiskInput(body) {
  const digits = onlyDigits(body.incoming_number || body.number || "");
  return {
    known_contact: Boolean(body.known_contact),
    has_number: digits.length > 0,
    digit_length: digits.length,
    prefix2: digits.slice(0, 2),
    prefix3: digits.slice(0, 3),
    prefix4: digits.slice(0, 4),
    starts_with_plus: String(body.incoming_number || "").trim().startsWith("+"),
    recent_same_number_count: clampInt(body.recent_same_number_count, 0, 30),
    recent_unknown_count: clampInt(body.recent_unknown_count, 0, 100),
    hour_of_day: clampInt(body.hour_of_day, 0, 23),
    local_rule_label: shortText(body.local_rule_label, 24),
    local_rule_score: clampNumber(body.local_rule_score, 0, 1),
    device_locale: shortText(body.device_locale, 16)
  };
}

function buildWechatInput(body) {
  return {
    step: shortText(body.step, 40),
    current_class: shortText(body.current_class, 100),
    target_alias: shortText(body.target_alias, 40),
    failure_reason: shortText(body.failure_reason, 80),
    nodes: Array.isArray(body.nodes) ? body.nodes.slice(0, 80).map(safeNode) : []
  };
}

async function buildWechatCacheKey(input) {
  const nodes = input.nodes.map((node) => ({
    text: node.text,
    content_description: node.content_description,
    view_id: node.view_id,
    class_name: node.class_name,
    clickable: node.clickable,
    editable: node.editable
  }));
  const hash = await sha256Hex(JSON.stringify({ step: input.step, current_class: input.current_class, nodes }));
  return `wechat-step-cache:v1:${hash.slice(0, 40)}`;
}

async function readWechatCache(env, key) {
  if (!env.PRO_DEVICES) {
    return null;
  }
  const text = await env.PRO_DEVICES.get(key);
  if (!text) {
    return null;
  }
  try {
    return normalizeWechatStep(JSON.parse(text));
  } catch (error) {
    return null;
  }
}

async function writeWechatCache(env, key, decision) {
  if (!env.PRO_DEVICES) {
    return;
  }
  const cached = {
    ...decision,
    target_text: "",
    reason: shortText(decision.reason || "缓存决策", 60)
  };
  await env.PRO_DEVICES.put(key, JSON.stringify(cached), { expirationTtl: wechatCacheTtl });
}

function safeNode(node) {
  return {
    text: shortText(node && node.text, 40),
    content_description: shortText(node && node.content_description, 40),
    view_id: shortText(node && node.view_id, 80),
    class_name: shortText(node && node.class_name, 80),
    clickable: Boolean(node && node.clickable),
    editable: Boolean(node && node.editable)
  };
}

function normalizeCallRisk(result) {
  if (!result.available) {
    return { available: false, risk_level: "medium", label: "无法判断", should_silence: false, confidence: 0, reason: result.error || "服务暂不可用" };
  }
  const riskLevel = allowedRiskLevels.has(result.risk_level) ? result.risk_level : "medium";
  return { available: true, risk_level: riskLevel, label: shortText(result.label || defaultRiskLabel(riskLevel), 16), should_silence: Boolean(result.should_silence && riskLevel !== "low"), confidence: clampNumber(result.confidence, 0, 1), reason: shortText(result.reason, 60) };
}

function normalizeWechatStep(result) {
  if (!result.available) {
    return { available: false, page: "unknown", next_action: "fail", target_text: "", confidence: 0, reason: result.error || "服务暂不可用" };
  }
  const page = allowedWechatPages.has(result.page) ? result.page : "unknown";
  const nextAction = allowedWechatActions.has(result.next_action) ? result.next_action : "fail";
  const confidence = clampNumber(result.confidence, 0, 1);
  return { available: true, page, next_action: confidence >= 0.65 ? nextAction : "fail", target_text: shortText(result.target_text, 40), confidence, reason: shortText(result.reason, 60) };
}

function normalizeFeatures(value) {
  const input = value && typeof value === "object" ? value : {};
  return {
    "call-risk": input["call-risk"] !== false,
    "wechat-step": input["wechat-step"] !== false
  };
}

function normalizeAiBaseUrl(value) {
  const text = String(value || "").trim().replace(/\/+$/, "");
  if (!text) {
    return "";
  }
  try {
    const url = new URL(text);
    if (url.protocol !== "https:") {
      return "";
    }
    url.hash = "";
    url.search = "";
    let path = url.pathname.replace(/\/+$/, "");
    path = path.replace(/\/chat\/completions$/i, "").replace(/\/models$/i, "");
    url.pathname = path || "";
    return url.toString().replace(/\/+$/, "");
  } catch (error) {
    return "";
  }
}

function normalizeApiKey(value) {
  return String(value || "").trim().slice(0, 4096);
}

function aiApiUrl(config, path) {
  return `${config.base_url}${path}`;
}

function providerBalanceUrl(config) {
  try {
    const url = new URL(config.base_url);
    if (!/deepseek/i.test(url.hostname)) {
      return "";
    }
    return `${url.origin}/user/balance`;
  } catch (error) {
    return "";
  }
}

function maskSecret(value) {
  const text = String(value || "");
  if (!text) {
    return "";
  }
  if (text.length <= 10) {
    return `${text.slice(0, 2)}...${text.slice(-2)}`;
  }
  return `${text.slice(0, 4)}...${text.slice(-4)}`;
}

function normalizePlanCode(value) {
  const text = String(value || "").trim().toLowerCase();
  if (["year", "yearly", "annual", "年套餐", "一年套餐"].includes(text)) return "year";
  if (["lifetime", "forever", "long", "长期套餐", "永久套餐"].includes(text)) return "lifetime";
  return "month";
}

function planLabel(value) {
  const code = normalizePlanCode(value);
  if (code === "year") return "年套餐";
  if (code === "lifetime") return "长期套餐";
  return "月套餐";
}

function planDeviceExpiresAt(plan, now) {
  const code = normalizePlanCode(plan);
  if (code === "lifetime") {
    return "";
  }
  const date = new Date(now);
  if (code === "year") {
    date.setUTCFullYear(date.getUTCFullYear() + 1);
  } else {
    date.setUTCMonth(date.getUTCMonth() + 1);
  }
  return date.toISOString();
}

function defaultRiskLabel(level) {
  if (level === "high") return "疑似骚扰";
  if (level === "medium") return "陌生来电";
  return "风险较低";
}

function onlyDigits(value) {
  return String(value || "").replace(/\D/g, "");
}

function shortText(value, max) {
  return String(value || "").replace(/[\r\n\t]/g, " ").trim().slice(0, max);
}

function normalizeDeviceId(value) {
  const text = shortText(value, 80);
  return /^[a-zA-Z0-9._:-]{8,80}$/.test(text) ? text : "";
}

function normalizeDeviceToken(value) {
  const text = shortText(value, 128);
  return /^[a-zA-Z0-9._~:-]{16,128}$/.test(text) ? text : "";
}

function normalizeActivationCode(value) {
  const text = String(value || "").trim().toUpperCase().replace(/\s+/g, "");
  return /^[A-Z0-9-]{8,40}$/.test(text) ? text : "";
}

function normalizeOptionalIsoDate(value) {
  const text = shortText(value, 40);
  if (!text) {
    return "";
  }
  const date = new Date(text);
  return Number.isFinite(date.getTime()) ? date.toISOString() : "";
}

function isExpired(value) {
  if (!value) {
    return false;
  }
  const time = new Date(value).getTime();
  return Number.isFinite(time) && time <= Date.now();
}

function clampInt(value, min, max) {
  const number = Math.round(Number(value));
  if (!Number.isFinite(number)) return min;
  return Math.min(max, Math.max(min, number));
}

function clampNumber(value, min, max) {
  const number = Number(value);
  if (!Number.isFinite(number)) return min;
  return Math.min(max, Math.max(min, number));
}

function send(data, status) {
  return new Response(JSON.stringify(data), { status: status || 200, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
}

function sendHtml(html, status) {
  return new Response(html, { status: status || 200, headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
}

function adminPage() {
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>银杏 Pro 后台</title>
<style>
:root{color-scheme:light;--bg:#f4f6f8;--surface:#ffffff;--surface2:#f9fbfd;--ink:#17202e;--muted:#667386;--line:#d8e0ea;--accent:#2756d8;--accent2:#10a37f;--warn:#b7791f;--danger:#c9352b;--soft:#eef4ff;--greenSoft:#e8f7f1;--redSoft:#fff0ef;--shadow:0 18px 46px rgba(28,43,67,.12);--fast:160ms cubic-bezier(.2,.8,.2,1);--slow:520ms cubic-bezier(.16,1,.3,1)}
@media (prefers-color-scheme:dark){:root{color-scheme:dark;--bg:#0f141b;--surface:#151c25;--surface2:#111821;--ink:#eef4fb;--muted:#95a4b8;--line:#273445;--accent:#7ca5ff;--accent2:#54d4b0;--warn:#e8bb64;--danger:#ff776d;--soft:#172540;--greenSoft:#12342c;--redSoft:#3b1818;--shadow:0 18px 48px rgba(0,0,0,.3)}}
*{box-sizing:border-box}
body{margin:0;min-height:100vh;background:linear-gradient(180deg,rgba(39,86,216,.08),transparent 280px),radial-gradient(circle at 20% 0,rgba(16,163,127,.1),transparent 260px),var(--bg);color:var(--ink);font-family:"Microsoft YaHei UI","Microsoft YaHei","PingFang SC","Segoe UI",sans-serif}
body:before{content:"";position:fixed;inset:0;pointer-events:none;background-image:linear-gradient(rgba(23,32,46,.045) 1px,transparent 1px),linear-gradient(90deg,rgba(23,32,46,.045) 1px,transparent 1px);background-size:32px 32px;mask-image:linear-gradient(#000,transparent 70%)}
button,input,select{font:inherit}
button{border:0;border-radius:8px;padding:11px 14px;background:var(--ink);color:var(--surface);font-weight:800;cursor:pointer;transition:transform var(--fast),box-shadow var(--fast),background var(--fast),opacity var(--fast)}
button:hover{transform:translateY(-1px);box-shadow:0 10px 20px rgba(23,32,46,.16)}
button:active{transform:translateY(0) scale(.98)}
button:disabled{cursor:not-allowed;opacity:.48;box-shadow:none;transform:none}
button.secondary{background:var(--surface2);color:var(--ink);border:1px solid var(--line)}
button.primary{background:linear-gradient(135deg,var(--accent),#173a99)}
button.danger{background:var(--danger);color:white}
input,select{width:100%;border:1px solid var(--line);border-radius:8px;padding:11px 12px;background:var(--surface);color:var(--ink);outline:none;transition:border-color var(--fast),box-shadow var(--fast),background var(--fast)}
input:focus,select:focus{border-color:var(--accent);box-shadow:0 0 0 4px rgba(39,86,216,.14)}
main{position:relative;width:min(1240px,100%);margin:0 auto;padding:28px}
.shell{display:grid;gap:16px;animation:enter .65s var(--slow) both}
.topbar{display:flex;align-items:center;justify-content:space-between;gap:16px}
.brand{display:flex;align-items:center;gap:12px}
.mark{display:grid;place-items:center;width:42px;height:42px;border-radius:8px;background:linear-gradient(135deg,var(--ink),#334155);color:var(--surface);font-weight:900;letter-spacing:0}
h1{margin:0;font-size:28px;line-height:1.15;letter-spacing:0}
h2{margin:0;font-size:20px;letter-spacing:0}
h3{margin:0;font-size:15px;letter-spacing:0}
p{margin:7px 0 0;color:var(--muted);line-height:1.65}
.statusLine{display:flex;align-items:center;justify-content:flex-end;gap:10px;flex-wrap:wrap;color:var(--muted);font-size:13px}
.panel{background:rgba(255,255,255,.82);border:1px solid var(--line);border-radius:8px;box-shadow:var(--shadow);backdrop-filter:blur(18px)}
@media (prefers-color-scheme:dark){.panel{background:rgba(21,28,37,.82)}}
.hero{display:grid;grid-template-columns:1.1fr .9fr;gap:18px;padding:22px;overflow:hidden}
.heroCopy{display:flex;flex-direction:column;justify-content:space-between;gap:18px}
.heroCopy p{max-width:680px}
.authCard{display:grid;gap:10px;align-content:start;padding:14px;border:1px solid var(--line);border-radius:8px;background:var(--surface2)}
.authActions{display:grid;grid-template-columns:1fr auto auto;gap:10px}
.heroStats{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.miniStat{padding:13px;border:1px solid var(--line);border-radius:8px;background:var(--surface)}
.miniStat span,.metric span,.field label,.metaLabel{display:block;color:var(--muted);font-size:12px}
.miniStat b{display:block;margin-top:5px;font-size:22px}
.grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px}
.metric{position:relative;overflow:hidden;min-height:106px;padding:15px;border:1px solid var(--line);border-radius:8px;background:var(--surface);box-shadow:0 10px 24px rgba(28,43,67,.06);animation:rise .5s var(--slow) both}
.metric:nth-child(2){animation-delay:.04s}.metric:nth-child(3){animation-delay:.08s}.metric:nth-child(4){animation-delay:.12s}.metric:nth-child(5){animation-delay:.16s}.metric:nth-child(6){animation-delay:.2s}
.metric:after{content:"";position:absolute;right:-30px;top:-30px;width:92px;height:92px;border-radius:50%;background:color-mix(in srgb,var(--accent) 13%,transparent)}
.metric b{display:block;margin-top:8px;font-size:29px;line-height:1}
.metric em{display:block;margin-top:10px;color:var(--muted);font-style:normal;font-size:12px}
.section{padding:18px}
.sectionHead{display:flex;align-items:flex-start;justify-content:space-between;gap:14px;margin-bottom:14px}
.sectionTools{display:flex;align-items:center;justify-content:flex-end;gap:10px;flex-wrap:wrap}
.sectionTools input{width:min(280px,42vw)}
.formGrid{display:grid;grid-template-columns:1.2fr .7fr .8fr .8fr auto;gap:10px;align-items:end}
.field{display:grid;gap:6px}
.switches{display:flex;gap:8px;flex-wrap:wrap}
.check{display:inline-flex;align-items:center;gap:7px;min-height:42px;padding:9px 11px;border:1px solid var(--line);border-radius:8px;background:var(--surface);font-weight:800;color:var(--ink)}
.check input{width:auto;accent-color:var(--accent)}
.result{display:grid;grid-template-columns:1fr auto;gap:10px;align-items:center;margin-top:14px;padding:15px;border:1px solid color-mix(in srgb,var(--accent) 34%,var(--line));border-radius:8px;background:linear-gradient(135deg,var(--soft),var(--surface));animation:pop .42s var(--slow) both}
.resultCode{font-size:18px;font-weight:900;word-break:break-all}
.table{display:grid;gap:10px}
.row{display:grid;gap:12px;align-items:center;padding:14px;border:1px solid var(--line);border-radius:8px;background:var(--surface2);transition:transform var(--fast),border-color var(--fast),box-shadow var(--fast),background var(--fast);animation:rowIn .42s var(--slow) both}
.row:hover{transform:translateY(-2px);border-color:color-mix(in srgb,var(--accent) 45%,var(--line));box-shadow:0 14px 28px rgba(28,43,67,.1)}
.deviceRow{grid-template-columns:1.2fr .62fr .92fr .92fr .82fr auto}
.codeRow{grid-template-columns:1fr .7fr .8fr .9fr .9fr auto}
.titleCell{min-width:0}
.titleCell b{display:block;overflow-wrap:anywhere;font-size:15px}
.titleCell small{display:block;margin-top:4px;color:var(--muted);font-size:12px;overflow-wrap:anywhere}
.cell{min-width:0;overflow-wrap:anywhere}
.muted{color:var(--muted);font-size:13px}
.pill{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:28px;border-radius:999px;padding:5px 10px;background:var(--surface);border:1px solid var(--line);font-size:12px;font-weight:900;white-space:nowrap}
.ok{color:var(--accent2);background:var(--greenSoft);border-color:color-mix(in srgb,var(--accent2) 35%,var(--line))}
.bad{color:var(--danger);background:var(--redSoft);border-color:color-mix(in srgb,var(--danger) 35%,var(--line))}
.warn{color:var(--warn)}
.featureList{display:flex;gap:6px;flex-wrap:wrap;margin-top:7px}
.quota{display:grid;gap:6px}
.quotaTop{display:flex;align-items:center;justify-content:space-between;gap:8px;color:var(--muted);font-size:12px}
.bar{height:8px;border-radius:999px;background:var(--line);overflow:hidden}
.bar i{display:block;width:0;height:100%;border-radius:inherit;background:linear-gradient(90deg,var(--accent2),var(--accent));transition:width .75s var(--slow)}
.empty,.loading{display:grid;place-items:center;min-height:120px;border:1px dashed var(--line);border-radius:8px;background:var(--surface2);color:var(--muted);text-align:center}
.loading:before{content:"";width:30px;height:30px;border:3px solid var(--line);border-top-color:var(--accent);border-radius:50%;animation:spin .8s linear infinite}
.toast{position:fixed;right:22px;bottom:22px;z-index:20;display:grid;gap:8px;max-width:min(420px,calc(100vw - 44px))}
.toastItem{padding:12px 14px;border-radius:8px;background:var(--ink);color:var(--surface);box-shadow:var(--shadow);animation:toastIn .24s var(--slow) both}
.tabs{display:flex;gap:6px;flex-wrap:wrap}
.tab{border:1px solid var(--line);background:var(--surface2);color:var(--ink);padding:9px 11px}
.tab.active{background:var(--ink);color:var(--surface);border-color:var(--ink)}
.density{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}
.insight{padding:13px;border:1px solid var(--line);border-radius:8px;background:var(--surface)}
.insight b{display:block;margin-top:4px;font-size:18px}
.aiConfig{display:grid;grid-template-columns:1.1fr 1fr 1fr;gap:10px;align-items:end;margin:14px 0}
.aiButtons{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:14px}
.deviceCard{display:block;cursor:pointer}
.deviceMain{display:grid;grid-template-columns:1.15fr .76fr .9fr .9fr .9fr auto;gap:12px;align-items:center}
.deviceDetail{margin-top:13px;padding-top:13px;border-top:1px solid var(--line);animation:pop .26s var(--slow) both}
.detailGrid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
.detailItem{padding:12px;border:1px solid var(--line);border-radius:8px;background:var(--surface)}
.detailItem b{display:block;margin-top:4px;overflow-wrap:anywhere}
.inlineStats{display:flex;gap:6px;flex-wrap:wrap;margin-top:8px}
@keyframes enter{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:none}}
@keyframes rise{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:none}}
@keyframes rowIn{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
@keyframes pop{from{opacity:0;transform:scale(.98)}to{opacity:1;transform:scale(1)}}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes toastIn{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
@media (max-width:1040px){main{padding:20px}.hero{grid-template-columns:1fr}.grid{grid-template-columns:repeat(3,minmax(0,1fr))}.formGrid,.aiConfig{grid-template-columns:1fr 1fr}.formGrid button{grid-column:auto}.deviceRow,.codeRow,.deviceMain,.detailGrid{grid-template-columns:1fr 1fr}.sectionTools{justify-content:flex-start}}
@media (max-width:680px){main{padding:12px}.topbar{align-items:flex-start;flex-direction:column}.statusLine{justify-content:flex-start}.hero{padding:16px}.authActions,.formGrid,.aiConfig,.grid,.heroStats,.deviceRow,.codeRow,.deviceMain,.density,.detailGrid{grid-template-columns:1fr}.section{padding:14px}.sectionHead{flex-direction:column}.sectionTools input{width:100%}.result{grid-template-columns:1fr}.row{padding:12px}h1{font-size:24px}}
@media (prefers-reduced-motion:reduce){*,*:before,*:after{animation-duration:.01ms!important;animation-iteration-count:1!important;transition-duration:.01ms!important;scroll-behavior:auto!important}}
</style>
</head>
<body>
<main>
<div class="shell">
<div class="topbar">
<div class="brand">
<div class="mark">YX</div>
<div>
<h1>银杏 Pro 后台</h1>
<p>设备授权、激活码流转、AI 功能配额，一屏看清。</p>
</div>
</div>
<div class="statusLine">
<span id="connectionState" class="pill bad">未连接</span>
<span id="lastRefresh">尚未刷新</span>
</div>
</div>
<section class="hero panel">
<div class="heroCopy">
<div>
<h2>授权运营看板</h2>
<p>生成带备注、套餐和有效期的激活码，追踪设备可用状态、功能开关、剩余配额、兑换记录和最近更新时间。</p>
</div>
<div class="density">
<div class="insight"><span class="metaLabel">设备可用率</span><b id="activeRate">-</b></div>
<div class="insight"><span class="metaLabel">激活码兑换率</span><b id="redeemRate">-</b></div>
<div class="insight"><span class="metaLabel">AI 剩余额度</span><b id="quotaTotal">-</b></div>
</div>
</div>
<div class="authCard">
<h3>管理员密钥</h3>
<p>密钥只保存在当前浏览器，用于访问后台 API。</p>
<input id="token" type="password" autocomplete="current-password" placeholder="PRO_ADMIN_SECRET">
<div class="authActions">
<button id="saveToken" class="primary">保存并刷新</button>
<button id="toggleToken" class="secondary">显示</button>
<button id="clearToken" class="secondary">清除</button>
</div>
</div>
</section>
<div class="grid" aria-label="核心指标">
<div class="metric"><span>全部设备</span><b id="mDevices">-</b><em id="mDevicesHint">等待连接</em></div>
<div class="metric"><span>可用设备</span><b id="mActive">-</b><em id="mActiveHint">授权可用</em></div>
<div class="metric"><span>停用设备</span><b id="mInactive">-</b><em>已吊销或不可用</em></div>
<div class="metric"><span>激活码</span><b id="mCodes">-</b><em>累计生成</em></div>
<div class="metric"><span>未使用码</span><b id="mUsable">-</b><em>可继续兑换</em></div>
<div class="metric"><span>待清理码</span><b id="mRevokedCodes">-</b><em>已吊销未兑换</em></div>
</div>
<section class="section panel">
<div class="sectionHead">
<div>
<h2>AI 网关</h2>
<p>OpenAI-compatible 地址、API Key、模型、余额和本月调用用量。</p>
</div>
<div class="sectionTools"><button class="secondary" id="refreshAi">刷新 AI</button></div>
</div>
<div class="aiConfig">
<div class="field"><label for="aiBaseUrl">v1 地址</label><input id="aiBaseUrl" placeholder="https://api.example.com/v1"></div>
<div class="field"><label for="aiApiKey">API Key</label><input id="aiApiKey" type="password" autocomplete="off" placeholder="留空保留当前密钥"></div>
<div class="field"><label for="aiModelInput">模型</label><input id="aiModelInput" list="modelList" placeholder="选择或输入模型"><datalist id="modelList"></datalist></div>
</div>
<div class="aiButtons">
<button class="primary" id="saveAiConfig">保存配置</button>
<button class="secondary" id="loadModels">拉取模型</button>
<button class="secondary" id="testAi">测试连接</button>
<button class="secondary" id="toggleAiKey">显示 Key</button>
</div>
<div class="density">
<div class="insight"><span class="metaLabel">API Key</span><b id="aiKeyState">-</b></div>
<div class="insight"><span class="metaLabel">当前模型</span><b id="aiModel">-</b></div>
<div class="insight"><span class="metaLabel">余额状态</span><b id="aiBalanceState">-</b></div>
<div class="insight"><span class="metaLabel">本月请求</span><b id="aiRequests">-</b></div>
<div class="insight"><span class="metaLabel">本月 tokens</span><b id="aiTokens">-</b></div>
<div class="insight"><span class="metaLabel">连接测试</span><b id="aiTestState">未测试</b></div>
</div>
<div id="aiBalance" class="table"></div>
</section>
<section class="section panel">
<div class="sectionHead">
<div>
<h2>生成激活码</h2>
<p>建议写清用途或人员，后续排查会省很多时间。</p>
</div>
</div>
<div class="formGrid">
<div class="field"><label for="note">备注</label><input id="note" placeholder="例如：给家里备用机"></div>
<div class="field"><label for="plan">套餐</label><select id="plan"><option value="month">月套餐</option><option value="year">年套餐</option><option value="lifetime">长期套餐</option></select></div>
<div class="field"><label for="codeExpires">激活码过期</label><input id="codeExpires" type="datetime-local"></div>
<button id="createCode" class="primary">生成并复制</button>
</div>
<div class="switches" aria-label="功能开关">
<label class="check"><input id="featureCall" type="checkbox" checked>来电风险</label>
<label class="check"><input id="featureWechat" type="checkbox" checked>微信视频辅助</label>
</div>
<div id="newCode" class="result" hidden></div>
</section>
<section class="section panel">
<div class="sectionHead">
<div>
<h2>设备</h2>
<p>展示设备身份、备注、状态、功能开关、过期时间和本月剩余配额。</p>
</div>
<div class="sectionTools">
<input id="deviceSearch" placeholder="搜索设备、备注、套餐">
<div class="tabs" id="deviceFilters">
<button class="tab active" data-filter="all">全部</button>
<button class="tab" data-filter="active">可用</button>
<button class="tab" data-filter="inactive">停用</button>
</div>
<button class="secondary" id="refreshDevices">刷新</button>
</div>
</div>
<div id="devices" class="table"></div>
</section>
<section class="section panel">
<div class="sectionHead">
<div>
<h2>激活码记录</h2>
<p>历史记录只保留哈希前缀，未保存完整码时需要输入完整激活码才能吊销。</p>
</div>
<div class="sectionTools">
<input id="codeSearch" placeholder="搜索哈希、备注、设备">
<div class="tabs" id="codeFilters">
<button class="tab active" data-filter="all">全部</button>
<button class="tab" data-filter="usable">可用</button>
<button class="tab" data-filter="redeemed">已兑换</button>
<button class="tab" data-filter="revoked">已吊销</button>
<button class="tab" data-filter="inactive">不可用</button>
</div>
<button class="secondary" id="cleanupCodes">清理吊销码</button>
<button class="secondary" id="refreshCodes">刷新</button>
</div>
</div>
<div id="codes" class="table"></div>
</section>
</div>
</main>
<div id="toast" class="toast"></div>
<script>
const tokenInput=document.getElementById("token");
const saved=localStorage.getItem("oldlauncher_admin_token")||"";
const state={devices:[],codes:[],ai:null,models:[],aiTest:null,openDevice:"",deviceFilter:"all",codeFilter:"all",loading:false};
tokenInput.value=saved;
document.getElementById("saveToken").onclick=()=>{localStorage.setItem("oldlauncher_admin_token",tokenInput.value.trim());toast("密钥已保存");loadAll()};
document.getElementById("clearToken").onclick=()=>{localStorage.removeItem("oldlauncher_admin_token");tokenInput.value="";state.devices=[];state.codes=[];state.ai=null;state.models=[];state.aiTest=null;renderAll();setConnection(false,"密钥已清除")};
document.getElementById("toggleToken").onclick=()=>{const visible=tokenInput.type==="text";tokenInput.type=visible?"password":"text";document.getElementById("toggleToken").textContent=visible?"显示":"隐藏"};
function token(){return tokenInput.value.trim()}
async function api(path,options){
  const init=options||{};
  const currentToken=token();
  if(!currentToken)throw new Error("请先保存密钥");
  init.headers=Object.assign({"x-oldlauncher-admin-token":currentToken,"content-type":"application/json"},init.headers||{});
  const res=await fetch(path,init);
  const body=await res.json().catch(()=>({}));
  if(!res.ok||body.available===false)throw new Error(body.error||("HTTP "+res.status));
  return body;
}
function $(id){return document.getElementById(id)}
function text(value){return value||value===0?String(value):"-"}
function html(value){return text(value).replace(/[&<>"']/g,(char)=>({"&":"&amp;","<":"&lt;",">":"&gt;","\\"":"&quot;","'":"&#39;"}[char]))}
function pill(kind,label){return "<span class='pill "+kind+"'>"+html(label)+"</span>"}
function readyDevice(device){return Boolean(device.active&&!device.revoked&&!expired(device.expires_at))}
function readyCode(code){return Boolean(code.active&&!code.redeemed&&!expired(code.expires_at))}
function expired(value){const time=Date.parse(value||"");return Number.isFinite(time)&&time<=Date.now()}
function rate(part,total){return total?Math.round(part*100/total)+"%":"-"}
function dateText(value){if(!value)return "长期有效";const date=new Date(value);return Number.isFinite(date.getTime())?date.toLocaleString():"-"}
function compactDate(value){if(!value)return "-";const date=new Date(value);return Number.isFinite(date.getTime())?date.toLocaleDateString():"-"}
function isoFromLocal(value){if(!value)return "";const date=new Date(value);return Number.isFinite(date.getTime())?date.toISOString():""}
function numberText(value){return Number(value||0).toLocaleString()}
function quotaValue(device,key){const item=device.quotas&&device.quotas[key];return item?item:{limit:0,used:0,remaining:0,enabled:false}}
function quotaHtml(device,key,label){
  const item=quotaValue(device,key);
  const percent=item.limit?Math.round(item.remaining*100/item.limit):0;
  return "<div class='quota'><div class='quotaTop'><span>"+label+"</span><b>"+item.remaining+"/"+item.limit+"</b></div><div class='bar'><i style='width:"+percent+"%'></i></div></div>";
}
function featureHtml(features){
  const enabled=features||{};
  const items=[];
  items.push(pill(enabled["call-risk"]!==false?"ok":"bad","来电风险"));
  items.push(pill(enabled["wechat-step"]!==false?"ok":"bad","视频辅助"));
  return "<div class='featureList'>"+items.join("")+"</div>";
}
function deviceName(device){return device.device_name||device.note||"未命名设备"}
function planState(device){
  if(device.revoked||!device.active)return "已停用";
  if(!device.expires_at)return "长期有效";
  const time=Date.parse(device.expires_at);
  if(!Number.isFinite(time))return "-";
  const days=Math.ceil((time-Date.now())/86400000);
  return days>0?"剩余 "+days+" 天":"已过期";
}
function totalCalls(device){return quotaValue(device,"call-risk").used+quotaValue(device,"wechat-step").used}
function featureName(value){return value==="wechat-step"?"微信视频辅助":value==="call-risk"?"来电风险":value||"-"}
function setConnection(ok,label){
  $("connectionState").className="pill "+(ok?"ok":"bad");
  $("connectionState").textContent=ok?"已连接":"未连接";
  $("lastRefresh").textContent=label||"尚未刷新";
}
function toast(message){
  const item=document.createElement("div");
  item.className="toastItem";
  item.textContent=message;
  $("toast").appendChild(item);
  setTimeout(()=>{item.style.opacity="0";item.style.transform="translateY(8px)";setTimeout(()=>item.remove(),180)},2600);
}
function setLoading(target){
  $(target).innerHTML="<div class='loading'></div>";
}
function filteredDevices(){
  const q=$("deviceSearch").value.trim().toLowerCase();
  return state.devices.filter((device)=>{
    const ready=readyDevice(device);
    const inFilter=state.deviceFilter==="all"||state.deviceFilter==="active"&&ready||state.deviceFilter==="inactive"&&!ready;
    const hay=[device.device_id,device.device_name,device.note,device.plan,device.expires_at,device.last_active_at].join(" ").toLowerCase();
    return inFilter&&(!q||hay.includes(q));
  });
}
function filteredCodes(){
  const q=$("codeSearch").value.trim().toLowerCase();
  return state.codes.filter((code)=>{
    const ready=readyCode(code);
    const inFilter=state.codeFilter==="all"||state.codeFilter==="usable"&&ready||state.codeFilter==="redeemed"&&code.redeemed||state.codeFilter==="revoked"&&code.revoked||state.codeFilter==="inactive"&&!ready&&!code.redeemed;
    const hay=[code.code_hash,code.note,code.plan,code.redeemed_device_id,code.expires_at,code.device_expires_at].join(" ").toLowerCase();
    return inFilter&&(!q||hay.includes(q));
  });
}
function renderSummary(summary){
  const devices=state.devices;
  const codes=state.codes;
  const active=devices.filter(readyDevice).length;
  const revoked=devices.filter((device)=>device.revoked||!device.active||expired(device.expires_at)).length;
  const usable=codes.filter(readyCode).length;
  const redeemed=codes.filter((code)=>code.redeemed).length;
  const revokedCodes=codes.filter((code)=>code.revoked).length;
  const quota=devices.reduce((sum,device)=>sum+quotaValue(device,"call-risk").remaining+quotaValue(device,"wechat-step").remaining,0);
  $("mDevices").textContent=summary?summary.devices:devices.length;
  $("mActive").textContent=summary?summary.active_devices:active;
  $("mInactive").textContent=revoked;
  $("mCodes").textContent=summary?summary.codes:codes.length;
  $("mUsable").textContent=summary?summary.usable_codes:usable;
  $("mRevokedCodes").textContent=summary?summary.revoked_codes:revokedCodes;
  $("mDevicesHint").textContent=devices.length?devices.length+" 台设备入库":"等待设备数据";
  $("mActiveHint").textContent=active+" 台当前可用";
  $("activeRate").textContent=rate(active,devices.length);
  $("redeemRate").textContent=rate(redeemed,codes.length);
  $("quotaTotal").textContent=quota?String(quota):"-";
}
function renderAi(){
  const data=state.ai;
  const usage=data&&data.usage?data.usage:{totals:{requests:0,total_tokens:0,cache_hit_tokens:0,prompt_tokens:0,completion_tokens:0},features:{}};
  const balance=data&&data.balance?data.balance:{available:false,balances:[]};
  if(data){
    if(document.activeElement!==$("aiBaseUrl"))$("aiBaseUrl").value=data.base_url||"";
    if(document.activeElement!==$("aiModelInput"))$("aiModelInput").value=data.model||"";
  }
  $("aiKeyState").textContent=data&&data.key_configured?(data.key_source==="saved"?"已配置 "+(data.key_preview||""):"已配置 环境变量"):"未配置";
  $("aiModel").textContent=data&&data.model?data.model:"-";
  $("aiBalanceState").textContent=balance.available?"可用":"无法查询";
  $("aiRequests").textContent=numberText(usage.totals.requests);
  $("aiTokens").textContent=numberText(usage.totals.total_tokens);
  $("aiTestState").textContent=state.aiTest?state.aiTest.available?"正常":"失败":"未测试";
  renderModels();
  const box=$("aiBalance");
  box.innerHTML="";
  if(balance.balances&&balance.balances.length){
    balance.balances.forEach((item)=>{
      const row=document.createElement("div");
      row.className="row codeRow";
      row.innerHTML="<div class='titleCell'><b>"+html(item.currency)+"</b><small>DeepSeek 余额</small></div><div class='cell'><span class='muted'>总余额</span><br>"+html(item.total_balance)+"</div><div class='cell'><span class='muted'>赠送</span><br>"+html(item.granted_balance)+"</div><div class='cell'><span class='muted'>充值</span><br>"+html(item.topped_up_balance)+"</div>";
      box.appendChild(row);
    });
  }else{
    const reason=balance.error==="balance_unsupported"?"当前供应商没有统一余额接口，本月用量仍会按网关请求记录。":"余额接口没有返回可展示数据，本月用量仍会按网关请求记录。";
    box.innerHTML="<div class='empty'>"+html(reason)+"</div>";
  }
}
function renderModels(){
  const list=$("modelList");
  list.innerHTML="";
  state.models.forEach((model)=>{
    const option=document.createElement("option");
    option.value=model.id;
    option.label=model.owned_by?model.id+" · "+model.owned_by:model.id;
    list.appendChild(option);
  });
}
function renderDevices(){
  const box=$("devices");
  const data=filteredDevices();
  box.innerHTML="";
  if(!state.devices.length){box.innerHTML="<div class='empty'>暂无设备，兑换激活码后会出现在这里。</div>";return}
  if(!data.length){box.innerHTML="<div class='empty'>没有匹配的设备。</div>";return}
  data.forEach((device,index)=>{
    const row=document.createElement("div");
    const ready=readyDevice(device);
    const opened=state.openDevice===device.device_id;
    const calls=totalCalls(device);
    row.className="row deviceCard";
    row.style.animationDelay=Math.min(index*32,260)+"ms";
    row.innerHTML="<div class='deviceMain'><div class='titleCell'><b>"+html(deviceName(device))+"</b><small>"+html(device.device_id)+"</small>"+featureHtml(device.features)+"</div><div class='cell'>"+pill(ready?"ok":"bad",ready?"可用":"停用")+"<div class='inlineStats'>"+pill(ready?"ok":"bad",device.plan||"月套餐")+" "+pill(ready?"ok":"bad",planState(device))+"</div></div><div class='cell'>"+quotaHtml(device,"call-risk","来电")+"</div><div class='cell'>"+quotaHtml(device,"wechat-step","视频")+"</div><div class='cell'><span class='muted'>最后活跃</span><br>"+html(dateText(device.last_active_at||device.updated_at))+"<br><span class='muted'>调用</span> "+numberText(calls)+" 次</div></div>";
    const action=document.createElement("button");
    action.className="danger";
    action.textContent="吊销";
    action.disabled=!ready;
    action.onclick=async()=>{if(confirm("确定吊销这个设备？")){await revokeDevice(device.device_id)}};
    row.querySelector(".deviceMain").appendChild(action);
    if(opened){
      const detail=document.createElement("div");
      detail.className="deviceDetail";
      detail.innerHTML="<div class='detailGrid'><div class='detailItem'><span class='metaLabel'>设备名</span><b>"+html(deviceName(device))+"</b></div><div class='detailItem'><span class='metaLabel'>设备 ID</span><b>"+html(device.device_id)+"</b></div><div class='detailItem'><span class='metaLabel'>套餐</span><b>"+html(device.plan||"-")+"</b></div><div class='detailItem'><span class='metaLabel'>套餐状态</span><b>"+html(planState(device))+"</b></div><div class='detailItem'><span class='metaLabel'>来电额度</span><b>"+quotaValue(device,"call-risk").remaining+"/"+quotaValue(device,"call-risk").limit+"</b></div><div class='detailItem'><span class='metaLabel'>视频额度</span><b>"+quotaValue(device,"wechat-step").remaining+"/"+quotaValue(device,"wechat-step").limit+"</b></div><div class='detailItem'><span class='metaLabel'>最后活跃</span><b>"+html(dateText(device.last_active_at||device.updated_at))+"</b></div><div class='detailItem'><span class='metaLabel'>最近功能</span><b>"+html(featureName(device.last_feature))+"</b></div><div class='detailItem'><span class='metaLabel'>创建时间</span><b>"+html(dateText(device.activated_at))+"</b></div><div class='detailItem'><span class='metaLabel'>到期时间</span><b>"+html(dateText(device.expires_at))+"</b></div><div class='detailItem'><span class='metaLabel'>本月调用</span><b>"+numberText(calls)+" 次</b></div><div class='detailItem'><span class='metaLabel'>备注</span><b>"+html(device.note||"-")+"</b></div></div>";
      row.appendChild(detail);
    }
    row.onclick=(event)=>{if(event.target.closest("button"))return;state.openDevice=opened?"":device.device_id;renderDevices()};
    box.appendChild(row);
  });
}
function renderCodes(){
  const box=$("codes");
  const data=filteredCodes();
  box.innerHTML="";
  if(!state.codes.length){box.innerHTML="<div class='empty'>暂无激活码，生成后会显示在这里。</div>";return}
  if(!data.length){box.innerHTML="<div class='empty'>没有匹配的激活码。</div>";return}
  data.forEach((code,index)=>{
    const row=document.createElement("div");
    const ready=readyCode(code);
    const status=code.redeemed?pill("ok","已兑换"):code.revoked?pill("bad","已吊销"):pill(ready?"ok":"bad",ready?"可用":"不可用");
    row.className="row codeRow";
    row.style.animationDelay=Math.min(index*32,260)+"ms";
    row.innerHTML="<div class='titleCell'><b>"+html(code.code_hash)+"</b><small>"+html(code.note||"无备注")+" · "+html(code.plan||"月套餐")+"</small></div><div class='cell'>"+status+"</div><div class='cell'><span class='muted'>创建</span><br>"+html(dateText(code.created_at))+"</div><div class='cell'><span class='muted'>兑换</span><br>"+html(code.redeemed_at?dateText(code.redeemed_at):"-")+"<br><span class='muted'>绑定</span> "+html(code.redeemed_device_id||"-")+"</div><div class='cell'><span class='muted'>码过期</span><br>"+html(dateText(code.expires_at))+"<br><span class='muted'>设备到期</span> "+html(compactDate(code.device_expires_at))+"</div>";
    const action=document.createElement("button");
    action.className="danger";
    action.textContent="吊销";
    action.disabled=!ready;
    action.onclick=async()=>{const raw=prompt("请输入完整激活码");if(raw){await revokeCode(raw)}};
    row.appendChild(action);
    box.appendChild(row);
  });
}
function renderAll(summary){
  renderSummary(summary);
  renderAi();
  renderDevices();
  renderCodes();
}
async function loadAi(){
  state.ai=await api("/admin/api/ai");
  renderAi();
}
async function loadModels(){
  const data=await api("/admin/api/ai/models");
  state.models=data.models||[];
  renderModels();
  toast("已拉取 "+state.models.length+" 个模型");
}
async function saveAiConfig(){
  const body={
    base_url:$("aiBaseUrl").value.trim(),
    model:$("aiModelInput").value.trim()
  };
  const key=$("aiApiKey").value.trim();
  if(key)body.api_key=key;
  state.ai=await api("/admin/api/ai/config",{method:"POST",body:JSON.stringify(body)});
  $("aiApiKey").value="";
  renderAi();
  toast("AI 配置已保存");
}
async function testAi(){
  state.aiTest=null;
  renderAi();
  const body={
    base_url:$("aiBaseUrl").value.trim(),
    model:$("aiModelInput").value.trim()
  };
  const key=$("aiApiKey").value.trim();
  if(key)body.api_key=key;
  state.aiTest=await api("/admin/api/ai/test",{method:"POST",body:JSON.stringify(body)});
  renderAi();
  toast("连接正常，模型 "+state.aiTest.tested_model);
}
async function loadDevices(){
  const data=await api("/admin/api/devices");
  state.devices=data.devices||[];
  renderSummary();
  renderDevices();
}
async function loadCodes(){
  const data=await api("/admin/api/codes");
  state.codes=data.codes||[];
  renderSummary();
  renderCodes();
}
async function loadAll(){
  if(state.loading)return;
  try{
    state.loading=true;
    setLoading("devices");
    setLoading("codes");
    const data=await Promise.all([api("/admin/api/summary"),api("/admin/api/devices"),api("/admin/api/codes"),api("/admin/api/ai")]);
    state.devices=data[1].devices||[];
    state.codes=data[2].codes||[];
    state.ai=data[3]||null;
    renderAll(data[0]);
    setConnection(true,"刚刚刷新");
  }catch(error){
    setConnection(false,error.message);
    toast(error.message);
    renderAll();
  }finally{
    state.loading=false;
  }
}
async function revokeDevice(deviceId){
  try{
    await api("/admin/api/device/revoke",{method:"POST",body:JSON.stringify({device_id:deviceId})});
    toast("设备已吊销");
    loadAll();
  }catch(error){toast(error.message)}
}
async function revokeCode(code){
  try{
    await api("/admin/api/code/revoke",{method:"POST",body:JSON.stringify({activation_code:code})});
    toast("激活码已吊销");
    loadAll();
  }catch(error){toast(error.message)}
}
async function cleanupCodes(){
  try{
    if(!confirm("确定清理所有已吊销且未兑换的激活码？"))return;
    const data=await api("/admin/api/codes/cleanup",{method:"POST",body:"{}"});
    toast("已清理 "+data.deleted+" 个吊销码");
    loadAll();
  }catch(error){toast(error.message)}
}
async function copyText(value){
  try{await navigator.clipboard.writeText(value);toast("已复制到剪贴板")}catch(error){toast("已生成，请手动复制")}
}
$("createCode").onclick=async()=>{
  try{
    $("createCode").disabled=true;
    const body={
      note:$("note").value.trim(),
      plan:$("plan").value.trim(),
      expires_at:isoFromLocal($("codeExpires").value),
      features:{"call-risk":$("featureCall").checked,"wechat-step":$("featureWechat").checked}
    };
    const data=await api("/admin/api/codes",{method:"POST",body:JSON.stringify(body)});
    const box=$("newCode");
    box.hidden=false;
    box.innerHTML="<div><span class='metaLabel'>新激活码</span><div class='resultCode'>"+html(data.activation_code)+"</div><p>套餐 "+html(data.plan)+" · 码过期 "+html(dateText(data.expires_at))+" · 设备过期 "+html(dateText(data.device_expires_at))+"</p></div>";
    const copy=document.createElement("button");
    copy.className="secondary";
    copy.textContent="复制";
    copy.onclick=()=>copyText(data.activation_code);
    box.appendChild(copy);
    await copyText(data.activation_code);
    loadAll();
  }catch(error){toast(error.message)}finally{$("createCode").disabled=false}
};
$("refreshDevices").onclick=async()=>{try{setLoading("devices");await loadDevices();toast("设备已刷新")}catch(error){toast(error.message)}};
$("refreshCodes").onclick=async()=>{try{setLoading("codes");await loadCodes();toast("激活码已刷新")}catch(error){toast(error.message)}};
$("refreshAi").onclick=async()=>{try{state.ai=null;renderAi();await loadAi();toast("AI 状态已刷新")}catch(error){toast(error.message)}};
$("loadModels").onclick=async()=>{try{await loadModels()}catch(error){toast(error.message)}};
$("saveAiConfig").onclick=async()=>{try{$("saveAiConfig").disabled=true;await saveAiConfig()}catch(error){toast(error.message)}finally{$("saveAiConfig").disabled=false}};
$("testAi").onclick=async()=>{try{$("testAi").disabled=true;await testAi()}catch(error){state.aiTest={available:false};renderAi();toast(error.message)}finally{$("testAi").disabled=false}};
$("toggleAiKey").onclick=()=>{const input=$("aiApiKey");const visible=input.type==="text";input.type=visible?"password":"text";$("toggleAiKey").textContent=visible?"显示 Key":"隐藏 Key"};
$("cleanupCodes").onclick=cleanupCodes;
$("deviceSearch").oninput=renderDevices;
$("codeSearch").oninput=renderCodes;
document.querySelectorAll("#deviceFilters .tab").forEach((button)=>button.onclick=()=>{state.deviceFilter=button.dataset.filter;document.querySelectorAll("#deviceFilters .tab").forEach((item)=>item.classList.toggle("active",item===button));renderDevices()});
document.querySelectorAll("#codeFilters .tab").forEach((button)=>button.onclick=()=>{state.codeFilter=button.dataset.filter;document.querySelectorAll("#codeFilters .tab").forEach((item)=>item.classList.toggle("active",item===button));renderCodes()});
renderAll();
if(saved){setConnection(false,"正在连接");loadAll()}
</script>
</body>
</html>`;
}

function bearerToken(value) {
  const text = String(value || "").trim();
  return text.toLowerCase().startsWith("bearer ") ? text.slice(7).trim() : "";
}

function constantTimeEqual(a, b) {
  const left = String(a || "");
  const right = String(b || "");
  let diff = left.length ^ right.length;
  const max = Math.max(left.length, right.length);
  for (let i = 0; i < max; i++) {
    diff |= left.charCodeAt(i % Math.max(1, left.length)) ^ right.charCodeAt(i % Math.max(1, right.length));
  }
  return diff === 0;
}

async function sha256Hex(value) {
  const bytes = new TextEncoder().encode(String(value));
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function hashToken(deviceId, token) {
  return sha256Hex(`${deviceId}:${token}`);
}

async function activationCodeHash(code) {
  return sha256Hex(`activation:${normalizeActivationCode(code)}`);
}

async function activationCodeKey(code) {
  return `activation:${await activationCodeHash(code)}`;
}

function createActivationCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  const text = Array.from(bytes, (byte) => alphabet[byte % alphabet.length]).join("");
  return `YX-${text.slice(0, 4)}-${text.slice(4, 8)}-${text.slice(8, 12)}-${text.slice(12, 16)}`;
}

function createToken() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function monthKey() {
  return new Date().toISOString().slice(0, 7).replace("-", "");
}

function minuteKey() {
  return new Date().toISOString().slice(0, 16).replace(/[-:T]/g, "");
}
