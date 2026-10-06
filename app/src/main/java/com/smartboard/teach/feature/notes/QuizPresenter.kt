package com.smartboard.teach.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartboard.teach.R
import com.smartboard.teach.core.ui.theme.Accent
import com.smartboard.teach.core.ui.theme.ChromeDark
import com.smartboard.teach.core.ui.theme.StatusPresent
import com.smartboard.teach.core.ui.theme.TextOnChrome
import com.smartboard.teach.core.ui.theme.TextOnChromeMuted
import com.smartboard.teach.core.util.MathText
import com.smartboard.teach.domain.lessonpack.NcertPattern
import com.smartboard.teach.domain.model.AssignmentQuestion

/** The quiz order and the answer matching, kept pure so they can be tested. */
object QuizLogic {

    /** Questions in NCERT section order (A first), as the class would meet them. */
    fun ordered(questions: List<AssignmentQuestion>): List<AssignmentQuestion> {
        val order = NcertPattern.SECTIONS.map { it.letter }
        return questions.sortedBy { q -> order.indexOf(q.section).let { if (it < 0) order.size else it } }
    }

    /**
     * Which option the answer names. The AI writes it as a letter ("b",
     * "(b)", "b)"), the option's text, or both ("(b) 3/4"); null when it
     * matches no option, in which case the answer text is shown instead.
     */
    fun correctOption(q: AssignmentQuestion): Int? {
        if (q.options.isEmpty()) return null
        val answer = q.answer.trim()
        // Exact option text first, so an answer "a cube" is not read as option (a).
        q.options.indexOfFirst { it.trim().equals(answer, ignoreCase = true) }.let { if (it >= 0) return it }
        val letter = Regex("""^\(?([a-hA-H])\)?(?:[.)\s]|$)""").find(answer)?.groupValues?.get(1)
        if (letter != null) {
            val index = letter.lowercase()[0] - 'a'
            if (index in q.options.indices) return index
        }
        val text = answer.lowercase().replace(Regex("""^\(?[a-h]\)?[.)]?\s*"""), "")
        return q.options.indexOfFirst { it.trim().lowercase() == text }.takeIf { it >= 0 }
    }
}

/**
 * The lesson's questions presented to the class on the board, one at a time:
 * read it out, let the room answer, then Reveal. Big type, because it is read
 * from the back of a classroom.
 */
@Composable
fun QuizPresenter(questions: List<AssignmentQuestion>, onClose: () -> Unit) {
    val quiz = remember(questions) { QuizLogic.ordered(questions) }
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    if (quiz.isEmpty()) return
    val q = quiz[index]
    val correct = QuizLogic.correctOption(q)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(ChromeDark).padding(32.dp)) {
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 1100.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.quiz_progress, index + 1, quiz.size, pluralStringResource(R.plurals.quiz_marks, q.marks, q.marks)),
                        color = TextOnChromeMuted,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, stringResource(R.string.panel_close), tint = TextOnChrome)
                    }
                }
                Text(MathText.readable(q.text), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp)

                q.options.forEachIndexed { i, option ->
                    val isAnswer = revealed && i == correct
                    Text(
                        "(${'a' + i})  ${MathText.readable(option)}",
                        color = if (isAnswer) Color.White else TextOnChrome,
                        fontSize = 30.sp,
                        fontWeight = if (isAnswer) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth()
                            .background(if (isAnswer) StatusPresent else Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .border(1.dp, if (isAnswer) StatusPresent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }

                // An answer that names no option (or a written question) is shown in full.
                if (revealed && (correct == null) && q.answer.isNotBlank()) {
                    Text(
                        stringResource(R.string.quiz_answer, MathText.readable(q.answer)),
                        color = StatusPresent,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { index--; revealed = false }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        Text("  " + stringResource(R.string.quiz_previous), fontSize = 18.sp)
                    }
                    Button(onClick = { revealed = !revealed }, enabled = q.answer.isNotBlank() || correct != null) {
                        Text(stringResource(if (revealed) R.string.quiz_hide_answer else R.string.quiz_reveal), fontSize = 18.sp)
                    }
                    OutlinedButton(onClick = { index++; revealed = false }, enabled = index < quiz.lastIndex) {
                        Text(stringResource(R.string.quiz_next) + "  ", fontSize = 18.sp, color = Accent)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }
    }
}
