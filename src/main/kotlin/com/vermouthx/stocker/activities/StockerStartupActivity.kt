package com.vermouthx.stocker.activities

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.vermouthx.stocker.StockerApp
import com.vermouthx.stocker.notifications.StockerNotification
import com.vermouthx.stocker.settings.StockerSetting

class StockerStartupActivity : StartupActivity, DumbAware {

    companion object {
        private val setting = StockerSetting.instance
    }

    override fun runActivity(project: Project) {
        val currentVersion = PluginManager.getPlugin(PluginId.getId(StockerApp.pluginId))?.version ?: ""
        if (setting.version != currentVersion) {
            setting.version = currentVersion
            StockerNotification.notifyReleaseNote(project, currentVersion)
        }
    }
}