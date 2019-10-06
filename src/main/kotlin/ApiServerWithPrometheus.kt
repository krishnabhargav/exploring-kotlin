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
    //docker run -e SERVER_PORT='8000' -p 8000:8000 capsulecares/exploring-kotlin:latest
    val portNumber = System.getenv("SERVER_PORT")?.toInt() ?: 7000
    println("Reading port from environment: $portNumber")
    app.start(portNumber)

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
