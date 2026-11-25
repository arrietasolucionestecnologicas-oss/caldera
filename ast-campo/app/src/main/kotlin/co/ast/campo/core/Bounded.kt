package co.ast.campo.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Espera como máximo [timeoutMs] el resultado de [block] (una consulta de red) y devuelve null si no llega a tiempo.
 * [block] corre en [scope], independiente de quien espera: aunque se deje de esperar, la consulta sigue y puede dejar
 * su resultado en caché para la siguiente foto. (Una llamada de red bloqueante no se puede interrumpir, por eso
 * no basta con withTimeout.) Cualquier excepción de [block] se trata como "sin dato".
 */
suspend fun <T : Any> awaitBounded(scope: CoroutineScope, timeoutMs: Long, block: suspend () -> T?): T? {
    val job = scope.async { runCatching { block() }.getOrNull() }
    return withTimeoutOrNull(timeoutMs) { job.await() }
}
