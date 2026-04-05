package com.quantumbox.gemma4poc.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PocTools : ToolSet {

    @Tool(description = "Get the current date and time.")
    fun getCurrentDateTime(): Map<String, String> {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return mapOf(
            "status" to "succeeded",
            "datetime" to now.format(formatter),
        )
    }

    @Tool(description = "Calculate a mathematical expression. Supports basic operations: +, -, *, /")
    fun calculate(
        @ToolParam(description = "The mathematical expression to evaluate, e.g. '2 + 3 * 4'")
        expression: String,
    ): Map<String, String> {
        return try {
            val result = evaluateExpression(expression)
            mapOf(
                "status" to "succeeded",
                "expression" to expression,
                "result" to result.toString(),
            )
        } catch (e: Exception) {
            mapOf(
                "status" to "failed",
                "error" to (e.message ?: "Invalid expression"),
            )
        }
    }

    @Tool(description = "Convert temperature between Celsius and Fahrenheit.")
    fun convertTemperature(
        @ToolParam(description = "The temperature value to convert") value: Double,
        @ToolParam(description = "The unit to convert from: 'C' for Celsius, 'F' for Fahrenheit") fromUnit: String,
    ): Map<String, String> {
        val (result, toUnit) = when (fromUnit.uppercase()) {
            "C" -> Pair(value * 9.0 / 5.0 + 32.0, "F")
            "F" -> Pair((value - 32.0) * 5.0 / 9.0, "C")
            else -> return mapOf("status" to "failed", "error" to "Unknown unit: $fromUnit")
        }
        return mapOf(
            "status" to "succeeded",
            "input" to "$value°$fromUnit",
            "result" to "%.1f°$toUnit".format(result),
        )
    }

    private fun evaluateExpression(expr: String): Double {
        val tokens = tokenize(expr.trim())
        return parseAddSub(tokens.iterator())
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            when {
                expr[i].isWhitespace() -> i++
                expr[i] in "+-*/()" -> {
                    tokens.add(expr[i].toString())
                    i++
                }
                expr[i].isDigit() || expr[i] == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                else -> throw IllegalArgumentException("Unexpected character: ${expr[i]}")
            }
        }
        return tokens
    }

    private fun parseAddSub(tokens: Iterator<String>): Double {
        val peekable = tokens as? PeekableIterator ?: PeekableIterator(tokens)
        var result = parseMulDiv(peekable)
        while (peekable.hasNext() && peekable.peek() in listOf("+", "-")) {
            val op = peekable.next()
            val right = parseMulDiv(peekable)
            result = if (op == "+") result + right else result - right
        }
        return result
    }

    private fun parseMulDiv(tokens: PeekableIterator): Double {
        var result = parsePrimary(tokens)
        while (tokens.hasNext() && tokens.peek() in listOf("*", "/")) {
            val op = tokens.next()
            val right = parsePrimary(tokens)
            result = if (op == "*") result * right else result / right
        }
        return result
    }

    private fun parsePrimary(tokens: PeekableIterator): Double {
        if (!tokens.hasNext()) throw IllegalArgumentException("Unexpected end of expression")
        val token = tokens.peek()
        return if (token == "(") {
            tokens.next() // consume '('
            val result = parseAddSub(tokens)
            if (tokens.hasNext() && tokens.peek() == ")") tokens.next()
            result
        } else {
            tokens.next().toDouble()
        }
    }

    private class PeekableIterator(private val inner: Iterator<String>) : Iterator<String> {
        private var peeked: String? = null
        private var hasPeeked = false

        fun peek(): String {
            if (!hasPeeked) {
                peeked = inner.next()
                hasPeeked = true
            }
            return peeked!!
        }

        override fun hasNext(): Boolean = hasPeeked || inner.hasNext()

        override fun next(): String {
            if (hasPeeked) {
                hasPeeked = false
                return peeked!!
            }
            return inner.next()
        }
    }
}
