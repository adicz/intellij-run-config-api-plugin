package com.github.adicz.intellijrunconfigapiplugin.services

import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class RunConfigurationService(private val project: Project) {

    private val logger = thisLogger()
    private val runManager = RunManager.getInstance(project)

    /**
     * Get all run configurations
     */
    fun getAllRunConfigurations(): List<RunnerAndConfigurationSettings> {
        return runManager.allSettings
    }

    /**
     * Get run configuration by name
     */
    fun getRunConfigurationByName(name: String): RunnerAndConfigurationSettings? {
        return runManager.findConfigurationByName(name)
    }

    /**
     * Start a run configuration
     */
    fun startRunConfiguration(name: String): Boolean {
        val configuration = getRunConfigurationByName(name) ?: return false

        try {
            val environment = ExecutionEnvironmentBuilder
                .create(DefaultRunExecutor.getRunExecutorInstance(), configuration)
                .build()

            environment.runner.execute(environment)
            logger.info("Started run configuration: $name")
            return true
        } catch (e: Exception) {
            logger.error("Failed to start run configuration: $name", e)
            return false
        }
    }

    /**
     * Stop all running processes
     */
    fun stopAllRunningProcesses(): Boolean {
        val executionManager = ExecutionManager.getInstance(project)
        val processes = executionManager.getRunningProcesses()

        var stopped = false
        for (process in processes) {
            process.destroyProcess()
            stopped = true
        }

        logger.info("Stopped all running processes")
        return stopped
    }

    /**
     * Stop a run configuration by name
     * Only stops processes associated with the specified configuration
     */
    fun stopRunConfiguration(name: String): Boolean {
        logger.info("Attempting to stop run configuration: $name")

        val executionManager = ExecutionManager.getInstance(project)
        val processes = executionManager.getRunningProcesses()

        // Filter processes by configuration name
        // This is a best-effort approach since we don't have direct access to the configuration name
        // We'll use the toString() representation which often contains the configuration name
        logger.info("Found ${processes.size} running processes")
        processes.forEach { process ->
            logger.info("Process: ${process.toString()}")
        }

        val matchingProcesses = processes.filter { process ->
            val matches = process.toString().contains(name)
            if (matches) {
                logger.info("Found matching process for configuration: $name")
            }
            matches
        }

        if (matchingProcesses.isEmpty()) {
            logger.info("No running processes found for configuration: $name")
            return false
        }

        var stopped = false
        for (process in matchingProcesses) {
            process.destroyProcess()
            stopped = true
            logger.info("Stopped process that matches configuration: $name")
        }

        return stopped
    }

    /**
     * Restart a run configuration
     */
    fun restartRunConfiguration(name: String): Boolean {
        val stopped = stopRunConfiguration(name)
        if (!stopped) {
            logger.warn("Could not stop run configuration: $name. Starting anyway.")
        }

        // Give it a moment to stop
        Thread.sleep(500)

        return startRunConfiguration(name)
    }
}
