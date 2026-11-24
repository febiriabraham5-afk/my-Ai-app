package com.example.ai

enum class AnswerMode(
    val label: String,
    val subtitle: String,
    val instructionSuffix: String
) {
    CONCISE(
        label = "Concise",
        subtitle = "Direct, crisp answer",
        instructionSuffix = "Give a sharp, direct answer in 2-4 sentences or a brief bullet list. Avoid fluff."
    ),
    BALANCED(
        label = "Balanced",
        subtitle = "Clear & well-structured",
        instructionSuffix = "Give a clear, well-structured answer with key takeaways, short headings or bullets when helpful, and practical clarity."
    ),
    DEEP_DIVE(
        label = "Deep Dive",
        subtitle = "Thorough breakdown",
        instructionSuffix = "Provide a comprehensive, nuanced explanation with underlying principles, concrete examples, edge cases, and structured sections."
    ),
    ELI5(
        label = "Simple",
        subtitle = "Plain-language intuition",
        instructionSuffix = "Explain this in warm, everyday plain language using intuitive analogies anyone can grasp without jargon."
    )
}

data class StarterPrompt(
    val category: String,
    val title: String,
    val question: String,
    val mode: AnswerMode = AnswerMode.BALANCED
)

val DEFAULT_STARTER_PROMPTS = listOf(
    StarterPrompt(
        category = "Science",
        title = "How do quantum computers actually beat classical ones?",
        question = "Explain how quantum computers work and what kinds of problems they solve faster than classical computers.",
        mode = AnswerMode.ELI5
    ),
    StarterPrompt(
        category = "Everyday Life",
        title = "Why does coffee wake you up — and cause an afternoon crash?",
        question = "What happens in the brain when you drink caffeine, and how can I avoid the afternoon crash?",
        mode = AnswerMode.BALANCED
    ),
    StarterPrompt(
        category = "Engineering",
        title = "Compare REST, GraphQL, and gRPC for mobile apps",
        question = "Compare REST, GraphQL, and gRPC for a modern mobile app, including trade-offs and when to pick each.",
        mode = AnswerMode.DEEP_DIVE
    ),
    StarterPrompt(
        category = "History",
        title = "What sparked the Renaissance in 14th-century Florence?",
        question = "Summarize the key cultural, economic, and political factors that sparked the Renaissance in Florence.",
        mode = AnswerMode.CONCISE
    )
)
