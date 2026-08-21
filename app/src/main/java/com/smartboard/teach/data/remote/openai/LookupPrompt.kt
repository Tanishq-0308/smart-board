package com.smartboard.teach.data.remote.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Prompt and response schema for explaining a lassoed board region.
 */
object LookupPrompt {

    /**
     * A stronger model than the notes flow uses.
     *
     * Notes run every few minutes all day, so cost dominates there. A lookup
     * is a deliberate, occasional action where the teacher is WAITING and a
     * wrong answer gets said out loud to a class. Accuracy on handwriting is
     * worth the higher per-call cost at this frequency.
     */
    const val DEFAULT_MODEL = "gpt-4o"

    /** Short by design: the panel is read at a glance, not studied. */
    const val MAX_TOKENS = 700

    /**
     * The critical instruction is the unreadable escape hatch. A model asked
     * "what is this" about an illegible scrawl will confabulate something
     * plausible; a teacher then repeats it to thirty students. Being able to
     * answer "I cannot read this" must be an explicitly allowed outcome.
     */
    val SYSTEM_PROMPT = """
        You explain a region a teacher has circled on a classroom whiteboard.
        The image is a crop of handwritten board content, not a photograph of
        a real-world object.

        Rules:
        - First read the region literally. Put exactly what is written in
          `transcription`, preserving notation, subscripts and symbols.
        - Then explain what it means, at the level of the class it was drawn
          for. Two or three sentences, no preamble.
        - NEVER invent content that is not visible. If the region is blank,
          illegible, or too ambiguous to identify, set `isUnreadable` to true
          and say plainly what you could and could not make out.
        - `searchQuery` must be the query you would type to learn more about
          this topic — use correct technical terminology even when the board
          uses shorthand or abbreviations.
        - Keep `title` short enough to fit a panel heading.
    """.trimIndent()

    const val USER_PROMPT =
        "Explain the region of the whiteboard shown in this image."

    /**
     * Strict JSON schema. `additionalProperties: false` and a complete
     * `required` list are both mandatory for OpenAI strict mode.
     */
    fun schema(json: Json): JsonObject = json.parseToJsonElement(
        """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["title", "kind", "explanation", "transcription", "relatedTerms", "searchQuery", "isUnreadable"],
          "properties": {
            "title": {
              "type": "string",
              "description": "Short label for the circled region."
            },
            "kind": {
              "type": "string",
              "enum": ["TEXT", "EQUATION", "DIAGRAM", "CHEMISTRY", "GEOMETRY", "OTHER"],
              "description": "What kind of content the region holds."
            },
            "explanation": {
              "type": "string",
              "description": "Two or three sentences explaining the region."
            },
            "transcription": {
              "type": "string",
              "description": "Exactly what is written in the region, verbatim."
            },
            "relatedTerms": {
              "type": "array",
              "items": { "type": "string" },
              "description": "Key terms worth expanding on."
            },
            "searchQuery": {
              "type": "string",
              "description": "Best web search query to learn more about this."
            },
            "isUnreadable": {
              "type": "boolean",
              "description": "True when the region could not be read confidently."
            }
          }
        }
        """.trimIndent(),
    ) as JsonObject
}
