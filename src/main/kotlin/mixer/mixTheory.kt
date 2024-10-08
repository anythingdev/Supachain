package dev.supachain.mixer

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.absoluteValue

/*
░░░░░░░░░░░░░  ░░░░  ░░        ░░  ░░░░  ░░░░░░░░        ░░  ░░░░  ░░        ░░░      ░░░       ░░░  ░░░░  ░░░░░░░░░░░░░
▒▒▒▒▒▒▒▒▒▒▒▒▒   ▒▒   ▒▒▒▒▒  ▒▒▒▒▒▒  ▒▒  ▒▒▒▒▒▒▒▒▒▒▒▒  ▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒▒▒▒▒  ▒▒▒▒  ▒▒  ▒▒▒▒  ▒▒▒  ▒▒  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒
▓▓▓▓▓▓▓▓▓▓▓▓▓        ▓▓▓▓▓  ▓▓▓▓▓▓▓    ▓▓▓▓▓▓▓▓▓▓▓▓▓  ▓▓▓▓▓        ▓▓      ▓▓▓▓  ▓▓▓▓  ▓▓       ▓▓▓▓▓    ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
█████████████  █  █  █████  ██████  ██  ████████████  █████  ████  ██  ████████  ████  ██  ███  ██████  ████████████████
█████████████  ████  ██        ██  ████  ███████████  █████  ████  ██        ███      ███  ████  █████  ████████████████
*/

/**
 * Represents a single data pseudo container for a value of type [T].
 *
 * @param T The type of the value.
 * @property value The encapsulated value.
 */
@JvmInline
value class Single<T>(val value: T) {
    val input: T get() = value
}

/**
 * Represents a pseudo container for multiple data values of type [T].
 *
 * @param T The type of the values in the list.
 * @property data The list of values.
 * @property input Returns the first value in the list. The initial input
 * @property outputs Returns all values in the list except the first one. The transformations of input
 */
@JvmInline
value class Multi<T>(val data: List<T>) {
    val input: T get() = data.first()
    val outputs: List<T> get() = if (data.size < 2) emptyList() else data.subList(1, data.size)
}

// Mix Functions
/** Alias for a function that operates on a [Single] value of type [T] and returns a result of type [T]. */
typealias MixFunction<T> = Single<T>.() -> T
/** Alias for a function that operates on a [Multi] value of type [T] and returns a result of type [T]. */
typealias RemixFunction<T> = Multi<T>.() -> T

/**
 * Creates a [Remix] function that can be repeated a specified number of times.
 *
 * @param count The number of times the remix function should be repeated.
 * @return A [Remix] instance with the updated repeat count.
 */
operator fun <T> RemixFunction<T>.times(count: Int) = Remix(Pair(this, count))

/**
 * Represents a combination of mixable items and a remix function.
 */
typealias MultiMix<T> = Pair<List<Mixable<T>>, Remix<T>>


/**
 * Extension property that returns the list of mixable items.
 */
val <T> MultiMix<T>.mixers get() = first

/** Extension property that returns the remix function.*/
val <T> MultiMix<T>.remix get() = second

/**
 * Converts this value to a [Single] instance.
 */
fun <T> T.asSingle() = Single(this)

/**
 * Converts this list of values to a [Multi] instance.
 */
fun <T> List<T>.asMulti() = Multi(this)


/**
 * The base interface for all mixable entities.
 * Represents an entity that can be mixed with data of type [T].
 *
 * @param T The type of the input data.
 */
interface Mixable<T> {
    /**
     * Mixes the input data using the provided generate function.
     *
     * @param inputData The input data to be mixed.
     * @param generator Generates the mixed result.
     * @return The mixed result.
     */
    suspend fun mix(inputData: T, generator: Generator<T>): T

    /**
     * Combines this mixable with another mixable into an unmixed [Group].
     */
    operator fun plus(mixable: Mixable<T>) = Group(listOf(this, mixable))

    /**
     * Combines this mixable with another mixable into a track of sequential mixes.
     */
    operator fun times(mixable: Mixable<T>): Mixable<T> = Track(listOf(this, mixable))

    /**
     * Repeats this mixable a specified number of times.
     */
    operator fun times(count: Int): Mixable<T> = Repeater(Pair(this, count))

    /**
     * Connects this mixable to a remix function.
     */
    infix fun to(remixFunction: Remix<T>): Mixable<T> = MultiTrack(MultiMix(listOf(this), remixFunction))

    /**
     * Uses the specified generator for mixing.
     */
    infix fun with(generator: Generator<T>) = Mixer(Pair(this, generator))
}


/**
 * Represents a remix function that can be repeated a specified number of times.
 *
 * @param data The function and repeat count for the remix.
 */
