package co.ast.campo.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class BoundedTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Test fun returnsTheValueWhenItArrivesInTime() = runBlocking {
        assertEquals("ok", awaitBounded(scope, 2_000) { "ok" })
    }

    @Test fun returnsNullQuicklyWhenTheCallIsTooSlowAndLetsItFinishInTheBackground() = runBlocking {
        val finished = AtomicBoolean(false)
        val start = System.nanoTime()
        val r = awaitBounded(scope, 100) { Thread.sleep(600); finished.set(true); "tarde" } // bloqueante, como una llamada de red
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertNull(r)
        assertTrue("esperó ${elapsedMs} ms", elapsedMs < 400)
        delay(800)
        assertTrue("la consulta debía terminar sola", finished.get())
    }

    @Test fun exceptionsBecomeNoData() = runBlocking {
        assertNull(awaitBounded<String>(scope, 1_000) { error("sin internet") })
    }
}
