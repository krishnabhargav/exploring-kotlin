package infrastructure.salesforce

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.capsule.atlas.Outputs
import com.capsule.atlas.WriteResult
import com.capsule.atlas.models.*
import com.capsule.atlas.utils.Json
import infrastructure.asRest
import kotlinx.coroutines.runBlocking
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*

sealed class ForceSecurity {
    abstract val authLookup: AuthLookup

    data class UsernamePassword(
            val clientId: String = "<consumer key>",
            val email: String = "krishnbhargava@gmail.com",
            val password: String = "<password.token>",
            val clientSecret: String = "<client secret>") : ForceSecurity() {
        private suspend fun fetchAccessToken(): String {
            println("Fetching OAuth Access Token")
            val oauth = "rest://sales-login/services/oauth2/token?grant_type=password&client_id=$clientId&" +
                    "username=$email&password=$password&client_secret=$clientSecret&rest_operation=post"

            val sd = StreamDefinition.parse(oauth, { _ -> "https://login.salesforce.com" }, { _ -> Auth.None })
            return (Outputs.writeWithRetries(sd, DomainEvent.empty()).flatMap {
                when (val x = it) {
                    is WriteResult.Rest -> Json.tryGetFromPathT<String>(x.body, "$.access_token")
                    else -> throw Exception("Invalid state: cannot fetch access token for non rest results")
                }
            }).getOrThrow() ?: ""
        }

        private val token = lazy { runBlocking { fetchAccessToken() } }
        override val authLookup: AuthLookup = { _ -> Auth.OAuth { token.value } }
    }

    //refer to: http://blog.deadlypenguin.com/blog/2019/03/08/jwt-bearer-auth-salesforce-node/
    data class Jwt(
            //clientid/consumer key
            val iss: String = "<consumer key>",
            val sub: String = "krishnbhargava@gmail.com",
            val aud: String = "https://login.salesforce.com",
            private val privateKey: String
    ) : ForceSecurity() {
        private suspend fun fetchAccessToken(): String {
            val jwt = jwtAssertion()
            val oauth = "rest://sales-login/services/oauth2/token?" +
                    "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" +
                    "&assertion=$jwt&rest_operation=post"
            val sd = StreamDefinition.parse(oauth, { _ -> "https://login.salesforce.com" }, { _ -> Auth.None })
            val res = Outputs.writeWithRetries(sd, DomainEvent.empty()).asRest()
            return Json.tryGetFromPathT<String>(res.body, "\$.access_token").getOrThrow()
                    ?: throw Exception("unable to get access token")
        }

        private fun jwtAssertion(): String {
            //PemReader
            val decodedBytes =
                    privateKey.replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "").replace("\\s+", "")
                            .replace("\n", "")
                            .let {
                                Base64.getDecoder().decode(it)
                            }
            val kf = KeyFactory.getInstance("RSA")
            val keySpec: EncodedKeySpec = PKCS8EncodedKeySpec(decodedBytes)
            val pk = kf.generatePrivate(keySpec)
            val rsa256 = Algorithm.RSA256(null, pk as RSAPrivateKey)
            return JWT.create()
                    .withIssuer(iss)
                    .withSubject(sub)
                    .withAudience(aud)
                    .withExpiresAt(Date.from(Instant.now().plusSeconds(300)))
                    .sign(rsa256)
        }

        private val token = lazy { runBlocking { fetchAccessToken() } }
        override val authLookup: AuthLookup = { _ -> Auth.OAuth { token.value } }

    }
}