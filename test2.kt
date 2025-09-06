package com.example

import java.time.Duration
import java.time.Instant

private const val PAY_RATE_PER_MIN = 0.75

enum class Status { PENDING, DELIVERED, CANCELLED }

data class Delivery(
    val orderId: Long,
    val deliveryId: Long,
    val tipo: String,
    val status: Status,
    val timestamp: Instant
) {
    init { require(tipo.isNotBlank()) { "tipo cannot be blank" } }
}

data class DeliveryRate(
    val orderId: Long,
    val deliveryId: Long,
    val finalStatus: Status,
    val minutes: Double,
    val amount: Double
)



fun calculateRates(deliveries: List<Delivery>): List<DeliveryRate> {
    return deliveries
    	.filter { it.status in listOf(Status.PENDING, Status.DELIVERED, Status.CANCELLED) }
        .groupBy { it.orderId to it.deliveryId }
        .map { (key, events) ->
            val sorted = events.sortedBy { it.timestamp }
            require(sorted.size == 2) { "Expected exactly 2 events, found ${sorted.size}" }
            require(sorted[0].status == Status.PENDING) { "First status should be PENDING, found ${sorted[0].status}" }
            require(sorted[1].status in listOf(Status.DELIVERED, Status.CANCELLED)) { 
                "Second must be DELIVERED or CANCELLED, found ${sorted[1].status}" 
            }

            val minutes = Duration.between(sorted[0].timestamp, sorted[1].timestamp).toMinutes().toDouble()
            val amount = minutes * PAY_RATE_PER_MIN

            DeliveryRate(
                orderId = sorted[0].orderId,
                deliveryId = sorted[0].deliveryId,
                finalStatus = sorted[1].status,
                minutes = minutes,
                amount = amount
            )
        }
}


fun main() {
    val eventsMock = mutableListOf(
        Delivery(1, 1, "Entrega", Status.PENDING,   Instant.parse("2025-09-06T15:16:00Z")),
        Delivery(1, 2, "Entrega", Status.PENDING,   Instant.parse("2025-09-06T15:16:00Z")),
        Delivery(1, 1, "Entrega", Status.DELIVERED, Instant.parse("2025-09-06T15:18:00Z")),
        Delivery(1, 2, "Entrega", Status.CANCELLED, Instant.parse("2025-09-06T15:20:00Z"))
    )
    
    val l = calculateRates(eventsMock)
    println(l)
}
