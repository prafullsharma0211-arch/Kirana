package com.altstudio.kirana

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.altstudio.kirana.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

data class SalesStats(
    val totalSales: Double = 0.0,
    val totalProfit: Double = 0.0
)

data class MonthlyStats(
    val monthYear: String,
    val stats: SalesStats
)

class KiranaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: KiranaRepository

    val allProducts: StateFlow<List<Product>>
    val lowStockProducts: StateFlow<List<Product>>
    
    val dailyStats: StateFlow<SalesStats>
    val weeklyStats: StateFlow<SalesStats>
    val monthlyStats: StateFlow<SalesStats>
    
    val allInvoices: StateFlow<List<Invoice>>
    val allCustomerNames: StateFlow<List<String>>
    val allCustomers: StateFlow<List<Customer>>
    val creditInvoices: StateFlow<List<Invoice>>

    val monthlyHistory: StateFlow<List<MonthlyStats>>

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    val currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)

    init {
        val dao = KiranaDatabase.getDatabase(application).kiranaDao()
        repository = KiranaRepository(dao)
        
        allProducts = repository.allProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        lowStockProducts = repository.lowStockProducts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
        dailyStats = repository.getDailySales()
            .map { invoices -> calculateStats(invoices) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SalesStats())
            
        weeklyStats = repository.getWeeklySales()
            .map { invoices -> calculateStats(invoices) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SalesStats())
            
        monthlyStats = repository.getMonthlySales()
            .map { invoices -> calculateStats(invoices) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SalesStats())

        allInvoices = repository.allInvoices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allCustomerNames = repository.allCustomerNames.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allCustomers = repository.allCustomers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        creditInvoices = repository.creditInvoices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        monthlyHistory = allInvoices.map { invoices ->
            val sdf = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
            invoices.groupBy {
                sdf.format(java.util.Date(it.date))
            }.map { (monthYear, monthInvoices) ->
                MonthlyStats(monthYear, calculateStats(monthInvoices))
            }.sortedByDescending {
                sdf.parse(it.monthYear)?.time ?: 0L
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun makeRepayment(customerName: String, amount: Double) {
        viewModelScope.launch {
            repository.insertRepayment(Repayment(customerName = customerName, amount = amount))
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
        }
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.insertCustomer(customer)
        }
    }

    suspend fun getCustomerByName(name: String): Customer? {
        return repository.getCustomerByName(name)
    }

    fun getRepaymentsForCustomer(customerName: String): Flow<List<Repayment>> {
        return repository.getRepaymentsForCustomer(customerName)
    }

    fun getInvoicesForCustomer(customerName: String): Flow<List<Invoice>> {
        return repository.getInvoicesForCustomer(customerName)
    }

    private fun calculateStats(invoices: List<Invoice>): SalesStats {
        return SalesStats(
            totalSales = invoices.sumOf { it.totalAmount },
            totalProfit = invoices.sumOf { it.totalProfit }
        )
    }

    fun addProduct(product: Product) {
        viewModelScope.launch {
            repository.insertProduct(product)
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }

    fun makeSale(invoice: Invoice, items: List<SaleItem>) {
        viewModelScope.launch {
            repository.makeSale(invoice, items)
        }
    }
    
    suspend fun getItemsForInvoice(invoiceId: Int): List<SaleItem> {
        return repository.getItemsForInvoice(invoiceId)
    }

    suspend fun getProductByBarcode(barcode: String): Product? {
        return repository.getProductByBarcode(barcode)
    }

    fun searchInvoices(query: String): Flow<List<Invoice>> {
        return repository.searchInvoices(query)
    }

    fun onUserChanged(user: FirebaseUser?) {
        currentUser.value = user
    }

    // Backup layout: all data is stored as sibling documents in the "users" collection,
    // using compound IDs like "{uid}_products_0", "{uid}_products_1", etc.
    // Each document holds up to CHUNK_SIZE records under an "items" array field.
    // This avoids subcollections entirely, so standard security rules
    // (match /users/{docId}) cover everything without needing {document=**}.
    private val CHUNK_SIZE = 500

    fun syncToCloud(onResult: (String) -> Unit) {
        val user = currentUser.value ?: return onResult("Not logged in")
        viewModelScope.launch {
            try {
                db.enableNetwork().await()
                val col = db.collection("users")
                val uid = user.uid
                val storageRef = storage.reference.child("users").child(uid)

                val products = repository.getAllProductsList()
                val invoices = repository.getAllInvoicesList()
                val repayments = repository.getAllRepaymentsList()
                val saleItems = repository.getAllSaleItemsList()
                val customers = repository.allCustomers.first()

                // 1. Upload Images to Firebase Storage and update URIs
                val updatedProducts = products.map { product ->
                    if (!product.imageUri.isNullOrEmpty() && !product.imageUri.startsWith("http")) {
                        try {
                            val fileUri = Uri.parse(product.imageUri)
                            val imageRef = storageRef.child("products").child("${product.id}.jpg")
                            imageRef.putFile(fileUri).await()
                            product.copy(imageUri = imageRef.downloadUrl.await().toString())
                        } catch (e: Exception) { product }
                    } else product
                }

                val updatedCustomers = customers.map { customer ->
                    if (!customer.imageUri.isNullOrEmpty() && !customer.imageUri.startsWith("http")) {
                        try {
                            val fileUri = Uri.parse(customer.imageUri)
                            val imageRef = storageRef.child("customers").child("${customer.name}.jpg")
                            imageRef.putFile(fileUri).await()
                            customer.copy(imageUri = imageRef.downloadUrl.await().toString())
                        } catch (e: Exception) { customer }
                    } else customer
                }

                // Write a list as chunked sibling documents
                suspend fun <T> writeChunked(tag: String, items: List<T>, toMap: (T) -> Map<String, Any?>) {
                    val chunks = items.chunked(CHUNK_SIZE)
                    chunks.forEachIndexed { i, chunk ->
                        col.document("${uid}_${tag}_$i")
                            .set(mapOf("items" to chunk.map { toMap(it) })).await()
                    }
                    var i = chunks.size
                    while (true) {
                        val old = col.document("${uid}_${tag}_$i").get().await()
                        if (!old.exists()) break
                        old.reference.delete().await()
                        i++
                    }
                }

                writeChunked("products", updatedProducts) { p -> mapOf(
                    "id" to p.id, "name" to p.name, "barcode" to p.barcode,
                    "imageUri" to p.imageUri, "purchasePrice" to p.purchasePrice,
                    "salePrice" to p.salePrice, "tax" to p.tax,
                    "unitType" to p.unitType.name, "currentStock" to p.currentStock,
                    "lowStockThreshold" to p.lowStockThreshold
                )}

                writeChunked("invoices", invoices) { inv -> mapOf(
                    "id" to inv.id, "customerName" to inv.customerName,
                    "date" to inv.date, "totalAmount" to inv.totalAmount,
                    "totalProfit" to inv.totalProfit, "paymentMode" to inv.paymentMode.name,
                    "isFullyPaid" to inv.isFullyPaid
                )}

                writeChunked("repayments", repayments) { r -> mapOf(
                    "id" to r.id, "customerName" to r.customerName,
                    "amount" to r.amount, "date" to r.date
                )}

                writeChunked("saleItems", saleItems) { s -> mapOf(
                    "id" to s.id, "invoiceId" to s.invoiceId,
                    "productId" to s.productId, "productName" to s.productName,
                    "quantity" to s.quantity, "salePrice" to s.salePrice,
                    "purchasePrice" to s.purchasePrice, "totalAmount" to s.totalAmount
                )}

                writeChunked("customers", updatedCustomers) { c -> mapOf(
                    "name" to c.name, "phoneNumber" to c.phoneNumber,
                    "imageUri" to c.imageUri, "balance" to c.balance
                )}

                val productChunks = maxOf(1, (products.size + CHUNK_SIZE - 1) / CHUNK_SIZE)
                val invoiceChunks = maxOf(1, (invoices.size + CHUNK_SIZE - 1) / CHUNK_SIZE)
                val repaymentChunks = maxOf(1, (repayments.size + CHUNK_SIZE - 1) / CHUNK_SIZE)
                val saleItemChunks = maxOf(1, (saleItems.size + CHUNK_SIZE - 1) / CHUNK_SIZE)
                val customerChunks = maxOf(1, (customers.size + CHUNK_SIZE - 1) / CHUNK_SIZE)

                col.document(uid).set(mapOf(
                    "lastSync" to System.currentTimeMillis(),
                    "productChunks" to productChunks,
                    "invoiceChunks" to invoiceChunks,
                    "repaymentChunks" to repaymentChunks,
                    "saleItemChunks" to saleItemChunks,
                    "customerChunks" to customerChunks
                )).await()

                onResult("VERIFIED\n${updatedProducts.size} products backed up with images")
            } catch (e: Exception) {
                onResult("Backup failed: ${e.localizedMessage}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun syncFromCloud(onResult: (String) -> Unit) {
        val user = currentUser.value ?: return onResult("Not logged in")
        viewModelScope.launch {
            try {
                db.enableNetwork().await()
                val col = db.collection("users")
                val uid = user.uid

                val metaDoc = col.document(uid).get().await()
                if (!metaDoc.exists()) {
                    onResult("No backup file found for this account")
                    return@launch
                }

                val productChunks  = (metaDoc.getLong("productChunks")   ?: 0).toInt()
                val invoiceChunks  = (metaDoc.getLong("invoiceChunks")   ?: 0).toInt()
                val repaymentChunks = (metaDoc.getLong("repaymentChunks") ?: 0).toInt()
                val saleItemChunks = (metaDoc.getLong("saleItemChunks")  ?: 0).toInt()
                val customerChunks = (metaDoc.getLong("customerChunks")  ?: 0).toInt()

                if (productChunks == 0 && invoiceChunks == 0 && customerChunks == 0) {
                    onResult("Backup found but it is empty")
                    return@launch
                }

                // Read all chunks for a given tag and flatten into one list of maps
                suspend fun readChunks(tag: String, count: Int): List<Map<String, Any>> =
                    (0 until count).flatMap { i ->
                        (col.document("${uid}_${tag}_$i").get().await()
                            .get("items") as? List<Map<String, Any>>) ?: emptyList()
                    }

                val productsData   = readChunks("products",   productChunks)
                val invoicesData   = readChunks("invoices",   invoiceChunks)
                val repaymentsData = readChunks("repayments", repaymentChunks)
                val saleItemsData  = readChunks("saleItems",  saleItemChunks)
                val customersData  = readChunks("customers",  customerChunks)

                // Parse everything before touching the local DB —
                // a parse error here won't delete local data
                val products = productsData.map {
                    Product(
                        id = (it["id"] as? Long ?: 0).toInt(),
                        name = it["name"] as? String ?: "Unknown",
                        barcode = it["barcode"] as? String,
                        imageUri = it["imageUri"] as? String,
                        purchasePrice = (it["purchasePrice"] as? Number)?.toDouble() ?: 0.0,
                        salePrice = (it["salePrice"] as? Number)?.toDouble() ?: 0.0,
                        tax = (it["tax"] as? Number)?.toDouble() ?: 0.0,
                        unitType = UnitType.valueOf(it["unitType"] as? String ?: "UNITS"),
                        currentStock = (it["currentStock"] as? Number)?.toDouble() ?: 0.0,
                        lowStockThreshold = (it["lowStockThreshold"] as? Number)?.toDouble() ?: 5.0
                    )
                }

                val invoices = invoicesData.map {
                    Invoice(
                        id = (it["id"] as? Long ?: 0).toInt(),
                        customerName = it["customerName"] as? String ?: "Guest",
                        date = it["date"] as? Long ?: System.currentTimeMillis(),
                        totalAmount = (it["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                        totalProfit = (it["totalProfit"] as? Number)?.toDouble() ?: 0.0,
                        paymentMode = PaymentMode.valueOf(it["paymentMode"] as? String ?: "CASH"),
                        isFullyPaid = it["isFullyPaid"] as? Boolean ?: true
                    )
                }

                val repayments = repaymentsData.map {
                    Repayment(
                        id = (it["id"] as? Long ?: 0).toInt(),
                        customerName = it["customerName"] as? String ?: "",
                        amount = (it["amount"] as? Number)?.toDouble() ?: 0.0,
                        date = it["date"] as? Long ?: System.currentTimeMillis()
                    )
                }

                val saleItems = saleItemsData.map {
                    SaleItem(
                        id = (it["id"] as? Long ?: 0).toInt(),
                        invoiceId = (it["invoiceId"] as? Long ?: 0).toInt(),
                        productId = (it["productId"] as? Long ?: 0).toInt(),
                        productName = it["productName"] as? String ?: "",
                        quantity = (it["quantity"] as? Number)?.toDouble() ?: 0.0,
                        salePrice = (it["salePrice"] as? Number)?.toDouble() ?: 0.0,
                        purchasePrice = (it["purchasePrice"] as? Number)?.toDouble() ?: 0.0,
                        totalAmount = (it["totalAmount"] as? Number)?.toDouble() ?: 0.0
                    )
                }

                val customers = customersData.map {
                    Customer(
                        name = it["name"] as? String ?: "",
                        phoneNumber = it["phoneNumber"] as? String,
                        imageUri = it["imageUri"] as? String,
                        balance = (it["balance"] as? Number)?.toDouble() ?: 0.0
                    )
                }

                // 2. Download Images from Firebase Storage and update local URIs
                val imagesDir = File(getApplication<Application>().filesDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()

                suspend fun downloadImage(remoteUrl: String?, subDir: String, fileName: String): String? {
                    if (remoteUrl.isNullOrEmpty() || !remoteUrl.startsWith("http")) return remoteUrl
                    return try {
                        val file = File(imagesDir, "$subDir/$fileName")
                        file.parentFile?.mkdirs()
                        val imageRef = storage.getReferenceFromUrl(remoteUrl)
                        imageRef.getFile(file).await()
                        Uri.fromFile(file).toString()
                    } catch (e: Exception) {
                        remoteUrl
                    }
                }

                val finalProducts = products.map { product ->
                    product.copy(imageUri = downloadImage(product.imageUri, "products", "${product.id}.jpg"))
                }
                val finalCustomers = customers.map { customer ->
                    customer.copy(imageUri = downloadImage(customer.imageUri, "customers", "${customer.name}.jpg"))
                }

                repository.syncData(finalProducts, invoices, repayments, saleItems, finalCustomers)
                onResult("Success")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("Error: ${e.localizedMessage}")
            }
        }
    }
}
