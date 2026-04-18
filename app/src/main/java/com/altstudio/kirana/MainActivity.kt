package com.altstudio.kirana

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.altstudio.kirana.data.*
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import com.altstudio.kirana.ui.theme.KiranaTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KiranaTheme {
                KiranaApp()
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    ADD_PRODUCT("Add Product", Icons.Default.Add),
    INVENTORY("Inventory", Icons.Default.Inventory),
    POS("Sale", Icons.Default.ShoppingCart),
    HISTORY("History", Icons.Default.History),
    CREDIT("Credit", Icons.Default.AccountBalanceWallet),
    SETTINGS("Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiranaApp(viewModel: KiranaViewModel = viewModel()) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (!cameraGranted) {
            Toast.makeText(context, "Camera permission is required for scanning barcodes", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    var currentDestination by remember { mutableStateOf(AppDestinations.POS) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDestination.label) },
                actions = {
                    IconButton(onClick = { currentDestination = AppDestinations.ADD_PRODUCT }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Product")
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Dashboard") },
                            onClick = {
                                currentDestination = AppDestinations.DASHBOARD
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Dashboard, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Credit") },
                            onClick = {
                                currentDestination = AppDestinations.CREDIT
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                currentDestination = AppDestinations.SETTINGS
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val bottomDestinations = listOf(
                    AppDestinations.POS,
                    AppDestinations.INVENTORY,
                    AppDestinations.HISTORY
                )
                bottomDestinations.forEach { destination ->
                    NavigationBarItem(
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        selected = destination == currentDestination,
                        onClick = { currentDestination = destination }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            when (currentDestination) {
                AppDestinations.DASHBOARD -> DashboardScreen(viewModel)
                AppDestinations.ADD_PRODUCT -> AddProductScreen(viewModel)
                AppDestinations.INVENTORY -> InventoryScreen(viewModel)
                AppDestinations.POS -> SaleScreen(viewModel)
                AppDestinations.HISTORY -> HistoryScreen(viewModel)
                AppDestinations.CREDIT -> CreditScreen(viewModel)
                AppDestinations.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: KiranaViewModel) {
    val daily by viewModel.dailyStats.collectAsState()
    val weekly by viewModel.weeklyStats.collectAsState()
    val monthly by viewModel.monthlyStats.collectAsState()
    val monthlyHistory by viewModel.monthlyHistory.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Business Overview", style = MaterialTheme.typography.headlineMedium) }
        item { StatsCard("Today", daily) }
        item { StatsCard("This Week", weekly) }
        item { StatsCard("This Month", monthly) }
        
        if (monthlyHistory.isNotEmpty()) {
            item { 
                Text("Monthly History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            items(monthlyHistory) { monthStats ->
                StatsCard(monthStats.monthYear, monthStats.stats)
            }
        }
    }
}

@Composable
fun StatsCard(title: String, stats: SalesStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Sales", style = MaterialTheme.typography.bodyMedium)
                    Text("₹${String.format(Locale.getDefault(), "%.2f", stats.totalSales)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Profit", style = MaterialTheme.typography.bodyMedium)
                    Text("₹${String.format(Locale.getDefault(), "%.2f", stats.totalProfit)}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(viewModel: KiranaViewModel) {
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var lowStockThreshold by remember { mutableStateOf("5.0") }
    var unitType by remember { mutableStateOf(UnitType.UNITS) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scanner = remember { GmsBarcodeScanning.getClient(context) }
    val scope = rememberCoroutineScope()

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> imageUri = uri }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            imageUri = tempImageUri
            // Extract text from image
            tempImageUri?.let { uri ->
                val image = try {
                    InputImage.fromFilePath(context, uri)
                } catch (e: Exception) {
                    null
                }
                image?.let {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(it)
                        .addOnSuccessListener { visionText ->
                            val resultText = visionText.text
                            if (resultText.isNotBlank()) {
                                // Try to find product name (usually the first line)
                                if (name.isBlank()) {
                                    name = resultText.lines().firstOrNull { it.isNotBlank() } ?: ""
                                }
                                // Try to find prices (look for numbers)
                                val numbers = "\\d+(\\.\\d+)?".toRegex().findAll(resultText).map { it.value }.toList()
                                if (numbers.isNotEmpty()) {
                                    if (salePrice.isBlank()) salePrice = numbers.lastOrNull() ?: ""
                                    if (purchasePrice.isBlank() && numbers.size > 1) purchasePrice = numbers[numbers.size - 2]
                                }
                                Toast.makeText(context, "Text recognized", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }
        }
    }

    fun createImageUri(): Uri {
        val file = File(context.cacheDir, "images/temp_image_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Select Image Source") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Camera") },
                        leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                        modifier = Modifier.clickable {
                            val uri = createImageUri()
                            tempImageUri = uri
                            cameraLauncher.launch(uri)
                            showImageSourceDialog = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Gallery") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                        modifier = Modifier.clickable {
                            galleryLauncher.launch("image/*")
                            showImageSourceDialog = false
                        }
                    )
                }
            },
            confirmButton = {}
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Add New Product", style = MaterialTheme.typography.headlineMedium)
            
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showImageSourceDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("Add Product Image")
                    }
                }
            }

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, modifier = Modifier.fillMaxWidth())
            
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Barcode") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        scanner.startScan()
                            .addOnSuccessListener { barcodeResult -> barcode = barcodeResult.rawValue ?: "" }
                            .addOnFailureListener { e -> Toast.makeText(context, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Default.QrCodeScanner, null) }
                }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = purchasePrice, onValueChange = { purchasePrice = it }, label = { Text("Cost Price") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = salePrice, onValueChange = { salePrice = it }, label = { Text("Sale Price") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = tax, onValueChange = { tax = it }, label = { Text("Tax %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Initial Stock") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }

            OutlinedTextField(
                value = lowStockThreshold,
                onValueChange = { lowStockThreshold = it },
                label = { Text("Low Stock Alert Level") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text("You'll be notified when stock falls below this level") }
            )
            
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = unitType.name, onValueChange = {}, readOnly = true, label = { Text("Unit Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    UnitType.entries.forEach { unit ->
                        DropdownMenuItem(text = { Text(unit.name) }, onClick = { unitType = unit; expanded = false })
                    }
                }
            }

            Button(
                onClick = {
                    if (name.isNotEmpty() && purchasePrice.isNotEmpty() && salePrice.isNotEmpty()) {
                        viewModel.addProduct(Product(
                            name = name, barcode = barcode.ifEmpty { null }, imageUri = imageUri?.toString(),
                            purchasePrice = purchasePrice.toDoubleOrNull() ?: 0.0,
                            salePrice = salePrice.toDoubleOrNull() ?: 0.0,
                            tax = tax.toDoubleOrNull() ?: 0.0,
                            currentStock = stock.toDoubleOrNull() ?: 0.0,
                            lowStockThreshold = lowStockThreshold.toDoubleOrNull() ?: 5.0,
                            unitType = unitType
                        ))
                        name = ""; barcode = ""; purchasePrice = ""; salePrice = ""; tax = ""; stock = ""; lowStockThreshold = "5.0"; imageUri = null
                        Toast.makeText(context, "Product Added", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Save Product") }
        }
    }
}

@Composable
fun InventoryScreen(viewModel: KiranaViewModel) {
    val allProducts by viewModel.allProducts.collectAsState()
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showLowStockOnly by remember { mutableStateOf(false) }

    val filteredProducts = remember(allProducts, searchQuery, showLowStockOnly) {
        allProducts.filter { product ->
            val matchesSearch = product.name.contains(searchQuery, ignoreCase = true) ||
                    (product.barcode?.contains(searchQuery) == true)
            val matchesLowStock = !showLowStockOnly || product.currentStock <= product.lowStockThreshold
            matchesSearch && matchesLowStock
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Inventory Management", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Products") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            FilterChip(
                selected = showLowStockOnly,
                onClick = { showLowStockOnly = !showLowStockOnly },
                label = { Text("Low Stock Only") },
                leadingIcon = if (showLowStockOnly) {
                    { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
            if (showLowStockOnly) {
                Text(
                    text = "${filteredProducts.size} items low",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredProducts) { product ->
                ProductInventoryItem(
                    product, 
                    onEdit = { editingProduct = it },
                    onDelete = { productToDelete = it }
                )
            }
        }
    }

    if (editingProduct != null) {
        EditProductDialog(product = editingProduct!!, onDismiss = { editingProduct = null }, onSave = { updated ->
            viewModel.updateProduct(updated)
            editingProduct = null
        })
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Delete Product") },
            text = { Text("Are you sure you want to delete ${productToDelete?.name}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        productToDelete?.let { viewModel.deleteProduct(it) }
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProductInventoryItem(product: Product, onEdit: (Product) -> Unit, onDelete: (Product) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit(product) }) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (product.imageUri != null) {
                AsyncImage(model = product.imageUri, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text("Stock: ${product.currentStock} ${product.unitType}", color = if (product.currentStock <= product.lowStockThreshold) Color.Red else Color.Unspecified)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${product.salePrice}", fontWeight = FontWeight.Bold)
                Text("Cost: ₹${product.purchasePrice}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onDelete(product) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
    }
}

@Composable
fun EditProductDialog(product: Product, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var costPrice by remember { mutableStateOf(product.purchasePrice.toString()) }
    var salePrice by remember { mutableStateOf(product.salePrice.toString()) }
    var tax by remember { mutableStateOf(product.tax.toString()) }
    var stock by remember { mutableStateOf(product.currentStock.toString()) }
    var lowStockThreshold by remember { mutableStateOf(product.lowStockThreshold.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = costPrice, onValueChange = { costPrice = it }, label = { Text("Cost Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = salePrice, onValueChange = { salePrice = it }, label = { Text("Sale Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = tax, onValueChange = { tax = it }, label = { Text("Tax %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = lowStockThreshold, onValueChange = { lowStockThreshold = it }, label = { Text("Low Stock Alert Level") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(product.copy(
                    purchasePrice = costPrice.toDoubleOrNull() ?: product.purchasePrice,
                    salePrice = salePrice.toDoubleOrNull() ?: product.salePrice,
                    tax = tax.toDoubleOrNull() ?: product.tax,
                    currentStock = stock.toDoubleOrNull() ?: product.currentStock,
                    lowStockThreshold = lowStockThreshold.toDoubleOrNull() ?: product.lowStockThreshold
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SaleScreen(viewModel: KiranaViewModel) {
    val products by viewModel.allProducts.collectAsState()
    val customerNames by viewModel.allCustomerNames.collectAsState()
    
    var customerName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    val currentSaleItems = remember { mutableStateListOf<SaleItem>() }
    
    var selectedProductForQuantity by remember { mutableStateOf<Product?>(null) }
    var showCustomItemDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scanner = remember { GmsBarcodeScanning.getClient(context) }
    val scope = rememberCoroutineScope()

    val filteredProducts = remember(searchQuery, products) {
        if (searchQuery.isEmpty()) emptyList()
        else products.filter { it.name.contains(searchQuery, ignoreCase = true) || it.barcode?.contains(searchQuery) == true }
    }
    
    val filteredCustomers = remember(customerName, customerNames) {
        if (customerName.isEmpty()) emptyList()
        else customerNames.filter { it.contains(customerName, ignoreCase = true) }
    }

    if (selectedProductForQuantity != null) {
        QuantityDialog(
            product = selectedProductForQuantity!!,
            onDismiss = { selectedProductForQuantity = null },
            onConfirm = { quantity ->
                val product = selectedProductForQuantity!!
                currentSaleItems.add(SaleItem(
                    invoiceId = 0,
                    productId = product.id,
                    productName = product.name,
                    quantity = quantity,
                    salePrice = product.salePrice,
                    purchasePrice = product.purchasePrice,
                    totalAmount = product.salePrice * quantity
                ))
                selectedProductForQuantity = null
                searchQuery = ""
            }
        )
    }

    if (showCustomItemDialog) {
        CustomItemDialog(
            onDismiss = { showCustomItemDialog = false },
            onConfirm = { name, purchasePrice, salePrice, quantity ->
                currentSaleItems.add(SaleItem(
                    invoiceId = 0,
                    productId = -1,
                    productName = name,
                    quantity = quantity,
                    salePrice = salePrice,
                    purchasePrice = purchasePrice,
                    totalAmount = salePrice * quantity
                ))
                showCustomItemDialog = false
                searchQuery = ""
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("New Sale", style = MaterialTheme.typography.headlineMedium)
        
        Box {
            OutlinedTextField(
                value = customerName, 
                onValueChange = { customerName = it }, 
                label = { Text("Customer Name") }, 
                modifier = Modifier.fillMaxWidth(), 
                leadingIcon = { Icon(Icons.Default.Person, null) }
            )
            if (filteredCustomers.isNotEmpty() && !customerNames.contains(customerName)) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 65.dp).heightIn(max = 150.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                    LazyColumn {
                        items(filteredCustomers) { name ->
                            ListItem(headlineContent = { Text(name) }, modifier = Modifier.clickable { customerName = name })
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = paymentMode == PaymentMode.CASH,
                onClick = { paymentMode = PaymentMode.CASH },
                label = { Text("Cash") },
                leadingIcon = if (paymentMode == PaymentMode.CASH) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) } } else null
            )
            FilterChip(
                selected = paymentMode == PaymentMode.CREDIT,
                onClick = { paymentMode = PaymentMode.CREDIT },
                label = { Text("Credit") },
                leadingIcon = if (paymentMode == PaymentMode.CREDIT) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) } } else null
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Product or Scan") },
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    IconButton(onClick = {
                        scanner.startScan().addOnSuccessListener { result ->
                            val code = result.rawValue ?: ""
                            scope.launch {
                                val prod = viewModel.getProductByBarcode(code)
                                if (prod != null) {
                                    // Add product directly without asking for quantity
                                    currentSaleItems.add(SaleItem(
                                        invoiceId = 0,
                                        productId = prod.id,
                                        productName = prod.name,
                                        quantity = 1.0,
                                        salePrice = prod.salePrice,
                                        purchasePrice = prod.purchasePrice,
                                        totalAmount = prod.salePrice * 1.0
                                    ))
                                    Toast.makeText(context, "${prod.name} added", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Product not found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) { Icon(Icons.Default.QrCodeScanner, null) }
                }
            )
            IconButton(onClick = { showCustomItemDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Custom Item", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (filteredProducts.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                LazyColumn {
                    items(filteredProducts) { product ->
                        ListItem(
                            headlineContent = { Text(product.name) },
                            supportingContent = { Text("Price: ₹${product.salePrice} | Stock: ${product.currentStock}") },
                            modifier = Modifier.clickable {
                                selectedProductForQuantity = product
                            }
                        )
                    }
                }
            }
        }

        Text("Current Items", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(currentSaleItems) { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.productName, fontWeight = FontWeight.Bold)
                        Text("₹${item.salePrice} x ${item.quantity} = ₹${String.format(Locale.getDefault(), "%.2f", item.totalAmount)}")
                    }
                    IconButton(onClick = { currentSaleItems.remove(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    }
                }
            }
        }

        if (currentSaleItems.isNotEmpty()) {
            val total = currentSaleItems.sumOf { it.totalAmount }
            val totalProfit = currentSaleItems.sumOf { it.profit }
            
            Button(
                onClick = {
                    viewModel.makeSale(
                        Invoice(
                            customerName = customerName.ifEmpty { "Guest" },
                            totalAmount = total,
                            totalProfit = totalProfit,
                            paymentMode = paymentMode
                        ),
                        currentSaleItems.toList()
                    )
                    customerName = ""; searchQuery = ""; currentSaleItems.clear(); paymentMode = PaymentMode.CASH
                    Toast.makeText(context, "Sale Completed", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Complete Sale (Total: ₹${String.format(Locale.getDefault(), "%.2f", total)})") }
        }
    }
}

@Composable
fun CustomItemDialog(onDismiss: () -> Unit, onConfirm: (String, Double, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = purchasePrice, onValueChange = { purchasePrice = it }, label = { Text("Purchase Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = salePrice, onValueChange = { salePrice = it }, label = { Text("Sale Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val pPrice = purchasePrice.toDoubleOrNull() ?: 0.0
                val sPrice = salePrice.toDoubleOrNull() ?: 0.0
                val q = quantity.toDoubleOrNull() ?: 1.0
                if (name.isNotEmpty() && sPrice > 0) {
                    onConfirm(name, pPrice, sPrice, q)
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun QuantityDialog(product: Product, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var quantity by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Quantity for ${product.name}") },
        text = {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Quantity (${product.unitType})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                val q = quantity.toDoubleOrNull() ?: 1.0
                if (q > 0) onConfirm(q)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun HistoryScreen(viewModel: KiranaViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val invoices by viewModel.allInvoices.collectAsState()
    
    val filteredInvoices = remember(searchQuery, invoices) {
        if (searchQuery.isEmpty()) invoices
        else invoices.filter { it.customerName.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sales History", style = MaterialTheme.typography.headlineMedium)
        
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            label = { Text("Search by Customer Name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredInvoices) { invoice ->
                InvoiceItem(invoice, viewModel)
            }
        }
    }
}

@Composable
fun InvoiceItem(invoice: Invoice, viewModel: KiranaViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<SaleItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
        expanded = !expanded 
        if (expanded && items.isEmpty()) {
            scope.launch { items = viewModel.getItemsForInvoice(invoice.id) }
        }
    }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(invoice.customerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(dateFormat.format(Date(invoice.date)), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹${String.format(Locale.getDefault(), "%.2f", invoice.totalAmount)}", color = if (invoice.paymentMode == PaymentMode.CREDIT) Color.Red else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(invoice.paymentMode.name, style = MaterialTheme.typography.bodySmall, color = if (invoice.paymentMode == PaymentMode.CREDIT) Color.Red else Color.Unspecified)
                    Text("Profit: ₹${String.format(Locale.getDefault(), "%.2f", invoice.totalProfit)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.productName} (x${item.quantity})", style = MaterialTheme.typography.bodyMedium)
                        Text("₹${String.format(Locale.getDefault(), "%.2f", item.totalAmount)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun CreditScreen(viewModel: KiranaViewModel) {
    val customers by viewModel.allCustomers.collectAsState()
    val invoices by viewModel.allInvoices.collectAsState()
    
    // Get all unique customer names from invoices to ensure we show everyone who has a credit history
    val invoiceCustomerNames = remember(invoices) {
        invoices.filter { it.paymentMode == PaymentMode.CREDIT }.map { it.customerName }.distinct()
    }
    
    // Combine customers from the database and names from invoices
    val allCustomerList = remember(customers, invoiceCustomerNames) {
        val list = customers.toMutableList()
        invoiceCustomerNames.forEach { name ->
            if (list.none { it.name == name }) {
                list.add(Customer(name = name))
            }
        }
        list.sortedBy { it.name }
    }
    
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddCustomerDialog = true }) {
                Icon(Icons.Default.Add, "Add Customer")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Credit Management", style = MaterialTheme.typography.headlineMedium)
            
            if (allCustomerList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No customers with credit history")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(allCustomerList) { customer ->
                        var balance by remember { mutableStateOf(0.0) }
                        LaunchedEffect(customer.name) {
                            val cust = viewModel.getCustomerByName(customer.name)
                            balance = cust?.balance ?: 0.0
                        }
                        
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedCustomer = customer }) {
                            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (customer.imageUri != null) {
                                        AsyncImage(
                                            model = customer.imageUri,
                                            contentDescription = null,
                                            modifier = Modifier.size(50.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(customer.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(customer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        if (customer.phoneNumber != null) {
                                            Text(customer.phoneNumber, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Balance", style = MaterialTheme.typography.labelSmall)
                                    Text("₹${String.format(Locale.getDefault(), "%.2f", balance)}", 
                                        color = if (balance > 0) Color.Red else Color(0xFF4CAF50),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedCustomer != null) {
        CustomerCreditDetailDialog(
            customer = selectedCustomer!!,
            viewModel = viewModel,
            onDismiss = { selectedCustomer = null }
        )
    }

    if (showAddCustomerDialog) {
        AddCustomerDialog(viewModel = viewModel, onDismiss = { showAddCustomerDialog = false })
    }
}

@Composable
fun AddCustomerDialog(viewModel: KiranaViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(80.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (imageUri != null) {
                        AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.AddAPhoto, null)
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    viewModel.updateCustomer(Customer(name = name, phoneNumber = phone, imageUri = imageUri?.toString()))
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CustomerCreditDetailDialog(customer: Customer, viewModel: KiranaViewModel, onDismiss: () -> Unit) {
    val invoices by viewModel.getInvoicesForCustomer(customer.name).collectAsState(initial = emptyList())
    val repayments by viewModel.getRepaymentsForCustomer(customer.name).collectAsState(initial = emptyList())
    
    val dbCustomer by produceState<Customer?>(initialValue = customer, key1 = customer.name) {
        viewModel.allCustomers.collect { list ->
            value = list.find { it.name == customer.name } ?: customer
        }
    }
    
    val balance = dbCustomer?.balance ?: 0.0
    
    var showRepayDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dbCustomer?.imageUri != null) {
                            AsyncImage(model = dbCustomer?.imageUri, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column {
                            Text(customer.name, style = MaterialTheme.typography.headlineSmall)
                            if (dbCustomer?.phoneNumber != null) {
                                Text(dbCustomer?.phoneNumber!!, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(colors = CardDefaults.cardColors(containerColor = if (balance > 0) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Current Balance Due", style = MaterialTheme.typography.labelMedium)
                        Text("₹${String.format(Locale.getDefault(), "%.2f", balance)}", 
                            style = MaterialTheme.typography.headlineMedium, 
                            fontWeight = FontWeight.Bold,
                            color = if (balance > 0) Color.Red else Color(0xFF4CAF50)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showRepayDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("Add Repayment")
                    }
                    OutlinedIconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Transaction History", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { Text("Invoices", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(invoices) { inv ->
                        var showItems by remember { mutableStateOf(false) }
                        var saleItems by remember { mutableStateOf<List<SaleItem>>(emptyList()) }
                        
                        LaunchedEffect(showItems) {
                            if (showItems && saleItems.isEmpty()) {
                                saleItems = viewModel.getItemsForInvoice(inv.id)
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { showItems = !showItems }) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${dateFormat.format(Date(inv.date))} (${inv.paymentMode})")
                                Text("₹${String.format(Locale.getDefault(), "%.2f", inv.totalAmount)}", color = if(inv.paymentMode == PaymentMode.CREDIT) Color.Red else Color.Unspecified)
                            }
                            if (showItems) {
                                Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                                    saleItems.forEach { item ->
                                        Text("${item.productName} x ${item.quantity} = ₹${item.totalAmount}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Repayments", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(repayments) { rep ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateFormat.format(Date(rep.date)))
                            Text("-₹${String.format(Locale.getDefault(), "%.2f", rep.amount)}", color = Color(0xFF4CAF50))
                        }
                    }
                }
            }
        }
    }

    if (showRepayDialog) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRepayDialog = false },
            title = { Text("Add Repayment") },
            text = {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        viewModel.makeRepayment(customer.name, amt)
                        showRepayDialog = false
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showRepayDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Customer?") },
            text = { Text("Are you sure you want to delete ${customer.name}? This will remove their contact details and balance info.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteCustomer(dbCustomer ?: customer)
                    showDeleteConfirm = false
                    onDismiss()
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsScreen(viewModel: KiranaViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupSuccessMessage by remember { mutableStateOf<String?>(null) }
    var backupErrorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    viewModel.onUserChanged(result.user)
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (currentUser != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = currentUser?.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(25.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(currentUser?.displayName ?: "User", style = MaterialTheme.typography.titleMedium)
                            Text(currentUser?.email ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                            viewModel.onUserChanged(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign Out") }
                } else {
                    Text("Sync your data across devices", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(context.getString(R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                            val client = GoogleSignIn.getClient(context, gso)
                            launcher.launch(client.signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Login, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google")
                    }
                }
            }
        }

        if (currentUser != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cloud Backup & Sync", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (currentUser == null) {
                        Text("Please sign in to use backup.", color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = {
                            viewModel.syncToCloud { result ->
                                if (result.startsWith("VERIFIED")) {
                                    backupSuccessMessage = result.removePrefix("VERIFIED\n")
                                } else {
                                    backupErrorMessage = result
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup to Cloud")
                    }

                    // Success dialog — shown after backup is verified
                    backupSuccessMessage?.let { msg ->
                        AlertDialog(
                            onDismissRequest = { backupSuccessMessage = null },
                            icon = { Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            title = { Text("Backup Verified") },
                            text = {
                                Column {
                                    Text("Your data has been successfully backed up to the cloud and verified:", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    msg.lines().forEach { line ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(line, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { backupSuccessMessage = null }) { Text("OK") }
                            }
                        )
                    }

                    // Error dialog — replaces the easy-to-miss Toast for backup errors
                    backupErrorMessage?.let { err ->
                        AlertDialog(
                            onDismissRequest = { backupErrorMessage = null },
                            icon = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            title = { Text("Backup Failed") },
                            text = { Text(err, style = MaterialTheme.typography.bodyMedium) },
                            confirmButton = {
                                TextButton(onClick = { backupErrorMessage = null }) { Text("OK") }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { 
                            viewModel.syncFromCloud { result ->
                                if (result == "Success") {
                                    Toast.makeText(context, "Restore complete!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                }
                            } 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore from Cloud")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SettingsSuggest, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fix 'Offline' Error (App Info)")
                    }
                    Text("If it says 'Offline', go to App Info > Data Usage and enable 'Background Data' & 'Unrestricted Data'.", 
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

