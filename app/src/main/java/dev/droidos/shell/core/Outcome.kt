package dev.droidos.shell.core

/**
 * Result type used across repositories so a failing platform call surfaces to the
 * UI as a message instead of an unhandled exception or a silently dead control.
 */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Err(val message: String, val cause: Throwable? = null) : Outcome<Nothing>

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun valueOrNull(): T? = (this as? Ok)?.value

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }
}

inline fun <T> attempt(context: String, block: () -> T): Outcome<T> =
    try {
        Outcome.Ok(block())
    } catch (e: Exception) {
        AppLog.e("Outcome", "$context failed: ${e.javaClass.simpleName}: ${e.message}", e)
        Outcome.Err("${e.message ?: e.javaClass.simpleName}", e)
    }

inline fun <T> Outcome<T>.onError(action: (String) -> Unit): Outcome<T> {
    if (this is Outcome.Err) action(message)
    return this
}

inline fun <T, R> Outcome<T>.fold(onOk: (T) -> R, onErr: (String) -> R): R = when (this) {
    is Outcome.Ok -> onOk(value)
    is Outcome.Err -> onErr(message)
}
