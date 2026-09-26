package com.offlinestudy.solver.llm

/** Section 11: the answer-length/style modes the user can pick before generating. */
enum class AnswerMode(val label: String, val instruction: String) {
    SHORT("Short", "Give a brief 1-2 sentence definition-style answer."),
    EXAM("Exam", "Give a concise, exam-ready answer suitable for a written exam."),
    TWO_MARKS("2 Marks", "Give a short factual answer appropriate for a 2-mark question."),
    FIVE_MARKS("5 Marks", "Give a structured answer covering the important points, appropriate for a 5-mark question."),
    TEN_MARKS("10 Marks", "Give a detailed answer with headings and explanations, appropriate for a 10-mark question."),
    SCIENTIFIC("Scientific", "Use precise scientific terminology and formal definitions."),
    MATERIAL_ONLY("Material Only", "Answer strictly and only using the supplied reference material; do not add outside context.")
}
