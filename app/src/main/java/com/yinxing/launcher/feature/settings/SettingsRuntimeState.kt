package com.yinxing.launcher.feature.settings

import com.yinxing.launcher.feature.incoming.IncomingGuardReadiness
import kotlinx.coroutines.Job

internal class SettingsRuntimeState {
    var incomingGuardReadiness: IncomingGuardReadiness = IncomingGuardReadiness(emptyList())
    var permissionEntryStates: Map<PermissionEntry, PermissionEntryState> = emptyMap()
    var contactsSummaryJob: Job? = null
    var continueDefaultPhoneAfterNotification = false
}
