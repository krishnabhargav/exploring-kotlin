import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.Future

val executor = Executors.newFixedThreadPool(2)

fun futureExample(futureId: Int, sleepTime: Long): Future<Int> {
    return executor.submit<Int> {
        println("Future waiting. Id=$futureId")
        Thread.sleep(sleepTime)
        println("Future done. Id=$futureId")
        futureId
    }
}

/**
 * This function converts a java.util.concurrent.Future into Deferred so that we can integrate it into coroutines.
 * The implementation is nonblocking as we spin and check if the future was completed or cancelled
 * if the deferred itself is cancelled or the future is cancelled, we will throw cancellation exception
 */
fun <T> Future<T>.asAsync(waitTime: Long = 10): Deferred<T?> {
    val x = this
    return GlobalScope.async {
        while (isActive && !x.isDone && !x.isCancelled)
            delay(waitTime)
        if (isActive && x.isDone) x.get()
        else throw CancellationException()
    }
}

fun main() = runBlocking {
    val futures = (0..200).map { futureExample(it, 2000).asAsync(10) }.toList()
    futures.forEach { it.await() }
    executor.shutdown()
}