package com.github.adicz.intellijrunconfigapiplugin.services

import com.github.adicz.intellijrunconfigapiplugin.settings.ApiSettingsService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class ApiService(private val project: Project) {
    private val logger = thisLogger()
    private val isServerRunning = AtomicBoolean(false)
    private var server: HttpServer? = null
    private val settingsService = project.service<ApiSettingsService>()
    private var currentPort: Int = -1 // Will store the actual port being used

    /**
     * Start the API server
     */
    fun startServer() {
        if (isServerRunning.getAndSet(true)) {
            logger.info("API server is already running")
            return
        }

        try {
            // Get port from settings or use random port
            currentPort = settingsService.getPort()
            server = HttpServer.create(InetSocketAddress(currentPort), 0)
            server?.executor = Executors.newCachedThreadPool()

            // Get all run configurations
            server?.createContext("/api/run-configurations") { exchange ->
                if (exchange.requestMethod == "GET") {
                    val runConfigService = project.service<RunConfigurationService>()
                    val configurations = runConfigService.getAllRunConfigurations()
                        .map { it.name }

                    val response = "{ \"configurations\": [${configurations.joinToString(", ") { "\"$it\"" }}] }"
                    sendResponse(exchange, 200, response)
                } else {
                    sendResponse(exchange, 405, "{ \"error\": \"Method not allowed\" }")
                }
            }

            // Start a run configuration
            server?.createContext("/api/run-configurations/") { exchange ->
                if (exchange.requestMethod == "GET" && exchange.requestURI.path.endsWith("/start")) {
                    val name = extractNameFromPath(exchange.requestURI.path)
                    if (name.isNullOrEmpty()) {
                        sendResponse(exchange, 400, "{ \"error\": \"Configuration name is required\" }")
                        return@createContext
                    }

                    val runConfigService = project.service<RunConfigurationService>()
                    val result = runConfigService.startRunConfiguration(name)

                    if (result) {
                        sendResponse(exchange, 200, "{ \"status\": \"started\", \"name\": \"$name\" }")
                    } else {
                        sendResponse(exchange, 404, "{ \"error\": \"Failed to start configuration: $name\" }")
                    }
                } else if (exchange.requestMethod == "GET" && exchange.requestURI.path.endsWith("/stop")) {
                    val name = extractNameFromPath(exchange.requestURI.path)
                    if (name.isNullOrEmpty()) {
                        sendResponse(exchange, 400, "{ \"error\": \"Configuration name is required\" }")
                        return@createContext
                    }

                    val runConfigService = project.service<RunConfigurationService>()
                    val result = runConfigService.stopRunConfiguration(name)

                    if (result) {
                        sendResponse(exchange, 200, "{ \"status\": \"stopped\", \"name\": \"$name\" }")
                    } else {
                        sendResponse(exchange, 404, "{ \"error\": \"Failed to stop configuration: $name\" }")
                    }
                } else if (exchange.requestMethod == "GET" && exchange.requestURI.path.endsWith("/restart")) {
                    val name = extractNameFromPath(exchange.requestURI.path)
                    if (name.isNullOrEmpty()) {
                        sendResponse(exchange, 400, "{ \"error\": \"Configuration name is required\" }")
                        return@createContext
                    }

                    val runConfigService = project.service<RunConfigurationService>()
                    val result = runConfigService.restartRunConfiguration(name)

                    if (result) {
                        sendResponse(exchange, 200, "{ \"status\": \"restarted\", \"name\": \"$name\" }")
                    } else {
                        sendResponse(exchange, 404, "{ \"error\": \"Failed to restart configuration: $name\" }")
                    }
                } else {
                    sendResponse(exchange, 405, "{ \"error\": \"Method not allowed\" }")
                }
            }

            server?.start()
            logger.info("API server started on port $currentPort")
        } catch (e: Exception) {
            isServerRunning.set(false)
            logger.error("Failed to start API server", e)
        }
    }

    /**
     * Extract name from path like /api/run-configurations/name/action
     */
    private fun extractNameFromPath(path: String): String? {
        val parts = path.split("/")
        if (parts.size < 4) return null
        return parts[3]
    }

    /**
     * Send HTTP response
     */
    private fun sendResponse(exchange: HttpExchange, statusCode: Int, response: String) {
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(statusCode, response.length.toLong())
        val os = exchange.responseBody
        os.write(response.toByteArray())
        os.close()
    }

    /**
     * Get the current port the server is running on
     */
    fun getCurrentPort(): Int {
        return currentPort
    }

    /**
     * Check if the server is running
     */
    fun isServerRunning(): Boolean {
        return isServerRunning.get()
    }

    /**
     * Stop the API server
     */
    fun stopServer() {
        if (!isServerRunning.getAndSet(false)) {
            logger.info("API server is not running")
            return
        }

        try {
            server?.stop(0)
            server = null
            currentPort = -1
            logger.info("API server stopped")
        } catch (e: Exception) {
            logger.error("Failed to stop API server", e)
        }
    }
}
