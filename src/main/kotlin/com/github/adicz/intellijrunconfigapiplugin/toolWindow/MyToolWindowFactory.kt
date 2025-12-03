package com.github.adicz.intellijrunconfigapiplugin.toolWindow

import com.github.adicz.intellijrunconfigapiplugin.settings.ApiSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.github.adicz.intellijrunconfigapiplugin.MyBundle
import com.github.adicz.intellijrunconfigapiplugin.services.ApiService
import com.github.adicz.intellijrunconfigapiplugin.services.RunConfigurationService
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.SwingUtilities

class MyToolWindowFactory : ToolWindowFactory {

    private val logger = thisLogger()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow) {

        private val runConfigService = toolWindow.project.service<RunConfigurationService>()
        private val apiService = toolWindow.project.service<ApiService>()
        private val logger = thisLogger()

        // Store references to UI components that need to be updated
        private lateinit var statusLabel: JBLabel
        private lateinit var apiInfoLabel: JBLabel

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()

            // Header panel with API status
            val headerPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

                val currentPort = apiService.getCurrentPort()
                val portStatus = if (currentPort > 0) "Running on port $currentPort" else "Not running"
                statusLabel = JBLabel("API Server: $portStatus")
                add(statusLabel, BorderLayout.CENTER)

                // Add a prominent STOP button to the header
                val stopButton = JButton("STOP").apply {
                    background = java.awt.Color.RED
                    foreground = java.awt.Color.WHITE
                    font = font.deriveFont(java.awt.Font.BOLD)
                    addActionListener {
                        if (apiService.isServerRunning()) {
                            apiService.stopServer()
                            updatePortInfo()
                            JOptionPane.showMessageDialog(
                                this,
                                "API Server stopped",
                                "Server Stopped",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        } else {
                            JOptionPane.showMessageDialog(
                                this,
                                "API Server is not running",
                                "Server Status",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        }
                    }
                }
                add(stopButton, BorderLayout.EAST)
            }
            add(headerPanel, BorderLayout.NORTH)

            // Settings panel for port configuration
            val settingsPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createTitledBorder("Server Settings")

                val settingsService = ApiSettingsService.getInstance(toolWindow.project)

                val portPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    isFocusable = true
                    isFocusCycleRoot = false
                    isFocusTraversalPolicyProvider = false

                    val portLabel = JLabel("Port:")
                    add(portLabel)

                    // Use a regular JTextField instead of JBTextField
                    val portField = JTextField(10)
                    portField.isEditable = true
                    portField.isFocusable = true
                    portField.isRequestFocusEnabled = true
                    portLabel.setLabelFor(portField)
                    if (!settingsService.isUsingRandomPort()) {
                        portField.text = settingsService.getPort().toString()
                    }

                    // Add focus listener for debugging
                    portField.addFocusListener(object : java.awt.event.FocusListener {
                        override fun focusGained(e: java.awt.event.FocusEvent) {
                            logger.info("Port field gained focus")
                        }

                        override fun focusLost(e: java.awt.event.FocusEvent) {
                            logger.info("Port field lost focus, text: ${portField.text}")
                        }
                    })

                    // Add key listener for debugging
                    portField.addKeyListener(object : java.awt.event.KeyAdapter() {
                        override fun keyTyped(e: java.awt.event.KeyEvent) {
                            logger.info("Key typed in port field: ${e.keyChar}")
                        }
                    })

                    // Add document listener for debugging
                    portField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                            logger.info("Document insert update in port field: ${portField.text}")
                        }

                        override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                            logger.info("Document remove update in port field: ${portField.text}")
                        }

                        override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                            logger.info("Document changed update in port field: ${portField.text}")
                        }
                    })

                    // Add mouse listener to request focus when clicked
                    portField.addMouseListener(object : java.awt.event.MouseAdapter() {
                        override fun mouseClicked(e: java.awt.event.MouseEvent) {
                            logger.info("Mouse clicked on port field")
                            portField.requestFocusInWindow()
                        }
                    })

                    add(portField)

                    val randomPortCheckbox = JCheckBox("Use random port")
                    randomPortCheckbox.isSelected = settingsService.isUsingRandomPort()
                    randomPortCheckbox.addActionListener {
                        portField.isEnabled = !randomPortCheckbox.isSelected
                        if (randomPortCheckbox.isSelected) {
                            settingsService.useRandomPort()
                        }
                    }
                    add(randomPortCheckbox)

                    portField.isEnabled = !randomPortCheckbox.isSelected

                    val applyButton = JButton("Apply")
                    applyButton.addActionListener {
                        if (!randomPortCheckbox.isSelected) {
                            try {
                                val port = portField.text.toInt()
                                if (port in 1024..65535) {
                                    settingsService.setPort(port)

                                    // Restart the server with the new port
                                    apiService.stopServer()
                                    apiService.startServer()

                                    val newPort = apiService.getCurrentPort()
                                    JOptionPane.showMessageDialog(
                                        this,
                                        "Port settings applied. Server restarted on port $newPort",
                                        "Settings Applied",
                                        JOptionPane.INFORMATION_MESSAGE
                                    )

                                    // Update the UI with the new port information
                                    updatePortInfo()

                                    // Refresh the run configurations
                                    refreshRunConfigurations()
                                } else {
                                    JOptionPane.showMessageDialog(
                                        this,
                                        "Port must be between 1024 and 65535",
                                        "Invalid Port",
                                        JOptionPane.ERROR_MESSAGE
                                    )
                                }
                            } catch (e: NumberFormatException) {
                                JOptionPane.showMessageDialog(
                                    this,
                                    "Please enter a valid port number",
                                    "Invalid Port",
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                        } else {
                            // If using random port, just restart the server
                            apiService.stopServer()
                            apiService.startServer()

                            val newPort = apiService.getCurrentPort()
                            JOptionPane.showMessageDialog(
                                this,
                                "Server restarted on random port $newPort",
                                "Server Restarted",
                                JOptionPane.INFORMATION_MESSAGE
                            )

                            // Update the UI with the new port information
                            updatePortInfo()

                            // Refresh the run configurations
                            refreshRunConfigurations()
                        }
                    }
                    add(applyButton)

                    // Add Start and Stop buttons for the server
                    val startButton = JButton("Start Server")
                    startButton.addActionListener {
                        if (!apiService.isServerRunning()) {
                            apiService.startServer()
                            updatePortInfo()
                            JOptionPane.showMessageDialog(
                                this,
                                "Server started on port ${apiService.getCurrentPort()}",
                                "Server Started",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        } else {
                            JOptionPane.showMessageDialog(
                                this,
                                "Server is already running",
                                "Server Status",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        }
                    }
                    add(startButton)

                    val stopButton = JButton("Stop Server")
                    stopButton.addActionListener {
                        if (apiService.isServerRunning()) {
                            apiService.stopServer()
                            updatePortInfo()
                            JOptionPane.showMessageDialog(
                                this,
                                "Server stopped",
                                "Server Stopped",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        } else {
                            JOptionPane.showMessageDialog(
                                this,
                                "Server is not running",
                                "Server Status",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        }
                    }
                    add(stopButton)
                }

                add(portPanel, BorderLayout.CENTER)
            }

            // Create a central panel to hold both settings and run configurations
            val centralPanel = JPanel().apply {
                layout = BorderLayout()
                border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

                // Add settings panel at the top
                add(settingsPanel, BorderLayout.NORTH)

                // Main panel with run configurations
                val runConfigPanel = JPanel(BorderLayout()).apply {
                    border = BorderFactory.createTitledBorder("Run Configurations")

                    // Panel for the configurations
                    val configsPanel = JPanel().apply {
                        layout = GridLayout(0, 1, 5, 5)

                        // Add run configurations
                        refreshRunConfigurations(this)
                    }

                    // Add reload config button at the top
                    val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        add(JButton("Reload Config").apply {
                            addActionListener {
                                refreshRunConfigurations(configsPanel)
                            }
                        })
                    }

                    add(buttonPanel, BorderLayout.NORTH)
                    add(configsPanel, BorderLayout.CENTER)
                }

                val scrollPane = JBScrollPane(runConfigPanel)
                add(scrollPane, BorderLayout.CENTER)
            }
            add(centralPanel, BorderLayout.CENTER)

            // Footer panel with API info
            val footerPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

                val currentPort = apiService.getCurrentPort()
                apiInfoLabel = JBLabel("API Endpoints: http://localhost:$currentPort/api/run-configurations")
                add(apiInfoLabel, BorderLayout.CENTER)
            }
            add(footerPanel, BorderLayout.SOUTH)
        }

        /**
         * Update the UI with the current port information
         */
        private fun updatePortInfo() {
            SwingUtilities.invokeLater {
                val currentPort = apiService.getCurrentPort()
                val portStatus = if (currentPort > 0) "Running on port $currentPort" else "Not running"
                statusLabel.text = "API Server: $portStatus"
                apiInfoLabel.text = "API Endpoints: http://localhost:$currentPort/api/run-configurations"
            }
        }

        private fun refreshRunConfigurations(panel: JPanel? = null) {
            SwingUtilities.invokeLater {
                try {
                    val configurations = runConfigService.getAllRunConfigurations()

                    if (panel != null) {
                        panel.removeAll()

                        if (configurations.isEmpty()) {
                            panel.add(JBLabel("No run configurations found"))
                        } else {
                            for (config in configurations) {
                                val configPanel = JPanel(BorderLayout()).apply {
                                    border = BorderFactory.createTitledBorder(config.name)

                                    val buttonsPanel = JPanel().apply {
                                        add(JButton("Start").apply {
                                            addActionListener {
                                                runConfigService.startRunConfiguration(config.name)
                                            }
                                        })

                                        add(JButton("Stop").apply {
                                            addActionListener {
                                                runConfigService.stopRunConfiguration(config.name)
                                            }
                                        })

                                        add(JButton("Restart").apply {
                                            addActionListener {
                                                runConfigService.restartRunConfiguration(config.name)
                                            }
                                        })
                                    }

                                    add(buttonsPanel, BorderLayout.CENTER)
                                }

                                panel.add(configPanel)
                            }
                        }

                        panel.revalidate()
                        panel.repaint()
                    }
                } catch (e: Exception) {
                    logger.error("Failed to refresh run configurations", e)
                }
            }
        }
    }
}
