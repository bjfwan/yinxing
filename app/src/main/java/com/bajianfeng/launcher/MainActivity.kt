package com.bajianfeng.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 设置返回键拦截
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@MainActivity, "已经是桌面了", Toast.LENGTH_SHORT).show()
            }
        })

        // 2. 初始化点击事件
        initClicks()
    }

    private fun initClicks() {
        // 电话
        findViewById<CardView>(R.id.card_phone).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开电话", Toast.LENGTH_SHORT).show()
            }
        }

        // 微信 (com.tencent.mm)
        findViewById<CardView>(R.id.card_wechat).setOnClickListener {
            launchApp("com.tencent.mm", "微信")
        }

        // 抖音 (com.ss.android.ugc.aweme)
        findViewById<CardView>(R.id.card_douyin).setOnClickListener {
            launchApp("com.ss.android.ugc.aweme", "抖音")
        }

        // 设置
        findViewById<CardView>(R.id.card_settings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }

        // 添加应用
        findViewById<CardView>(R.id.card_add).setOnClickListener {
            // TODO: 跳转到应用选择界面
            Toast.makeText(this, "添加应用功能开发中...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchApp(packageName: String, appName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "未安装$appName", Toast.LENGTH_SHORT).show()
        }
    }
}
