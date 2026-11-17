// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

/**
 * One rule of a rules value [R], written once: its [name] in a session's `session.rules` line, how its value is [read]
 * for that line and how a replay [set]s it from a number. Made by [numberRule], [countRule], [levelRule] or [flagRule],
 * which say how the number becomes the rule's own type.
 */
internal class RuleField<R>(val name: String, val read: (R) -> Any, val set: (R, Double) -> R)

/** A rule that is a number, taken as given. */
internal fun <R> numberRule(name: String, read: (R) -> Double, set: (R, Double) -> R) = RuleField(name, read, set)

/** A rule that is a count of frames or notes: a replay's number is rounded. */
internal fun <R> countRule(name: String, read: (R) -> Int, set: (R, Int) -> R) =
    RuleField(name, read) { rules, value -> set(rules, Math.round(value).toInt()) }

/** A rule on the level scale, a float. */
internal fun <R> levelRule(name: String, read: (R) -> Float, set: (R, Float) -> R) =
    RuleField(name, read) { rules, value -> set(rules, value.toFloat()) }

/** A rule that is a yes or a no: 1 or 0 in a session's line, and any number but 0 is a yes. */
internal fun <R> flagRule(name: String, read: (R) -> Boolean, set: (R, Boolean) -> R) =
    RuleField(name, read) { rules, value -> set(rules, value != 0.0) }

/**
 * Every rule of a rules value [R] (the tracker's [NoteOnsetTracker.Rules], the run's `ListenRun.Rules`), in the order a
 * session's line has them. A rule is listed here once, and from that the value renders itself ([fields]) and a replay
 * moves one rule by its name ([with]).
 */
internal class RuleTable<R>(private vararg val rules: RuleField<R>) {
    /** The names [with] knows. */
    val names: Set<String> = rules.map { it.name }.toSet()

    /** Name and value of every rule of [of], in order: a number as Kotlin prints it, a yes as 1 and a no as 0. */
    fun fields(of: R): List<Pair<String, String>> = rules.map { rule ->
        rule.name to rule.read(of).let { if (it is Boolean) (if (it) "1" else "0") else it.toString() }
    }

    /** [of] with the rule named [name] set to [value]; an unknown name is refused. */
    fun with(of: R, name: String, value: Double): R =
        (rules.firstOrNull { it.name == name } ?: throw IllegalArgumentException("no rule named $name")).set(of, value)
}
