package com.bajianfeng.launcher.feature.phone

import android.graphics.Bitmap

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photo: Bitmap?
)
