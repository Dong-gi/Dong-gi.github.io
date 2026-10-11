package io.github.donggi.iroirobrowser.adblock

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.donggi.iroirobrowser.R
import io.github.donggi.iroirobrowser.WebApp
import io.github.donggi.iroirobrowser.ui.Ui
import java.text.NumberFormat

/**
 * 광고 차단 설정 — 켜기, 목록(켜기·끄기·상태·더하기·빼기), 지금 갱신, Wi-Fi 에서만 갱신, 광고 차단을 끈 사이트, **숨긴 요소**(페이지의 메뉴
 * '요소 숨기기' 로 숨긴 것 — 사이트별로, 규칙 하나 지우기·사이트째 지우기. 광고 목록과 따로 저장한다 — [HiddenElements]).
 */
@Suppress("DEPRECATION") // Switch: 플랫폼 위젯을 쓴다(새 의존 없이)
class AdBlockActivity : Activity() {

    private lateinit var service: AdBlockService
    private lateinit var hidden: HiddenElements
    private lateinit var column: LinearLayout
    private val listener = AdBlockService.Listener { render() }
    private val hiddenListener = HiddenElements.Listener { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = WebApp.of(this).adblock
        hidden = WebApp.of(this).hidden
        val scroll = ScrollView(this)
        Ui.edgeToEdge(this, scroll)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 32))
        }
        scroll.addView(column)
        setContentView(scroll)
        render()
    }

    override fun onStart() {
        super.onStart()
        service.addListener(listener)
        hidden.addListener(hiddenListener)
        render()
    }

    override fun onStop() {
        service.removeListener(listener)
        hidden.removeListener(hiddenListener)
        super.onStop()
    }

    private fun render() {
        column.removeAllViews()
        column.addView(Ui.header(this, getString(R.string.adblock)) { finish() })
        column.addView(padded(Switch(this).apply {
            text = getString(R.string.adblock)
            textSize = 16f
            isChecked = service.enabled
            setOnCheckedChangeListener { _, on -> service.setEnabled(on) }
        }))
        note(getString(R.string.adblock_note))

        section(R.string.adblock_lists)
        val engine = service.engine
        if (service.busy) note(getString(R.string.adblock_busy))
        for (list in service.lists) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(context, 16), Ui.dp(context, 2), Ui.dp(context, 16), Ui.dp(context, 2))
            }
            row.addView(CheckBox(this).apply {
                text = list.title
                isChecked = list.enabled
                setOnCheckedChangeListener { _, on -> service.setListEnabled(list, on) }
                if (!list.builtin) {
                    setOnLongClickListener {
                        confirmRemove(list)
                        true
                    }
                }
            })
            row.addView(TextView(this).apply {
                text = status(list)
                textSize = 12f
                alpha = 0.7f
                setPadding(Ui.dp(context, 32), 0, 0, Ui.dp(context, 4))
            })
            column.addView(row)
        }
        if (engine != null) {
            val nf = NumberFormat.getIntegerInstance()
            note(getString(R.string.adblock_engine, nf.format(engine.networkRuleCount), nf.format(engine.cosmeticRuleCount)))
        }
        column.addView(padded(Button(this).apply {
            text = getString(R.string.adblock_update_now)
            isEnabled = !service.busy
            setOnClickListener {
                service.updateNow { Toast.makeText(this@AdBlockActivity, R.string.adblock_updated, Toast.LENGTH_SHORT).show() }
            }
        }))
        column.addView(padded(Switch(this).apply {
            text = getString(R.string.adblock_wifi_only)
            isChecked = service.wifiOnly
            setOnCheckedChangeListener { _, on -> service.setWifiOnly(on) }
        }))
        column.addView(padded(Button(this).apply {
            text = getString(R.string.adblock_add_list)
            setOnClickListener { addList() }
        }))

        section(R.string.adblock_allowed_sites)
        val sites = service.allowedSites()
        if (sites.isEmpty()) note(getString(R.string.adblock_allowed_none))
        for (site in sites) {
            column.addView(TextView(this).apply {
                text = site
                textSize = 15f
                setPadding(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), Ui.dp(context, 10))
                setOnClickListener {
                    AlertDialog.Builder(this@AdBlockActivity)
                        .setMessage(getString(R.string.adblock_allowed_remove, site))
                        .setPositiveButton(R.string.adblock_allowed_reenable) { _, _ -> service.setAllowed(site, false) }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            })
        }
        renderHidden()
        note(getString(R.string.adblock_credits))
    }

    /** '숨긴 요소' — 사이트마다 이름·수와 '모두 지우기', 그 아래 규칙(선택자)마다 한 줄(누르면 지울지 묻는다). */
    private fun renderHidden() {
        section(R.string.hidden_elements)
        note(getString(R.string.hidden_elements_note))
        val sites = hidden.sites()
        if (sites.isEmpty()) note(getString(R.string.hidden_elements_none))
        for (site in sites) {
            val rules = hidden.rulesOf(site)
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 8), 0)
            }
            header.addView(TextView(this).apply {
                text = getString(R.string.hidden_site_row, site, rules.size)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = getString(R.string.hidden_remove_site)
                contentDescription = getString(R.string.hidden_remove_site_description, site)
                setOnClickListener {
                    AlertDialog.Builder(this@AdBlockActivity)
                        .setMessage(getString(R.string.hidden_remove_site_question, site, rules.size))
                        .setPositiveButton(R.string.delete) { _, _ -> hidden.removeSite(site) }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            })
            column.addView(header)
            for (rule in rules) {
                column.addView(TextView(this).apply {
                    text = rule.selector
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    contentDescription = getString(R.string.hidden_rule_description, rule.selector)
                    setPadding(Ui.dp(context, 32), Ui.dp(context, 6), Ui.dp(context, 20), Ui.dp(context, 6))
                    setBackgroundResource(TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }.resourceId)
                    setOnClickListener {
                        AlertDialog.Builder(this@AdBlockActivity)
                            .setMessage(getString(R.string.hidden_remove_rule_question, rule.toString()))
                            .setPositiveButton(R.string.delete) { _, _ -> hidden.remove(rule) }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                })
            }
        }
    }

    private fun status(list: AdBlockService.FilterList): String {
        val parts = ArrayList<String>()
        if (list.ruleCount > 0) parts += getString(R.string.adblock_rules, NumberFormat.getIntegerInstance().format(list.ruleCount))
        parts += if (list.lastSuccess > 0) {
            getString(R.string.adblock_fetched, DateUtils.getRelativeTimeSpanString(list.lastSuccess).toString())
        } else {
            getString(R.string.adblock_not_fetched)
        }
        list.error?.let { parts += getString(R.string.adblock_error, it) }
        return parts.joinToString(" · ")
    }

    private fun addList() {
        val url = EditText(this).apply {
            hint = "https://…/list.txt"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val title = EditText(this).apply {
            hint = getString(R.string.adblock_list_title)
            isSingleLine = true
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), 0)
            addView(url, Ui.matchWidth())
            addView(title, Ui.matchWidth())
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.adblock_add_list)
            .setView(box)
            .setPositiveButton(R.string.adblock_add) { _, _ ->
                val u = url.text.toString().trim()
                if (!u.startsWith("https://") && !u.startsWith("http://")) {
                    Toast.makeText(this, R.string.adblock_bad_url, Toast.LENGTH_SHORT).show()
                } else if (service.addList(u, title.text.toString().trim().ifEmpty { u.substringAfter("://").substringBefore('/') }) == null) {
                    Toast.makeText(this, R.string.adblock_duplicate, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(list: AdBlockService.FilterList) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.adblock_remove_question, list.title))
            .setPositiveButton(R.string.delete) { _, _ -> service.removeList(list) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun section(title: Int) {
        column.addView(TextView(this).apply {
            text = getString(title)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Ui.themeColor(context, android.R.attr.colorAccent))
            setPadding(Ui.dp(context, 20), Ui.dp(context, 24), Ui.dp(context, 20), Ui.dp(context, 4))
        })
    }

    private fun note(text: String) {
        column.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            alpha = 0.75f
            setPadding(Ui.dp(context, 20), Ui.dp(context, 2), Ui.dp(context, 20), Ui.dp(context, 6))
        })
    }

    private fun padded(view: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
        addView(view, Ui.matchWidth())
    }
}