@JvmInline
value class Remix<T>(val data: Pair<Multi<T>.() -> T, Int>) {
    /** Creates a [Remix] function that repeats the specified number of times.
     *
     * @param count The number of repetitions.
     * @return A [Remix] with the updated repeat count.
     */
    operator fun times(count: Int) = Remix(Pair(data.first, count))
    private val function: Multi<T>.() -> T get() = data.first
    private val repeatCount: Int get() = data.second - 1

    /**
     * Applies the remix function to the given [Multi] instance.
     *
     * @param multi The input [Multi] instance.
     * @return The remixed result.
     */
    fun mix(multi: Multi<T>): T {
        var result = function(multi)
        repeat(repeatCount) {
            result = function(Multi(listOf(result)))
        }
        return result
    }
}

/**
 * Represents an effect that applies a mix function to a value of type [T].
 *
 * @param instruction The mix function.
 */
@JvmInline
value class Effect<T>(val instruction: MixFunction<T>) : Mixable<T> {
    /**
     * Applies the mix function and generates the result.
     */
    override suspend fun mix(inputData: T, generator: Generator<T>): T {
        val sample = instruction(inputData.asSingle())
        return generator.generate(sample)
    }

    override fun toString(): String = "Effect${instruction.hashCode().absoluteValue % 10000}"
}


/**
 * Represents a mixable entity that repeats its mix operation a specified number of times.
 *
 * @param data A pair of the mixable and the repeat count.
 */
@JvmInline
value class Repeater<T>(val data: Pair<Mixable<T>, Int>) : Mixable<T> {
    private val mixable get() = data.first
    private val repeatCount get() = data.second

    /**
     * Repeats the mix operation that repeats with [repeatCount]
     */
    override suspend fun mix(inputData: T, generator: Generator<T>): T {
        var result: T = inputData
        repeat(repeatCount) { result = mixable.mix(result, generator) }
        return result
    }

    override fun toString(): String = "$mixable -> |$repeatCount| $mixable"
}

/**
 * Represents a group of unmixed mixable items.
 */
@JvmInline
value class Group<T>(val data: List<Mixable<T>>) : List<Mixable<T>> by data {
    /**
     * Adds a mixable to the group.
     */
    operator fun plus(mixable: Mixable<T>) = Group(data + mixable)

    /**
     * Connects this group to a remix function.
     */
    infix fun to(remix: Remix<T>): Mixable<T> = MultiTrack(MultiMix(data, remix))
}

/**
 * Represents a sequential track of mixable items.
 */
@JvmInline
value class Track<T>(private val mixes: List<Mixable<T>>) : Mixable<T> {
    /**
     * Combines this track with another mixable item.
     */
    override operator fun times(mixable: Mixable<T>) = Track(mixes + mixable)

    /**
     * Mixes the data by applying all mixable items in the track sequentially.
     */
    override suspend fun mix(inputData: T, generator: Generator<T>): T {
        var lastInput = inputData
        for (mix in mixes) {
            lastInput = mix.mix(lastInput, generator)
        }

        return lastInput
    }

    override fun toString(): String {
        return mixes.joinToString(" --> ")
    }
}

/**
 * Represents a multi-track mixable item that combines multiple mixable items with a remix function.
 */
@JvmInline
value class MultiTrack<T>(private val multiMix: MultiMix<T>) : Mixable<T> {
    private suspend fun makeMixes(inputData: T, generator: Generator<T>): Multi<T> =
        if (generator.parallelExecutionAllowed)
            coroutineScope {
                listOf(inputData) + multiMix.mixers.map { mixer ->
                    async { mixer.mix(inputData, generator) }
                }.awaitAll()
            }.asMulti()
        else (listOf(inputData) + multiMix.mixers.map { it.mix(inputData, generator) }).asMulti()

    /**
     * Mixes the input data and applies the remix function.
     */
    override suspend fun mix(inputData: T, generator: Generator<T>): T {
        val mixes = makeMixes(inputData, generator)
        val remix = multiMix.remix.mix(mixes)
        return generator.generate(remix)
    }

    override fun toString(): String =
        "${multiMix.mixers.joinToString(" & ")} --> Remix${multiMix.remix.hashCode().absoluteValue % 10000}"
}

/**
 * Represents a mixer that applies a generator function to a mixable item.
 *
 * @param data A pair of the mixable item and the generator function.
 */
@JvmInline
value class Mixer<T>(val data: Pair<Mixable<T>, Generator<T>>) {
    constructor(track: Mixable<T>, generator: Generator<T>) : this(Pair(track, generator))

    private val mixable get() = data.first
    private val generator get() = data.second

    /**
     * Invokes the mixer on the input data.
     *
     * @param input The input data.
     * @return The mixed result.
     */
    suspend operator fun invoke(input: T) = mixable.mix(input, generator)
}

/**
 * Represents a generator that produces data of type [T].
 */
interface Generator<T> {
    val parallelExecutionAllowed: Boolean
    fun generate(input: T): T
}

/**
 * Creates a new [Mixable] effect with the given mix function.
 */
fun <T> concept(fn: MixFunction<T>): Mixable<T> = Effect(fn)

/**
 * Creates a new remix function with a repeat count of 1.
 */
fun <T> mix(fn: RemixFunction<T>) = Remix(Pair(fn, 1))

