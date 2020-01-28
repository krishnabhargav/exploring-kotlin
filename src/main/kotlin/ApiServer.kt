import infrastructure.Json
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.http.Context
import io.javalin.plugin.json.FromJsonMapper
import io.javalin.plugin.json.JavalinJson
import io.javalin.plugin.json.ToJsonMapper
import io.javalin.plugin.openapi.OpenApiOptions
import io.javalin.plugin.openapi.OpenApiPlugin
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiResponse
import io.javalin.plugin.openapi.ui.SwaggerOptions
import io.swagger.v3.oas.models.info.Info


//javalin server with custom serializer, and openapi/swagger enabled.

private val toJsonMapper = object : ToJsonMapper {
    override fun map(obj: Any): String = Json.toJson(obj)
}

fun getOpenApiOptions(): OpenApiOptions =
        OpenApiOptions(Info().description("Example Swagger Setup"))
                .toJsonMapper(toJsonMapper)
                .path("/swagger-api")
                .swagger(SwaggerOptions("/swagger").title("Swagger API Documentation"))

fun main() {
    val app: Javalin = Javalin.create { config ->
        config.registerPlugin(OpenApiPlugin(getOpenApiOptions()))
    }.start(7000);

    JavalinJson.toJsonMapper = toJsonMapper
    JavalinJson.fromJsonMapper = object : FromJsonMapper {
        override fun <T> map(json: String, targetClass: Class<T>): T = Json.fromJsonWithClass(json, targetClass)
    }
    app.get("/", EmployeeController::hello)

    app.exception(Exception::class.java) { e, ctx ->
        e.printStackTrace()
    }
    app.routes {
        path("/employee") {
            get(EmployeeController::allEmployees)
            path("/:id") {
                get(EmployeeController::byId)
            }
        }
    }
}

typealias Res = OpenApiResponse
typealias Cont = OpenApiContent

object EmployeeController {

    sealed class Device {
        data class Laptop(val model: String) : Device()
        data class Phone(val model: String, val carrier: String) : Device()
    }

    class Employee(val id: Int, val device: List<Device>)

    private val employees = listOf(
            Employee(1, listOf(Device.Laptop("Macbook Pro"), Device.Phone("IPhone", "T-Mobile"))),
            Employee(2, listOf(Device.Laptop("Macbook Pro"), Device.Phone("IPhone", "T-Mobile")))
    )

    @OpenApi(responses = [Res("200", [Cont(Employee::class, true, "application/json")])])
    fun allEmployees(ctx: Context) {
        ctx.json(employees)
    }

    @OpenApi(responses = [Res("200", [Cont(String::class)])])
    fun hello(ctx: Context) {
        ctx.result("hello")
    }

    fun byId(ctx: Context) {
        val result = when (ctx.pathParam("id").toInt()) {
            1 -> employees[0]
            2 -> employees[1]
            else -> throw Exception("Invalid id")
        }
        ctx.json(result)
    }

}