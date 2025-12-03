package com.github.adicz.intellijrunconfigapiplugin.startup

import com.github.adicz.intellijrunconfigapiplugin.services.ApiService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {
    private val logger = thisLogger()

    override suspend fun execute(project: Project) {
        logger.info("Starting Run Configuration API server...")

        try {
            val apiService = project.service<ApiService>()
            apiService.startServer()
            logger.info("Run Configuration API server started successfully")
        } catch (e: Exception) {
            logger.error("Failed to start Run Configuration API server", e)
        }
    }
}
