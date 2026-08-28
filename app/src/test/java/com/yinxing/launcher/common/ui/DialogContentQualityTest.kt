package com.yinxing.launcher.common.ui

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class DialogContentQualityTest {
    private val appRoot: Path = sequenceOf(
        Path.of(System.getProperty("user.dir")),
        Path.of(System.getProperty("user.dir"), "app")
    ).first { Files.isDirectory(it.resolve("src/main")) }

    private val layoutRoot = appRoot.resolve("src/main/res/layout")
    private val valuesRoot = appRoot.resolve("src/main/res/values")

    @Test
    fun `dialog titles keep shared typography and can wrap at large font sizes`() {
        val violations = DIALOG_LAYOUTS.flatMap { layoutName ->
            elements(layoutName)
                .filter { it.getAttribute("style") == DIALOG_TITLE_STYLE }
                .filter { it.androidAttribute("maxLines") == "1" || it.androidAttribute("textSize").isNotEmpty() }
                .map { "$layoutName#${it.androidAttribute("id")}" }
        }

        assertTrue("Dialog titles must inherit shared typography and allow wrapping: $violations", violations.isEmpty())
    }

    @Test
    fun `form and stepper controls use the elder friendly control height`() {
        CONTROL_IDS.forEach { (layoutName, ids) ->
            val elements = elements(layoutName)
            ids.forEach { id ->
                val control = elements.single { it.androidAttribute("id") == "@+id/$id" }
                assertEquals(
                    "$layoutName#$id must use the shared 56dp control height",
                    DIALOG_CONTROL_HEIGHT,
                    control.androidAttribute("layout_height")
                )
            }
        }
    }

    @Test
    fun `choice dialogs use the shared title and message hierarchy`() {
        val choiceLayout = layoutRoot.resolve("dialog_phone_layout_choice.xml").toFile().readText()

        assertTrue(DIALOG_TITLE_STYLE in choiceLayout)
        assertTrue(DIALOG_MESSAGE_STYLE in choiceLayout)
    }

    @Test
    fun `version update status is allowed to wrap`() {
        val status = elements("dialog_version_details.xml")
            .single { it.androidAttribute("id") == "@+id/tv_version_update_status" }

        assertNotEquals("Version status must not be clipped to one line", "1", status.androidAttribute("maxLines"))
    }

    @Test
    fun `dialog list content avoids tiny fixed text`() {
        val violations = DIALOG_ITEM_LAYOUTS.flatMap { layoutName ->
            elements(layoutName)
                .filter { it.androidAttribute("textSize") in TINY_TEXT_SIZES }
                .map { "$layoutName#${it.androidAttribute("id")}" }
        }

        assertTrue("Dialog list text must remain readable for elderly users: $violations", violations.isEmpty())
    }

    @Test
    fun `dialog typography remains readable without overwhelming large font layouts`() {
        assertEquals("22sp", styleItem("TextAppearance.OldLauncher.DialogTitle", "android:textSize"))
        assertEquals("16sp", styleItem("TextAppearance.OldLauncher.DialogMessage", "android:textSize"))
        assertEquals("15sp", styleItem("TextAppearance.OldLauncher.DialogSectionLabel", "android:textSize"))
        assertEquals("14sp", styleItem("TextAppearance.OldLauncher.DialogSupporting", "android:textSize"))
        assertEquals("16sp", styleItem("TextAppearance.OldLauncher.DialogActionPrimary", "android:textSize"))
        assertEquals("16sp", styleItem("TextAppearance.OldLauncher.DialogActionSecondary", "android:textSize"))
        assertEquals("16sp", styleItem("TextAppearance.OldLauncher.DialogInput", "android:textSize"))
        assertEquals("14sp", styleItem("TextAppearance.OldLauncher.DialogBadge", "android:textSize"))
    }

    @Test
    fun `dialog actions use compact dialog typography instead of page button typography`() {
        val violations = DIALOG_LAYOUTS.filter { layoutName ->
            val source = layoutRoot.resolve(layoutName).toFile().readText()
            LEGACY_PAGE_BUTTON_STYLES.any { it in source }
        }

        assertTrue("Dialog actions must use dialog-specific typography: $violations", violations.isEmpty())
    }

    @Test
    fun `avatar action stays short enough for a half width large font button`() {
        val strings = parse(valuesRoot.resolve("strings_contacts.xml"))
        val action = strings.getElementsByTagName("string")
            .let { nodes -> (0 until nodes.length).mapNotNull { nodes.item(it) as? Element } }
            .single { it.getAttribute("name") == "action_edit_avatar" }

        assertEquals("选择头像", action.textContent)
    }

    @Test
    fun `version subtitle stays concise at elderly font scale`() {
        val strings = parse(valuesRoot.resolve("strings_settings.xml"))
        val subtitle = strings.getElementsByTagName("string")
            .let { nodes -> (0 until nodes.length).mapNotNull { nodes.item(it) as? Element } }
            .single { it.getAttribute("name") == "settings_about_subtitle" }

        assertEquals("个人免费，专为长辈设计", subtitle.textContent)
    }

    @Test
    fun `report privacy note stays concise at elderly font scale`() {
        val strings = parse(valuesRoot.resolve("strings_settings.xml"))
        val message = strings.getElementsByTagName("string")
            .let { nodes -> (0 until nodes.length).mapNotNull { nodes.item(it) as? Element } }
            .single { it.getAttribute("name") == "settings_user_report_message" }

        assertEquals("请勿填写个人信息", message.textContent)
    }

    @Test
    fun `wechat guidance fits the single line field at elderly font scale`() {
        val strings = parse(valuesRoot.resolve("strings_contacts.xml"))
        val nodes = strings.getElementsByTagName("string")
            .let { values -> (0 until values.length).mapNotNull { values.item(it) as? Element } }
            .associate { it.getAttribute("name") to it.textContent }

        assertEquals("请输入微信备注或昵称", nodes["hint_wechat_search_name"])
        assertEquals("需填写微信里能搜到的名称", nodes["contact_dialog_wechat_tip"])
    }

    @Test
    fun `long dialogs use compact horizontal identity headers`() {
        listOf("dialog_add_contact.xml", "dialog_phone_contact.xml").forEach { layoutName ->
            val identity = elements(layoutName)
                .single { it.androidAttribute("id") == "@+id/layout_contact_identity" }
            val avatar = elements(layoutName)
                .single { it.androidAttribute("id") == "@+id/iv_photo_preview" }

            assertEquals("horizontal", identity.androidAttribute("orientation"))
            assertEquals("88dp", avatar.androidAttribute("layout_width"))
            assertEquals("88dp", avatar.androidAttribute("layout_height"))
        }

        val versionIdentity = elements("dialog_version_details.xml")
            .single { it.androidAttribute("id") == "@+id/layout_version_identity" }
        assertEquals("horizontal", versionIdentity.androidAttribute("orientation"))
    }

    @Test
    fun `compact choice tile is readable and touchable`() {
        val choice = elements("item_settings_dialog_choice.xml")
        val root = choice.first()

        assertEquals("96dp", root.androidAttribute("minHeight"))
        assertEquals("true", root.androidAttribute("clickable"))
        assertEquals("true", root.androidAttribute("focusable"))
        assertTrue(DIALOG_SECTION_STYLE in layoutRoot.resolve("item_settings_dialog_choice.xml").toFile().readText())
    }

    @Test
    fun `report keeps telemetry details behind a secondary disclosure`() {
        val report = elements("dialog_user_report.xml")
        val details = report.single {
            it.androidAttribute("id") == "@+id/user_report_privacy_details"
        }
        val toggle = report.single {
            it.androidAttribute("id") == "@+id/user_report_privacy_toggle"
        }

        assertEquals("gone", details.androidAttribute("visibility"))
        assertEquals("true", toggle.androidAttribute("clickable"))
        assertEquals("true", toggle.androidAttribute("focusable"))
    }

    @Test
    fun `dialog fields and list rows avoid oversized page typography`() {
        val layoutViolations = DIALOG_LAYOUTS.filter { layoutName ->
            LEGACY_PAGE_FIELD_STYLES.any { it in layoutRoot.resolve(layoutName).toFile().readText() }
        }
        val itemViolations = DIALOG_ITEM_LAYOUTS.filter { layoutName ->
            LEGACY_PAGE_LIST_STYLES.any { it in layoutRoot.resolve(layoutName).toFile().readText() }
        }

        assertTrue("Dialog fields must use dialog typography: $layoutViolations", layoutViolations.isEmpty())
        assertTrue("Dialog list rows must use dialog typography: $itemViolations", itemViolations.isEmpty())
    }

    private fun elements(layoutName: String): List<Element> {
        val document = parse(layoutRoot.resolve(layoutName))
        val nodes = document.getElementsByTagName("*")
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun styleItem(styleName: String, itemName: String): String {
        val document = parse(valuesRoot.resolve("typography.xml"))
        val styles = document.getElementsByTagName("style")
        val style = (0 until styles.length)
            .mapNotNull { styles.item(it) as? Element }
            .single { it.getAttribute("name") == styleName }
        val items = style.getElementsByTagName("item")
        return (0 until items.length)
            .mapNotNull { items.item(it) as? Element }
            .singleOrNull { it.getAttribute("name") == itemName }
            ?.textContent
            .orEmpty()
    }

    private fun parse(path: Path) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(path.toFile())

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val DIALOG_TITLE_STYLE = "@style/TextAppearance.OldLauncher.DialogTitle"
        const val DIALOG_MESSAGE_STYLE = "@style/TextAppearance.OldLauncher.DialogMessage"
        const val DIALOG_SECTION_STYLE = "@style/TextAppearance.OldLauncher.DialogSectionLabel"
        const val DIALOG_CONTROL_HEIGHT = "@dimen/launcher_dialog_control_height"

        val DIALOG_LAYOUTS = listOf(
            "dialog_accessibility_prompt.xml",
            "dialog_add_contact.xml",
            "dialog_avatar_editor.xml",
            "dialog_delete_contact.xml",
            "dialog_fall_contact.xml",
            "dialog_import_contacts.xml",
            "dialog_overlay_permission.xml",
            "dialog_permission_group.xml",
            "dialog_phone_contact.xml",
            "dialog_phone_layout_choice.xml",
            "dialog_set_city.xml",
            "dialog_settings_contacts.xml",
            "dialog_settings_value.xml",
            "dialog_user_report.xml",
            "dialog_version_details.xml",
            "dialog_weather_city_search.xml"
        )

        val CONTROL_IDS = mapOf(
            "dialog_add_contact.xml" to listOf("et_contact_name", "et_wechat_name"),
            "dialog_phone_contact.xml" to listOf("et_name", "et_phone", "sw_auto_answer"),
            "dialog_settings_value.xml" to listOf("value_dialog_minus", "value_dialog_plus")
        )

        val DIALOG_ITEM_LAYOUTS = listOf(
            "item_import_contact_option.xml",
            "item_settings_dialog_section.xml",
            "item_settings_dialog_choice.xml",
            "item_settings_permission_entry.xml",
            "item_settings_permission_entry_compact.xml"
        )

        val TINY_TEXT_SIZES = setOf("12sp", "13sp")

        val LEGACY_PAGE_BUTTON_STYLES = setOf(
            "@style/TextAppearance.OldLauncher.ButtonPrimary",
            "@style/TextAppearance.OldLauncher.ButtonSecondary"
        )

        val LEGACY_PAGE_FIELD_STYLES = setOf("@style/TextAppearance.OldLauncher.Input")

        val LEGACY_PAGE_LIST_STYLES = setOf(
            "@style/TextAppearance.OldLauncher.ListTitle",
            "@style/TextAppearance.OldLauncher.Meta",
            "@style/TextAppearance.OldLauncher.Badge"
        )
    }
}
