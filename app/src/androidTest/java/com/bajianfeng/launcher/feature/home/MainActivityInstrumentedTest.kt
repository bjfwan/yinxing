package com.bajianfeng.launcher.feature.home

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.testutil.InstrumentationTestEnvironment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Before
    fun setUp() {
        InstrumentationTestEnvironment.resetAppState()
    }

    @Test
    fun launchShowsBuiltInHomeItemsAndClock() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationTestEnvironment.waitUntil(
                scenario = scenario,
                message = "主页未加载出内置入口"
            ) { activity ->
                activity.findViewById<RecyclerView>(R.id.recycler_home).adapter?.itemCount == 5
            }

            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<RecyclerView>(R.id.recycler_home)
                val timeText = activity.findViewById<android.widget.TextView>(R.id.tv_time).text
                val dateText = activity.findViewById<android.widget.TextView>(R.id.tv_date).text
                org.junit.Assert.assertEquals(5, recyclerView.adapter?.itemCount)
                org.junit.Assert.assertTrue(timeText.isNotBlank())
                org.junit.Assert.assertTrue(dateText.isNotBlank())
            }
        }
    }
}
