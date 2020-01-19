package infrastructure

import infrastructure.salesforce.ForceModel
import infrastructure.salesforce.ForceSecurity
import infrastructure.salesforce.ForceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader


private fun CoroutineScope.loadPrivateKeyFromResource(): String {
    val resourceAsStream = javaClass.classLoader.getResourceAsStream("key.pem")
    val bf = BufferedReader(InputStreamReader(resourceAsStream!!))
    return bf.readText()
}

fun main() = runBlocking {
    val force = ForceSettings(security = ForceSecurity.Jwt(privateKey = loadPrivateKeyFromResource()))
    val c = ForceModel.loadContact(force, "0033k00003GJlFJAA1")
    println(c)

    val updates =
            mapOf(
                    ForceModel.Contact::Email.name to "kvangapandu@capsulecares.com",
                    ForceModel.Contact::MobilePhone.name to "919-800-8032"
            )
    val res = ForceModel.updateContact(force, c.Id!!, updates)

    println(res)
}
