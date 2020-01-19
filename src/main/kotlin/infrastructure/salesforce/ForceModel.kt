package infrastructure.salesforce

import com.capsule.atlas.Outputs
import com.capsule.atlas.WriteResult
import com.capsule.atlas.models.DomainEvent
import com.capsule.atlas.models.StreamDefinition
import com.capsule.atlas.models.getOrElse
import com.capsule.atlas.utils.Json
import com.squareup.moshi.JsonClass
import infrastructure.asRest

object ForceModel {
    data class Attributes(
            val type: String,
            val url: String
    )

    @JsonClass(generateAdapter = true)
    data class Contact(
            val attributes: Attributes,
            val Id: String?,
            val IsDeleted: Boolean?,
            val MasterRecordId: String?,
            val AccountId: String?,
            val LastName: String?,
            val FirstName: String?,
            val Salutation: String?,
            val Name: String?,
            val OtherStreet: String?,
            val OtherCity: String?,
            val OtherState: String?,
            val OtherPostalCode: String?,
            val OtherCountry: String?,
            val OtherLatitude: String?,
            val OtherLongitude: String?,
            val OtherGeocodeAccuracy: String?,
            val OtherAddress: String?,
            val MailingStreet: String?,
            val MailingCity: String?,
            val MailingState: String?,
            val MailingPostalCode: String?,
            val MailingCountry: String?,
            val MailingLatitude: String?,
            val MailingLongitude: String?,
            val MailingGeocodeAccuracy: String?,
            val MailingAddress: String?,
            val Phone: String?,
            val Fax: String?,
            val MobilePhone: String?,
            val HomePhone: String?,
            val OtherPhone: String?,
            val AssistantPhone: String?,
            val ReportsToId: String?,
            val Email: String?,
            val Title: String?,
            val Department: String?,
            val AssistantName: String?,
            val LeadSource: String?,
            val Birthdate: String?,
            val Description: String?,
            val OwnerId: String?,
            val CreatedDate: String?,
            val CreatedById: String?,
            val LastModifiedDate: String?,
            val LastModifiedById: String?,
            val SystemModstamp: String?,
            val LastActivityDate: String?,
            val LastCURequestDate: String?,
            val LastCUUpdateDate: String?,
            val LastViewedDate: String?,
            val LastReferencedDate: String?,
            val EmailBouncedReason: String?,
            val EmailBouncedDate: String?,
            val IsEmailBounced: Boolean?,
            val PhotoUrl: String?,
            val Jigsaw: String?,
            val JigsawContactId: String?,
            val CleanStatus: String?,
            val IndividualId: String?,
            val customProperties: Map<String, Any?> = mapOf()
    )

    suspend fun loadContact(force: ForceSettings, id: String): Contact {
        val sd = StreamDefinition.parse(force.forContactId(id), force.hostLookup, force.security.authLookup)
        val restResult = Outputs.writeWithRetries(sd, DomainEvent.empty()).asRest()
        val contact = Json.fromJson<Contact>(restResult.body)
        //to get all custom fields, we will need to parse it again. Not ideal but better than hitting another API
        val allFields = Json.fromJson<Map<String, Any?>>(restResult.body).keys
        val customFields = allFields.filter { it.endsWith("__c") }
        val values = customFields.fold(mapOf<String, String?>(), { c, f ->
            val valueForTheField =
                    Json.tryGetFromPathT<String>(restResult.body, "\$.$f")
                            .getOrElse { null }
            return@fold (c + (f to valueForTheField))
        })
        return contact.copy(customProperties = values)
    }

    suspend fun updateContact(force: ForceSettings, id: String, updates: Map<String, Any>): WriteResult.Rest {
        return updateSObject(
                force = force,
                resourceString = force.forSObjectUpdate(id, "contact"),
                updates = updates
        )
    }

    private suspend fun updateSObject(force: ForceSettings, resourceString: String, updates: Map<String, Any>): WriteResult.Rest {
        val sd = StreamDefinition.parse(
                uri_string = resourceString,
                hostLookup = force.hostLookup,
                authLookup = force.security.authLookup
        )
        val de = DomainEvent.empty().copy(data = Json.toJsonBytes(updates))
        return Outputs.writeWithRetries(sd, de).asRest()
    }
}