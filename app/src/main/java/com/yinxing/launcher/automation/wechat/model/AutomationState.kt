package com.bajianfeng.launcher.automation.wechat.model

enum class AutomationState {
    IDLE,
    LAUNCHING_WECHAT,
    WAITING_HOME,
    WAITING_LAUNCHER_UI,
    WAITING_SEARCH,
    WAITING_CONTACT_RESULT,
    WAITING_CONTACT_DETAIL,
    WAITING_VIDEO_OPTIONS,
    RECOVERING,
    COMPLETED,
    FAILED
}

