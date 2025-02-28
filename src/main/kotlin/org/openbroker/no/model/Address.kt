package org.openbroker.no.model

import org.openbroker.common.requireMatchRegex
import org.openbroker.common.requireNotBlank

data class Address(
    val firstName: String,
    val lastName: String,
    val address: String,
    val city: String,
    val postalCode: String,
    val careOf: String? = null
) {
    init {
        firstName.requireNotBlank("firstName")
        lastName.requireNotBlank("lastName")
        address.requireNotBlank("address")
        city.requireNotBlank("city")
        postalCode.requireMatchRegex(postalCodeRegex, "postalCode")
        careOf?.requireNotBlank("careOf")
    }

    companion object {
        val postalCodeRegex = Regex("^\\d{4}$")
    }
}