package com.smartboard.teach.data.remote.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Prompt and response schema for turning a board snapshot into lesson notes.
 */
object NotesPrompt {

    /**
     * Vision-capable and cheap enough to run every few minutes throughout a
     * teaching day, which is the realistic usage pattern for a classroom
     * board. A stronger model can be swapped in behind Settings if
     * handwriting transcription proves insufficient on real boards.
     */
    const val DEFAULT_MODEL = "gpt-4o-mini"

    const val MAX_TOKENS = 1500

    /**
     * The instruction not to invent content is the important line here.
     * A teacher will paste these notes to students; a plausible-sounding
     * hallucinated definition is worse than a short, accurate note.
     */
    val SYSTEM_PROMPT = """
        You are a teaching assistant that converts photographs of classroom
        whiteboards into clean, structured lesson notes.

        Rules:
        - Transcribe text, equations and diagrams labels faithfully.
        - Never invent content that is not visible on the board.
        - Preserve the teacher's terminology and notation.
        - If the board is blank, illegible, or contains no lesson content, say
          so plainly in the summary and leave the other fields empty.
        - Keep the title short and specific to what is on the board.
    """.trimIndent()

    const val USER_PROMPT =
        "Convert this classroom whiteboard into structured lesson notes."

    /**
     * Strict JSON schema. `additionalProperties: false` and a complete
     * `required` list are both mandatory for OpenAI strict mode — omitting
     * either makes the API reject the request rather than the model.
     */
    fun schema(json: Json): JsonObject = json.parseToJsonElement(
        """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["title", "summary", "topics", "keyPoints", "definitions", "formulas", "followUpQuestions"],
          "properties": {
            "title": {
              "type": "string",
              "description": "Short, specific title for this board."
            },
            "summary": {
              "type": "string",
              "description": "Two or three sentences describing what was taught."
            },
            "topics": {
              "type": "array",
              "items": { "type": "string" },
              "description": "Topics covered."
            },
            "keyPoints": {
              "type": "array",
              "items": { "type": "string" },
              "description": "The main points a student should take away."
            },
            "definitions": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["term", "meaning"],
                "properties": {
                  "term": { "type": "string" },
                  "meaning": { "type": "string" }
                }
              },
              "description": "Terms defined on the board."
            },
            "formulas": {
              "type": "array",
              "items": { "type": "string" },
              "description": "Formulas or equations exactly as written."
            },
            "followUpQuestions": {
              "type": "array",
              "items": { "type": "string" },
              "description": "Practice questions arising from this board."
            }
          }
        }
        """.trimIndent(),
    ) as JsonObject
}
