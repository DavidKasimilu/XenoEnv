package com.example.dualspace

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private data class AppItem(val label: String, val pkg: String, val icon: Drawable)

    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private lateinit var status: TextView
    private lateinit var createBtn: Button
    private lateinit var list: ListView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(DevicePolicyManager::class.java)
        admin = ComponentName(this, AdminReceiver::class.java)

        status = TextView(this).apply { textSize = 16f }
        createBtn = Button(this).apply {
            text = "Create Dual Space"
            setOnClickListener { startProvisioning() }
        }
        list = ListView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(status)
            addView(createBtn)
            addView(list, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun inProfile() = dpm.isProfileOwnerApp(packageName)

    private fun hasProfile() =
        getSystemService(UserManager::class.java).userProfiles.size > 1

    private fun refresh() {
        when {
            inProfile() -> {
                status.text = "You are inside the Dual Space profile.\n\n" +
                    "Cloned apps appear in your launcher with a briefcase badge. " +
                    "Open the Dual Space icon WITHOUT the badge to clone more apps."
                createBtn.visibility = View.GONE
                list.visibility = View.GONE
            }
            hasProfile() -> {
                status.text = "Tap an app to clone it. Long-press to remove a clone."
                createBtn.visibility = View.GONE
                list.visibility = View.VISIBLE
                showApps()
            }
            else -> {
                status.text = "Dual Space creates an isolated Android work profile. " +
                    "Apps cloned into it have separate data, so you can log into a " +
                    "second account."
                createBtn.visibility = View.VISIBLE
                list.visibility = View.GONE
            }
        }
    }

    private fun showApps() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }

        list.adapter = AppAdapter(apps)
        list.setOnItemClickListener { _, _, pos, _ ->
            val app = apps[pos]
            AlertDialog.Builder(this)
                .setTitle(app.label)
                .setMessage("Clone this app into Dual Space?")
                .setPositiveButton("Clone") { _, _ -> sendRequest(app.pkg, false) }
                .setNegativeButton("Cancel", null)
                .show()
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val app = apps[pos]
            AlertDialog.Builder(this)
                .setTitle(app.label)
                .setMessage("Remove the clone from Dual Space?")
                .setPositiveButton("Remove") { _, _ -> sendRequest(app.pkg, true) }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
    }

    /** Sends an intent that Android forwards across the profile boundary. */
    private fun sendRequest(pkg: String, remove: Boolean) {
        val intent = Intent(Actions.CLONE)
            .putExtra(Actions.EXTRA_PKG, pkg)
            .putExtra(Actions.EXTRA_REMOVE, remove)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Dual Space profile is not ready", Toast.LENGTH_LONG).show()
        }
    }

    private fun startProvisioning() {
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PROVISION)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Work profiles are not supported on this device", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROVISION) {
            val msg = if (resultCode == RESULT_OK) "Dual Space created!" else "Setup cancelled or failed"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private inner class AppAdapter(val items: List<AppItem>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = items[position]
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            row.addView(ImageView(this@MainActivity).apply { setImageDrawable(item.icon) },
                LinearLayout.LayoutParams(dp(40), dp(40)))
            row.addView(TextView(this@MainActivity).apply {
                text = item.label
                textSize = 16f
                setPadding(dp(12), 0, 0, 0)
            })
            return row
        }
    }

    companion object {
        private const val REQ_PROVISION = 1
    }
}
