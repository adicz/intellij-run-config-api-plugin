package com.github.adicz.intellijrunconfigapiplugin.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.concurrent.ThreadLocalRandom

@Service(Service.Level.PROJECT)
@State(
    name = "RunConfigApiSettings",
    storages = [Storage("runConfigApiSettings.xml")]
)
class ApiSettingsService(private val project: Project) : PersistentStateComponent<ApiSettingsService.State> {
    private val logger = thisLogger()
    private var myState = State()

    /**
     * State class for storing settings
     */
    data class State(
        var port: Int = -1 // -1 means use random port
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /**
     * Get the configured port or generate a random one if not set
     */
    fun getPort(): Int {
        if (myState.port == -1) {
            // Generate a random port between 10000 and 65535
            return ThreadLocalRandom.current().nextInt(10000, 65535)
        }
        return myState.port
    }

    /**
     * Set a specific port
     */
    fun setPort(port: Int) {
        myState.port = port
        logger.info("Port set to: $port")
    }

    /**
     * Reset to use random port
     */
    fun useRandomPort() {
        myState.port = -1
        logger.info("Set to use random port")
    }

    /**
     * Check if using random port
     */
    fun isUsingRandomPort(): Boolean {
        return myState.port == -1
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ApiSettingsService {
            return project.service<ApiSettingsService>()
        }
    }
}