package com.example

import java.time.Duration
import java.time.Instant
import java.math.BigDecimal
import java.math.RoundingMode

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

// ---------------- infrastructure ----------------
class DeliveryRepository {

    private val events = mutableListOf(
        Delivery(1, 1, "Entrega", Status.PENDING,   Instant.parse("2025-09-06T15:16:00Z")),
        Delivery(1, 2, "Entrega", Status.PENDING,   Instant.parse("2025-09-06T15:16:00Z")),
        Delivery(1, 1, "Entrega", Status.DELIVERED, Instant.parse("2025-09-06T15:18:00Z")),
        Delivery(1, 2, "Entrega", Status.CANCELLED, Instant.parse("2025-09-06T15:20:00Z"))
    )

    fun save(delivery: Delivery): Delivery {
        events += delivery
        return delivery
    }

    fun update(match: (Delivery) -> Boolean, newDelivery: Delivery): Delivery {
        val idx = events.indexOfFirst(match)
        if (idx >= 0) events[idx] = newDelivery
        return newDelivery
    }

    fun list(): List<Delivery> = events.toList()

    fun listBy(orderId: Long, deliveryId: Long): List<Delivery> =
        events.filter { it.orderId == orderId && it.deliveryId == deliveryId }
}

// ---------------- application/command ----------------
class DeliveryCommandInterface(
    private val repo: DeliveryRepository
) {
    fun create(delivery: Delivery): Delivery = repo.save(delivery)

    fun updateFirst(orderId: Long, deliveryId: Long, delivery: Delivery): Delivery =
        repo.update({ it.orderId == orderId && it.deliveryId == deliveryId }, delivery)

    fun calculateRates(): List<DeliveryRate> {
        val events = repo.list()
        val grouped = events.groupBy { it.orderId to it.deliveryId }

        return grouped.map { (key, evs) ->
            val sorted = evs.sortedBy { it.timestamp }

            val start = sorted.firstOrNull { it.status == Status.PENDING }?.timestamp
            val finalEv = sorted.lastOrNull { it.status == Status.DELIVERED || it.status == Status.CANCELLED }
            val end = finalEv?.timestamp
            val finalStatus = finalEv?.status ?: Status.PENDING

            val minutes = if (start != null && end != null) {
                val m = Duration.between(start, end).toMinutes().toDouble()
                maxOf(0.0, m)
            } else 0.0

            val amount = minutes * PAY_RATE_PER_MIN

            DeliveryRate(
                orderId = key.first,
                deliveryId = key.second,
                finalStatus = finalStatus,
                minutes = minutes,
                amount = amount
            )
        }
    }
}

// ---------------- application/query ----------------
class DeliveryQueryInterface(
    private val repo: DeliveryRepository
) {
    fun list(): List<Delivery> = repo.list()
    fun listBy(orderId: Long, deliveryId: Long): List<Delivery> = repo.listBy(orderId, deliveryId)
}

// ---------------- interface/api ----------------
class DeliveryInterfaceApi(
    private val repo: DeliveryRepository = DeliveryRepository()
) {
    private val command = DeliveryCommandInterface(repo)
    private val query = DeliveryQueryInterface(repo)

    fun create(delivery: Delivery): Delivery = command.create(delivery)
    fun updateFirst(orderId: Long, deliveryId: Long, delivery: Delivery): Delivery =
        command.updateFirst(orderId, deliveryId, delivery)
    fun listEvents(): List<Delivery> = query.list()
    fun listEventsBy(orderId: Long, deliveryId: Long): List<Delivery> = query.listBy(orderId, deliveryId)
    fun rates(): List<DeliveryRate> = command.calculateRates()
}


// ---------------- demo ----------------
fun main() {
    val api = DeliveryInterfaceApi()

    println("Eventos:")
    api.listEvents().forEach { println(it) }

    println("\nRates calculados (R$ 0,75/min):")
    api.rates().forEach { println(it) }
}
