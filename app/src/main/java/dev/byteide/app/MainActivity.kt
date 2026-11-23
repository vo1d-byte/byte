package dev.byteide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Canvas = Color(0xFF0B0F17)
private val Surface = Color(0xFF141B27)
private val Surface2 = Color(0xFF1B2534)
private val Accent = Color(0xFF8BA8FF)
private val Muted = Color(0xFF91A0B5)
private val TextMain = Color(0xFFE8EEF9)
private val ErrorRed = Color(0xFFFF647C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ByteTheme { EditorFirstWorkspace() } }
    }
}

@Composable
private fun ByteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Canvas, surface = Surface,
        onBackground = TextMain, onSurface = TextMain, secondary = Muted
    ), content = content)
}

private enum class OutputTab(val label: String) {
    OUTPUT("Output"), TERMINAL("Terminal"), PROBLEMS("Problems"), DEBUG("Debug"), LOGS("Logs")
}
private enum class PanelSize { COLLAPSED, NORMAL, EXPANDED }

@Composable
private fun EditorFirstWorkspace() {
    var file by remember { mutableStateOf("Main.kt") }
    val editorSession = remember(file) { EditorSession("fun main() {\n    println(\"Hello, Byte!\")\n}", file) }
    val context = LocalContext.current
    val workspaceRoot = remember { File(context.filesDir, "workspace").apply { mkdirs() } }
    val toolchainsRoot = remember { File(context.filesDir, "toolchains").apply { mkdirs() } }
    val cacheRoot = remember { File(context.cacheDir, "toolchain-downloads").apply { mkdirs() } }
    val lspWorkspace = remember {
        LspWorkspaceManager(toolchainsRoot = toolchainsRoot, workspaceRoot = workspaceRoot)
    }
    val toolchainInstaller = remember(toolchainsRoot, cacheRoot, lspWorkspace) {
        ToolchainInstaller(toolchainsRoot, cacheRoot, lspWorkspace)
    }
    val languageCatalog = remember(context) { runCatching { LanguagePackageRegistry.load(context) } }
    var showToolchains by remember { mutableStateOf(false) }
    DisposableEffect(toolchainInstaller) { onDispose { toolchainInstaller.close() } }
    val documentUri = remember(file, workspaceRoot) { File(workspaceRoot, file).toURI().toString() }
    val lspController = remember(editorSession, lspWorkspace, documentUri) {
        LspEditorController(editorSession, lspWorkspace, documentUri, "kotlin")
    }
    val diagnostics = lspWorkspace.snapshot.diagnosticsByUri[documentUri].orEmpty()
    DisposableEffect(lspWorkspace) { onDispose { lspWorkspace.close() } }
    DisposableEffect(lspController) { onDispose { lspController.close() } }
    var editorValue by remember(editorSession) { mutableStateOf(editorSession.value) }
    var editorUiRevision by remember { mutableIntStateOf(0) }
    val dirty = remember(editorUiRevision) { editorSession.isDirty }
    DisposableEffect(editorSession) { onDispose { editorSession.close() } }
    var activeTab by remember { mutableStateOf(OutputTab.OUTPUT) }
    var panelSize by remember { mutableStateOf(PanelSize.NORMAL) }
    var actionLayer by remember { mutableStateOf(0) }
    var showFiles by remember { mutableStateOf(false) }
    var showCursorPad by remember { mutableStateOf(false) }
    var keyboardVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var terminalInput by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("Byte workspace ready. Select Terminal to start an Android shell.") }
    var terminalState by remember { mutableStateOf("Shell stopped") }
    val terminalSession = remember(workspaceRoot) {
        TerminalSession(
            workingDirectory = workspaceRoot,
            onOutput = { snapshot -> outputText = snapshot },
            onState = { terminalState = it }
        )
    }
    DisposableEffect(terminalSession) { onDispose { terminalSession.close() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Canvas)) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxHeight().fillMaxWidth(0.34f)) {
                    OutputPanel(activeTab, { next ->
                        activeTab = next
                        if (next == OutputTab.TERMINAL) terminalSession.start()
                    }, panelSize, { panelSize = it }, error,
                        outputText, terminalInput, { terminalInput = it }, landscape = true,
                        terminalState = terminalState,
                        onTerminalSubmit = { command -> terminalSession.execute(command); terminalInput = "" },
                        onTerminalInterrupt = { terminalSession.sendInterruptByte() },
                        onTerminalPaste = { terminalSession.writeInput(it) },
                        onTerminalResize = { rows, columns -> terminalSession.resize(rows, columns) },
                        diagnostics = diagnostics,
                        onDiagnosticClick = { diagnostic ->
                            val target = diagnostic.range.start
                            val offset = editorValue.text.split("\n").take(target.line).sumOf { it.length + 1 } +
                                target.character
                            val safe = offset.coerceIn(0, editorValue.text.length)
                            editorSession.restoreSelection(TextRange(safe))
                            editorValue = editorSession.value
                        })
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    FileTabs(file, dirty, { file = it }, {})
                    Box(Modifier.weight(1f)) {
                        ByteCodeEditor(editorValue, { next ->
                            editorValue = next
                            editorSession.applyValue(next)
                            lspController.onEditorValueChanged(next)
                            editorUiRevision++
                        }, Modifier.fillMaxSize(), editorSession.syntaxSpans)
                        LspCompletionPopup(
                            items = lspController.uiState.completions,
                            visible = lspController.uiState.completionVisible,
                            onSelect = { item ->
                                lspController.selectCompletion(item)
                                editorValue = editorSession.value
                                editorUiRevision++
                            },
                            onDismiss = { lspController.dismissCompletion() },
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        )
                        LspHoverCard(
                            hover = lspController.uiState.hover,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        )
                    }
                    CodeKeyRow(activeTab == OutputTab.TERMINAL, { token ->
                        if (token == "Cursor") showCursorPad = !showCursorPad
                        else if (activeTab == OutputTab.TERMINAL) {
                            when (token) {
                                "Ctrl+C" -> terminalSession.sendInterruptByte()
                                "Tab" -> terminalSession.writeInput("\t")
                                "↑" -> terminalSession.writeInput("\u001b[A")
                                "↓" -> terminalSession.writeInput("\u001b[B")
                                "Esc" -> terminalSession.writeInput("\u001b")
                                else -> terminalSession.writeInput(token)
                            }
                        } else {
                            val next = editorValue.text + token
                            editorValue = TextFieldValue(next, TextRange(next.length))
                            editorSession.applyValue(editorValue)
                        }
                    })
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                Spacer(Modifier.height(8.dp))
                if (panelSize == PanelSize.COLLAPSED || keyboardVisible || showCursorPad) {
                    CollapsedPeek(error, file) { panelSize = if (panelSize == PanelSize.COLLAPSED) PanelSize.NORMAL else PanelSize.COLLAPSED }
                } else {
                    OutputPanel(activeTab, { next ->
                        activeTab = next
                        if (next == OutputTab.TERMINAL) terminalSession.start()
                    }, panelSize, { panelSize = it }, error,
                        outputText, terminalInput, { terminalInput = it }, landscape = false,
                        terminalState = terminalState,
                        onTerminalSubmit = { command -> terminalSession.execute(command); terminalInput = "" },
                        onTerminalInterrupt = { terminalSession.sendInterruptByte() },
                        onTerminalPaste = { terminalSession.writeInput(it) },
                        modifier = Modifier.fillMaxWidth().weight(if (panelSize == PanelSize.EXPANDED) 0.8f else 0.25f),
                        diagnostics = diagnostics,
                        onDiagnosticClick = { diagnostic ->
                            val target = diagnostic.range.start
                            val offset = editorValue.text.split("\n").take(target.line).sumOf { it.length + 1 } +
                                target.character
                            val safe = offset.coerceIn(0, editorValue.text.length)
                            editorSession.restoreSelection(TextRange(safe))
                            editorValue = editorSession.value
                        })
                }
                FileTabs(file, dirty, { file = it }, {})
                Box(Modifier.weight(1f)) {
                    ByteCodeEditor(editorValue, { next ->
                        editorValue = next
                        editorSession.applyValue(next)
                        lspController.onEditorValueChanged(next)
                        editorUiRevision++
                    }, Modifier.fillMaxSize(), editorSession.syntaxSpans)
                    LspCompletionPopup(
                        items = lspController.uiState.completions,
                        visible = lspController.uiState.completionVisible,
                        onSelect = { item ->
                            lspController.selectCompletion(item)
                            editorValue = editorSession.value
                            editorUiRevision++
                        },
                        onDismiss = { lspController.dismissCompletion() },
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    )
                    LspHoverCard(
                        hover = lspController.uiState.hover,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    )
                }
                if (showCursorPad) CursorPad(onClose = { showCursorPad = false })
                else CodeKeyRow(activeTab == OutputTab.TERMINAL, { token ->
                    if (token == "Cursor") showCursorPad = true
                    else {
                        val next = editorValue.text + token
                        editorValue = TextFieldValue(next, TextRange(next.length))
                        editorSession.applyValue(editorValue)
                    }
                })
                Spacer(Modifier.height(5.dp))
            }
        }

        if (!landscape) {
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 66.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actionLayer > 0) {
                    val actions = if (actionLayer == 1) listOf("Run", "Find", "Undo", "More") else
                        listOf("Debug", "Files", "Languages", "Git", "Replace", "Definition", "Comment", "Duplicate", "Format", "Back")
                    actions.forEach { action ->
                        FilledTonalButton(onClick = {
                            when (action) {
                                "More" -> actionLayer = 2
                                "Back" -> actionLayer = 1
                                "Files" -> showFiles = true
                                "Languages" -> showToolchains = true
                                "Run" -> { outputText = "Run requested. Build runner is not connected yet."; activeTab = OutputTab.OUTPUT; panelSize = PanelSize.NORMAL }
                                "Debug" -> { activeTab = OutputTab.DEBUG; panelSize = PanelSize.NORMAL }
                                else -> outputText = "$action command selected."
                            }
                        }, modifier = Modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 16.dp)) { Text(action) }
                    }
                }
                FloatingActionButton(onClick = { actionLayer = if (actionLayer == 0) 1 else 0 },
                    containerColor = Accent.copy(alpha = if (keyboardVisible) 0.7f else 1f),
                    contentColor = Canvas, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Quick actions")
                }
            }
        }
        if (showFiles) {
            AlertDialog(onDismissRequest = { showFiles = false },
                title = { Text("Files") },
                text = { Text("Project file explorer will appear here when a workspace is opened.") },
                confirmButton = { TextButton(onClick = { showFiles = false }) { Text("Close") } })
        }
        if (showToolchains) {
            val catalog = languageCatalog.getOrNull()
            if (catalog != null) {
                ToolchainManagerDialog(catalog, toolchainInstaller) { showToolchains = false }
            } else {
                AlertDialog(
                    onDismissRequest = { showToolchains = false },
                    title = { Text("Language catalog unavailable") },
                    text = { Text(languageCatalog.exceptionOrNull()?.message ?: "Unable to load language catalog.") },
                    confirmButton = { TextButton(onClick = { showToolchains = false }) { Text("Close") } }
                )
            }
        }
    }
}

