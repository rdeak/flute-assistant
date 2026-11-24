// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import kotlin.math.roundToInt

/**
 * One rule of a rules value [R], written once: its [name] in a session's `session.rules` line, its value as that line
 * has it ([text]) and how a replay [set]s it from a number. Made by [numberRule], [countRule], [levelRule] or
 * [flagRule], which say how the rule's own type is written and how a number becomes it.
 */
internal class RuleField<R>(val name: String, val text: (R) -> String, val set: (R, Double) -> R)

/** A rule that is a number, taken as given and written as Kotlin prints it. */
internal fun <R> numberRule(name: String, read: (R) -> Double, set: (R, Double) -> R) =
    RuleField(name, { read(it).toString() }, set)

/** A rule that is a count of frames or notes: a replay's number is rounded. */
internal fun <R> countRule(name: String, read: (R) -> Int, set: (R, Int) -> R) =
    RuleField<R>(name, { read(it).toString() }) { rules, value -> set(rules, value.roundToInt()) }

/** A rule on the level scale, a float. */
internal fun <R> levelRule(name: String, read: (R) -> Float, set: (R, Float) -> R) =
    RuleField<R>(name, { read(it).toString() }) { rules, value -> set(rules, value.toFloat()) }

/** A rule that is a yes or a no: 1 or 0 in a session's line, and any number but 0 is a yes. */
internal fun <R> flagRule(name: String, read: (R) -> Boolean, set: (R, Boolean) -> R) =
    RuleField<R>(name, { if (read(it)) "1" else "0" }) { rules, value -> set(rules, value != 0.0) }

/**
 * Every rule of a rules value [R] (the tracker's [NoteOnsetTracker.Rules], the run's `ListenRun.Rules`), in the order a
 * session's line has them. A rule is listed here once, and from that the value renders itself ([fields]) and a replay
 * moves one rule by its name ([with]).
 */
internal class RuleTable<R>(private vararg val rules: RuleField<R>) {
    /** The names [with] knows. */
    val names: Set<String> = rules.map { it.name }.toSet()

    /** Name and value of every rule of [of], in order: a number as Kotlin prints it, a yes as 1 and a no as 0. */
    fun fields(of: R): List<Pair<String, String>> = rules.map { it.name to it.text(of) }

    /** [of] with the rule named [name] set to [value]; an unknown name is refused. */
    fun with(of: R, name: String, value: Double): R =
        requireNotNull(rules.firstOrNull { it.name == name }) { "no rule named $name" }.set(of, value)
}
