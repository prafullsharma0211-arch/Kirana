package com.altstudio.kirana.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class KiranaRepository(private val kiranaDao: KiranaDao) {
    val allProducts: Flow<List<Product>> = kiranaDao.getAllProducts()
    fun searchProducts(query: String) = kiranaDao.searchProducts(query)
    val lowStockProducts: Flow<List<Product>> = kiranaDao.getLowStockProducts()
    val allInvoices: Flow<List<Invoice>> = kiranaDao.getAllInvoices()
    val allCustomerNames: Flow<List<String>> = kiranaDao.getAllCustomerNames()
    val creditInvoices: Flow<List<Invoice>> = kiranaDao.getCreditInvoices()
    val allCustomers: Flow<List<Customer>> = kiranaDao.getAllCustomers()

    suspend fun insertRepayment(repayment: Repayment) = kiranaDao.insertRepaymentAndUpdateBalance(repayment)
    fun getRepaymentsForCustomer(customerName: String) = kiranaDao.getRepaymentsForCustomer(customerName)
    fun getInvoicesForCustomer(customerName: String) = kiranaDao.getInvoicesForCustomer(customerName)

    suspend fun insertCustomer(customer: Customer) = kiranaDao.insertCustomer(customer)
    suspend fun deleteCustomer(customer: Customer) = kiranaDao.deleteCustomer(customer)
    suspend fun getCustomerByName(name: String) = kiranaDao.getCustomerByName(name)

    suspend fun insertProduct(product: Product) = kiranaDao.insertProduct(product)
    suspend fun updateProduct(product: Product) = kiranaDao.updateProduct(product)
    suspend fun deleteProduct(product: Product) = kiranaDao.deleteProduct(product)
    suspend fun makeSale(invoice: Invoice, items: List<SaleItem>) = kiranaDao.makeSale(invoice, items)
    suspend fun getItemsForInvoice(invoiceId: Int) = kiranaDao.getItemsForInvoice(invoiceId)
    suspend fun getProductByBarcode(barcode: String) = kiranaDao.getProductByBarcode(barcode)

    suspend fun getAllProductsList() = kiranaDao.getAllProductsList()
    suspend fun getAllInvoicesList() = kiranaDao.getAllInvoicesList()
    suspend fun getAllSaleItemsList() = kiranaDao.getAllSaleItemsList()
    suspend fun getAllRepaymentsList() = kiranaDao.getAllRepaymentsList()

    suspend fun syncData(products: List<Product>, invoices: List<Invoice>, repayments: List<Repayment>, saleItems: List<SaleItem>, customers: List<Customer>) {
        kiranaDao.deleteAllProducts()
        kiranaDao.deleteAllInvoices()
        kiranaDao.deleteAllRepayments()
        kiranaDao.deleteAllSaleItems()
        kiranaDao.deleteAllCustomers()
        
        kiranaDao.insertProducts(products)
        kiranaDao.insertInvoices(invoices)
        kiranaDao.insertRepayments(repayments)
        kiranaDao.insertSaleItems(saleItems)
        kiranaDao.insertCustomers(customers)
    }

    fun getInvoicesFromDate(startDate: Long): Flow<List<Invoice>> = kiranaDao.getInvoicesFromDate(startDate)

    fun getDailySales() = getInvoicesFromDate(getStartOfDay())
    fun getWeeklySales() = getInvoicesFromDate(getStartOfWeekly())
    fun getMonthlySales() = getInvoicesFromDate(getStartOfMonth())

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getStartOfWeekly(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getStartOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun searchInvoices(query: String) = kiranaDao.searchInvoicesByCustomer(query)
}
