package com.vse.launcher.util

import kotlin.math.*

/**
 * Небольшой вычислитель выражений: + - * / ^ %, скобки, унарный минус,
 * функции sqrt sin cos tan ln log abs, константы pi и e.
 * Понимает символы × ÷ − √ π и запятую как десятичный разделитель.
 */
object Calc {
    fun eval(input: String): Double? {
        val src = input
            .replace('×', '*').replace('÷', '/').replace('−', '-')
            .replace(',', '.').replace("π", "pi").replace("√", "sqrt")
            .replace(" ", "").lowercase()
        if (src.isEmpty()) return null
        return runCatching {
            val p = Parser(src)
            val v = p.parseExpr()
            if (p.pos != src.length) null else v
        }.getOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    /** Похоже ли выражение на математику (чтобы не показывать результат для "1"). */
    fun looksLikeMath(input: String): Boolean {
        val s = input.trim()
        if (s.isEmpty()) return false
        val hasOp = s.drop(1).any { it in "+-*/^%×÷−" } || s.contains("sqrt") || s.contains("√") ||
            s.contains("sin") || s.contains("cos") || s.contains("tan") || s.contains("log") || s.contains("ln")
        return hasOp && s.any { it.isDigit() }
    }

    fun format(v: Double): String {
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        val s = "%.10f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
        return if (s.length > 16) "%.6e".format(java.util.Locale.US, v) else s
    }

    private class Parser(val s: String) {
        var pos = 0

        fun parseExpr(): Double {
            var v = parseTerm()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> return v
                }
            }
            return v
        }

        fun parseTerm(): Double {
            var v = parseFactor()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; v *= parseFactor() }
                    '/' -> { pos++; v /= parseFactor() }
                    '%' -> {
                        pos++
                        // "50%" в конце или перед оператором — процент, иначе остаток от деления
                        if (pos >= s.length || s[pos] in "+-*/)") v /= 100.0 else v %= parseFactor()
                    }
                    else -> return v
                }
            }
            return v
        }

        fun parseFactor(): Double {
            val base = parseUnary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                return base.pow(parseFactor())
            }
            return base
        }

        fun parseUnary(): Double {
            if (pos < s.length && s[pos] == '-') { pos++; return -parseUnary() }
            if (pos < s.length && s[pos] == '+') { pos++; return parseUnary() }
            return parsePrimary()
        }

        fun parsePrimary(): Double {
            if (pos >= s.length) error("eof")
            val c = s[pos]
            if (c == '(') {
                pos++
                val v = parseExpr()
                if (pos < s.length && s[pos] == ')') pos++
                return v
            }
            if (c.isDigit() || c == '.') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                return s.substring(start, pos).toDouble()
            }
            if (c.isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                val name = s.substring(start, pos)
                return when (name) {
                    "pi" -> PI
                    "e" -> E
                    else -> {
                        val arg = parseUnary()
                        when (name) {
                            "sqrt" -> sqrt(arg)
                            "sin" -> sin(Math.toRadians(arg))
                            "cos" -> cos(Math.toRadians(arg))
                            "tan" -> tan(Math.toRadians(arg))
                            "ln" -> ln(arg)
                            "log" -> log10(arg)
                            "abs" -> abs(arg)
                            else -> error("unknown $name")
                        }
                    }
                }
            }
            error("bad char $c")
        }
    }
}
