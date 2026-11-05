package dev.droidos.shell.apps.convert

import java.util.Locale
import kotlin.math.abs

/**
 * Unit conversion arithmetic. Every category converts through one base unit, so adding a unit is
 * a single line and there is no matrix of pairwise factors to get wrong. Pure and unit-tested.
 */
object ConversionEngine {

    enum class Category(val label: String) {
        LENGTH("Length"), MASS("Mass"), TEMPERATURE("Temperature"),
        DATA("Data"), SPEED("Speed"), TIME("Time")
    }

    /**
     * [factor] and [offset] convert a value to the category's base unit: `base = value * factor + offset`.
     * Temperature uses the offset; every other category leaves it at zero.
     */
    data class Unit(val id: String, val label: String, val factor: Double, val offset: Double = 0.0)

    private val table: Map<Category, List<Unit>> = mapOf(
        Category.LENGTH to listOf(
            Unit("m", "Metre", 1.0),
            Unit("km", "Kilometre", 1_000.0),
            Unit("cm", "Centimetre", 0.01),
            Unit("mm", "Millimetre", 0.001),
            Unit("mi", "Mile", 1_609.344),
            Unit("yd", "Yard", 0.9144),
            Unit("ft", "Foot", 0.3048),
            Unit("in", "Inch", 0.0254),
            Unit("nmi", "Nautical mile", 1_852.0)
        ),
        Category.MASS to listOf(
            Unit("kg", "Kilogram", 1.0),
            Unit("g", "Gram", 0.001),
            Unit("mg", "Milligram", 0.000001),
            Unit("t", "Tonne", 1_000.0),
            Unit("lb", "Pound", 0.45359237),
            Unit("oz", "Ounce", 0.028349523125),
            Unit("st", "Stone", 6.35029318)
        ),
        Category.TEMPERATURE to listOf(
            Unit("c", "Celsius", 1.0),
            Unit("f", "Fahrenheit", 5.0 / 9.0, -32.0 * 5.0 / 9.0),
            Unit("k", "Kelvin", 1.0, -273.15)
        ),
        Category.DATA to listOf(
            Unit("b", "Byte", 1.0),
            Unit("kb", "Kilobyte", 1_000.0),
            Unit("kib", "Kibibyte", 1_024.0),
            Unit("mb", "Megabyte", 1_000_000.0),
            Unit("mib", "Mebibyte", 1_048_576.0),
            Unit("gb", "Gigabyte", 1_000_000_000.0),
            Unit("gib", "Gibibyte", 1_073_741_824.0),
            Unit("tb", "Terabyte", 1_000_000_000_000.0)
        ),
        Category.SPEED to listOf(
            Unit("mps", "Metre/second", 1.0),
            Unit("kmh", "Kilometre/hour", 1.0 / 3.6),
            Unit("mph", "Mile/hour", 0.44704),
            Unit("kn", "Knot", 0.514444444)
        ),
        Category.TIME to listOf(
            Unit("s", "Second", 1.0),
            Unit("ms", "Millisecond", 0.001),
            Unit("min", "Minute", 60.0),
            Unit("h", "Hour", 3_600.0),
            Unit("d", "Day", 86_400.0),
            Unit("wk", "Week", 604_800.0)
        )
    )

    fun units(category: Category): List<Unit> = table.getValue(category)

    fun defaultFrom(category: Category): Unit = units(category).first()

    fun defaultTo(category: Category): Unit = units(category).getOrElse(1) { units(category).first() }

    fun convert(value: Double, from: Unit, to: Unit): Double {
        val base = value * from.factor + from.offset
        return (base - to.offset) / to.factor
    }

    /** Enough significant digits to see the conversion move, without a wall of zeros. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "-"
        val magnitude = abs(value)
        val decimals = when {
            magnitude == 0.0 -> 0
            magnitude >= 1_000.0 -> 2
            magnitude >= 1.0 -> 4
            magnitude >= 0.001 -> 6
            else -> 9
        }
        var text = String.format(Locale.US, "%,.${decimals}f", value)
        if (text.contains('.')) {
            text = text.trimEnd('0').trimEnd('.')
        }
        return text
    }

    /** Parses the input field, tolerating grouped digits such as "1,200.5" and a leading plus. */
    fun parse(input: String): Double? = input
        .trim()
        .replace(",", "")
        .removePrefix("+")
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }
}
