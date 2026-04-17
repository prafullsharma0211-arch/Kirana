package com.altstudio.kirana.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

enum class UnitType {
    LITRE, KG, UNITS
}

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val barcode: String? = null,
    val imageUri: String? = null,
    val purchasePrice: Double,
    val salePrice: Double,
    val tax: Double,
    val unitType: UnitType,
    val currentStock: Double,
    val lowStockThreshold: Double = 5.0
)

enum class PaymentMode {
    CASH, CREDIT
}

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val date: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val totalProfit: Double,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val isFullyPaid: Boolean = (paymentMode == PaymentMode.CASH)
)

@Entity(tableName = "repayments")
data class Repayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val amount: Double,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val name: String,
    val phoneNumber: String? = null,
    val imageUri: String? = null,
    val balance: Double = 0.0
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceId: Int,
    val productId: Int,
    val productName: String,
    val quantity: Double,
    val salePrice: Double,
    val purchasePrice: Double,
    val totalAmount: Double
) {
    val profit: Double get() = (salePrice - purchasePrice) * quantity
}

