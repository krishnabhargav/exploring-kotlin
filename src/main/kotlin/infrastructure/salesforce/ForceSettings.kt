package infrastructure.salesforce

import com.capsule.atlas.models.HostLookup

data class ForceSettings(
        private val root: String = "rest://sales-api/services/data/v47.0",
        val security: ForceSecurity,
        val hostLookup: HostLookup = { _ -> "https://na114.salesforce.com" }
) {
    fun forContactMetadata() =
            "${this.root}/sobjects/contact/describe?rest_operation=get&auth=force"

    fun forContactId(id: String) =
            "${this.root}/sobjects/contact/$id?rest_operation=get&auth=force"

    fun forSObjectUpdate(id: String, sobject: String) =
            "${this.root}/sobjects/$sobject/$id?rest_operation=post&auth=force&_HttpMethod=PATCH"
}