@Composable
private fun OutputPanel(
    tab: OutputTab, onTab: (OutputTab) -> Unit, size: PanelSize, onSize: (PanelSize) -> Unit,
    error: Boolean, output: String, terminalInput: String, onTerminalInput: (String) -> Unit,
    landscape: Boolean, modifier: Modifier = Modifier,
    terminalState: String = "Shell stopped",
    onTerminalSubmit: (String) -> Unit = {},
    onTerminalInterrupt: () -> Unit = {},
    onTerminalPaste: (String) -> Unit = {},
    onTerminalResize: (Int, Int) -> Unit = { _, _ -> },
    diagnostics: List<LspDiagnostic> = emptyList(),
    onDiagnosticClick: (LspDiagnostic) -> Unit = {}
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    var selectedTerminalText by remember { mutableStateOf("") }
    var cursorStyle by remember { mutableStateOf("Block") }
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    Column(
        modifier.background(Surface, RoundedCornerShape(16.dp)).padding(8.dp)
            .onSizeChanged { size ->
                val columns = (size.width / (8f * density)).toInt().coerceIn(1, 500)
                val rows = (size.height / (18f * density)).toInt().coerceIn(1, 300)
                onTerminalResize(rows, columns)
            }
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.CenterStart) {
                Text("━━━", color = Muted, fontSize = 15.sp)
            }
            TextButton(onClick = { onSize(when(size) {
                PanelSize.COLLAPSED -> PanelSize.NORMAL
                PanelSize.NORMAL -> PanelSize.EXPANDED
                PanelSize.EXPANDED -> PanelSize.COLLAPSED
            }) }) { Text(if (size == PanelSize.EXPANDED) "Collapse" else "Expand") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutputTab.entries.forEach { item ->
                FilterChip(selected = tab == item, onClick = { onTab(item) },
                    label = { Text(if (item == OutputTab.PROBLEMS) "Problems ${diagnostics.size}" else item.label, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(6.dp))
        if (tab == OutputTab.TERMINAL) {
            Text(terminalState, color = Muted, fontSize = 10.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Block", "Bar", "Underline").forEach { style ->
                    FilterChip(
                        selected = cursorStyle == style,
                        onClick = { cursorStyle = style },
                        label = { Text(style, fontSize = 10.sp) }
                    )
                }
            }
            TerminalScreen(
                output = output,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                cursorStyle = cursorStyle,
                onSelectionChanged = { selectedTerminalText = it }
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (selectedTerminalText.isNotEmpty()) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Byte Terminal", selectedTerminalText))
                    }
                }, enabled = selectedTerminalText.isNotEmpty()) { Text("Copy") }
                TextButton(onClick = {
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        onTerminalPaste(clip.getItemAt(0).coerceToText(context).toString())
                    }
                }, enabled = clipboard.hasPrimaryClip()) { Text("Paste") }
                OutlinedTextField(
                    value = terminalInput, onValueChange = onTerminalInput,
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Enter shell command") },
                    textStyle = TextStyle(color = TextMain, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { onTerminalSubmit(terminalInput) }, enabled = terminalInput.isNotBlank()) {
                    Icon(Icons.Default.Send, contentDescription = "Run command", tint = Accent)
                }
                IconButton(onClick = onTerminalInterrupt) {
                    Icon(Icons.Default.Stop, contentDescription = "Send interrupt byte", tint = ErrorRed)
                }
            }
        } else if (tab == OutputTab.PROBLEMS) {
            LspProblemsPanel(diagnostics = diagnostics, onSelect = onDiagnosticClick)
        } else {
            Text(output, color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CollapsedPeek(error: Boolean, file: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Surface, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (error) ErrorRed else Muted, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(file, color = TextMain, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("Tap to peek output", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FileTabs(file: String, dirty: Boolean, onSelect: (String) -> Unit, onDirty: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(color = Surface2, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.heightIn(min = 44.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(file, fontSize = 12.sp)
                if (dirty) Text(" •", color = Accent)
            }
        }
        IconButton(onClick = { onSelect("Untitled.kt"); onDirty() }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Add, "New file", tint = Accent)
        }
    }
}

@Composable
private fun CodeKeyRow(terminal: Boolean, onKey: (String) -> Unit) {
    val keys = if (terminal) listOf("Cursor", "Ctrl", "Esc", "Tab", "↑", "↓", "|", "~", "/", "-", "Ctrl+C")
        else listOf("Cursor", "Tab", "()", "{}", "[]", ";", ":", "\"", "=", "<", ">", "/", "_", "←", "→")
    Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        keys.forEach { key ->
            Surface(onClick = { onKey(if (key == "()" || key == "{}" || key == "[]") key.substring(0, 1) else key },
                color = Surface2, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(40.dp)) {
                Box(Modifier.widthIn(min = 38.dp).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                    Text(key, fontSize = 12.sp, color = if (key == "Cursor") Accent else TextMain)
                }
            }
        }
    }
}

@Composable
private fun CursorPad(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CURSOR & SELECTION", color = Accent, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("ABC") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Select", "Word", "Line", "Doc").forEach { TextButton(onClick = {}) { Text(it) } }
        }
        Box(Modifier.fillMaxWidth().height(72.dp).background(Surface2, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) { Text("Touchpad · drag to move cursor", color = Muted, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Cut", "Copy", "Paste", "All").forEach { TextButton(onClick = {}) { Text(it) } }
        }
    }
}
