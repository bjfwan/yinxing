package com.bajianfeng.launcher.model

enum class AutomationState {
    IDLE,
    CHECKING_PRECONDITIONS,
    LAUNCHING_WECHAT,
    WAITING_HOME,
    OPENING_SEARCH,
    SEARCHING_CONTACT,
    OPENING_CHAT,
    STARTING_VIDEO_CALL,
    SUCCESS,
    FAILED
}
