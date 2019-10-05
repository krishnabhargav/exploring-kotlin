import io.javalin.Javalin
import io.javalin.plugin.metrics.MicrometerPlugin
import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future

fun main() {
    val app = Javalin.create { config ->
        config.registerPlugin(MicrometerPlugin())
    }
    app.start(7000)

    //by default a global registry gets used by MicrometerPlugin
    //so lets add prometheus registry
    val promRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    Metrics.globalRegistry.add(promRegistry)

    app.before { ctx ->
        Metrics.counter("requests/${ctx.path()}").increment()
    }
    app.get("/delayed") {
        it.result(GlobalScope.future {
            delay(100)
            return@future "Hello, World!"
        })
    }
    app.get("/prometheus") { ctx -> ctx.result(promRegistry.scrape()) }
}
