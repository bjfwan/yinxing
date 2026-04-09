package com.bajianfeng.launcher.data.contact

import android.graphics.Bitmap

data class PhoneContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photo: Bitmap?
)
