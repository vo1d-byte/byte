package dev.byteide.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

private val EditorBackground = Color(0xFF0B0F17)
private val EditorForeground = Color(0xFFE8EEF9)
private val EditorGutter = Color(0xFF718096)
private val EditorKeyword = Color(0xFFC792EA)
private val EditorString = Color(0xFFC3E88D)
private val EditorComment = Color(0xFF718096)
private val EditorNumber = Color(0xFFF78C6C)
private val EditorType = Color(0xFF82AAFF)

@Composable
fun ByteCodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val lineCount = value.text.count { it == '\n' } + 1
    val lineNumbers = (1..lineCount).joinToString("\n")

    Column(modifier.fillMaxWidth().background(EditorBackground)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("KOTLIN", color = Color(0xFF8BA8FF), fontSize = 10.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            Text("$lineCount lines", color = EditorGutter, fontSize = 10.sp)
        }
        Row(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 10.dp, bottom = 8.dp)
        ) {
            Text(
                text = lineNumbers,
                modifier = Modifier.widthIn(min = 34.dp).padding(end = 12.dp),
                color = EditorGutter,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 23.sp,
                softWrap = false
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).wrapContentHeight(unbounded = true),
                textStyle = TextStyle(
                    color = EditorForeground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 23.sp
                ),
                visualTransformation = KotlinSyntaxTransformation,
                maxLines = Int.MAX_VALUE,
                softWrap = false,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF8BA8FF))
            )
        }
    }
}

private object KotlinSyntaxTransformation : VisualTransformation {
    private val tokenPattern = Regex(
        """//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b(?:package|import|class|object|interface|fun|val|var|if|else|when|for|while|do|return|break|continue|this|super|is|in|as|try|catch|finally|throw|public|private|protected|internal|override|open|abstract|data|sealed|enum|companion|suspend|inline|lateinit|const|operator|infix|tailrec|where|typealias|by|null|true|false)\b|\b(?:Int|Long|Short|Byte|Float|Double|Boolean|Char|String|Unit|Any|Nothing)\b|\b\d+(?:\.\d+)?\b"""
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = buildAnnotatedString {
            append(text)
            tokenPattern.findAll(text.text).forEach { match ->
                val token = match.value
                val color = when {
                    token.startsWith("//") || token.startsWith("/*") -> EditorComment
                    token.startsWith('"') || token.startsWith("'") -> EditorString
                    token.firstOrNull()?.isDigit() == true -> EditorNumber
                    token in setOf("Int", "Long", "Short", "Byte", "Float", "Double",
                        "Boolean", "Char", "String", "Unit", "Any", "Nothing") -> EditorType
                    else -> EditorKeyword
                }
                addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
