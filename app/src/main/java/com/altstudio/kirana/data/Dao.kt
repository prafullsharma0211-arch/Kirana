package com.altstudio.kirana.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KiranaDao {
    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%'")
    fun searchProducts(query: String): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("SELECT * FROM products WHERE currentStock <= lowStockThreshold")
    fun getLowStockProducts(): Flow<List<Product>>

    @Insert
    suspend fun insertInvoice(invoice: Invoice): Long

    @Insert
    suspend fun insertSaleItems(items: List<SaleItem>)

    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM sale_items WHERE invoiceId = :invoiceId")
    suspend fun getItemsForInvoice(invoiceId: Int): List<SaleItem>

    @Query("SELECT * FROM invoices WHERE customerName LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchInvoicesByCustomer(query: String): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE date >= :startDate ORDER BY date DESC")
    fun getInvoicesFromDate(startDate: Long): Flow<List<Invoice>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Int): Product?

    @Query("SELECT * FROM invoices WHERE paymentMode = 'CREDIT' ORDER BY date DESC")
    fun getCreditInvoices(): Flow<List<Invoice>>

    @Insert
    suspend fun insertRepayment(repayment: Repayment)

    @Query("SELECT * FROM repayments WHERE customerName = :customerName ORDER BY date DESC")
    fun getRepaymentsForCustomer(customerName: String): Flow<List<Repayment>>

    @Query("SELECT * FROM invoices WHERE customerName = :customerName ORDER BY date DESC")
    fun getInvoicesForCustomer(customerName: String): Flow<List<Invoice>>

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun getProductByBarcode(barcode: String): Product?
    
    @Query("SELECT * FROM products")
    suspend fun getAllProductsList(): List<Product>

    @Query("SELECT * FROM invoices")
    suspend fun getAllInvoicesList(): List<Invoice>

    @Query("SELECT * FROM sale_items")
    suspend fun getAllSaleItemsList(): List<SaleItem>

    @Query("SELECT * FROM repayments")
    suspend fun getAllRepaymentsList(): List<Repayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<Invoice>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayments(repayments: List<Repayment>)
    
    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()

    @Query("DELETE FROM sale_items")
    suspend fun deleteAllSaleItems()

    @Query("DELETE FROM repayments")
    suspend fun deleteAllRepayments()

    @Query("SELECT DISTINCT customerName FROM invoices")
    fun getAllCustomerNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Query("SELECT * FROM customers")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE name = :name")
    suspend fun getCustomerByName(name: String): Customer?

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)
    
    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Transaction
    suspend fun makeSale(invoice: Invoice, items: List<SaleItem>) {
        val invoiceId = insertInvoice(invoice).toInt()
        val itemsWithId = items.map { it.copy(invoiceId = invoiceId) }
        insertSaleItems(itemsWithId)
        
        for (item in items) {
            val product = getProductById(item.productId)
            if (product != null) {
                updateProduct(product.copy(currentStock = product.currentStock - item.quantity))
            }
        }

        if (invoice.paymentMode == PaymentMode.CREDIT) {
            val currentCustomer = getCustomerByName(invoice.customerName) ?: Customer(name = invoice.customerName)
            insertCustomer(currentCustomer.copy(balance = currentCustomer.balance + invoice.totalAmount))
        }
    }

    @Transaction
    suspend fun insertRepaymentAndUpdateBalance(repayment: Repayment) {
        insertRepayment(repayment)
        val customer = getCustomerByName(repayment.customerName) ?: Customer(name = repayment.customerName)
        insertCustomer(customer.copy(balance = customer.balance - repayment.amount))
    }
}
