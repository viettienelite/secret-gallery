package com.example.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.CryptoEngine
import com.example.ui.VaultViewModel

/**
 * Tách thông tin hiển thị từ Tree Uri của Vault:
 * - first: Tên folder cuối cùng (nơi chứa trực tiếp các file mã hoá)
 * - second: Đường dẫn (path) đầy đủ của folder đó
 */
private fun parseVaultDisplayInfo(uri: Uri): Pair<String, String> {
    val docId = try {
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.getTreeDocumentId(uri)
        } else {
            DocumentsContract.getDocumentId(uri)
        }
    } catch (_: Exception) {
        uri.lastPathSegment ?: uri.path ?: uri.toString()
    }

    val volume = docId.substringBefore(':', "")
    val relPath = docId.substringAfter(':', docId).trim('/')
    val lastFolderName = relPath.substringAfterLast('/').ifEmpty {
        volume.ifEmpty { "Vault Folder" }
    }

    val displayPath = when {
        volume.equals("primary", ignoreCase = true) -> {
            if (relPath.isNotEmpty()) "/storage/emulated/0/$relPath" else "/storage/emulated/0"
        }
        volume.equals("raw", ignoreCase = true) -> relPath
        volume.isNotEmpty() && volume != docId -> {
            if (relPath.isNotEmpty()) "/storage/$volume/$relPath" else "/storage/$volume"
        }
        else -> if (relPath.startsWith("/")) relPath else "/$relPath"
    }

    return lastFolderName to displayPath
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedVaults by viewModel.savedVaults.collectAsState()
    val vaultUri by viewModel.vaultUri.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // State lưu Vault đang chờ xác nhận xoá khỏi danh sách ghi nhớ
    var vaultUriPendingRemoval by remember { mutableStateOf<Uri?>(null) }

    // Nếu đang ở màn hình nhập password của 1 Vault đã chọn, vuốt Back sẽ quay về danh sách Vault đã ghi nhớ
    BackHandler(enabled = vaultUri != null && !isLoading) {
        viewModel.setVaultUri(context, null)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.setVaultUri(context, uri)
        }
    }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Reset fields on folder change
    LaunchedEffect(vaultUri) {
        password = ""
        confirmPassword = ""
        showPassword = false
    }

    // Hiệu ứng nhịp thở nhẹ nhàng cho vòng sáng quanh biểu tượng Lock
    val infiniteTransition = rememberInfiniteTransition(label = "hero_lock_pulse")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .align(Alignment.Center)
        ) {
            // App Hero Header với vòng sáng êm ái
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(88.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = haloScale
                            scaleY = haloScale
                            alpha = haloAlpha
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    Color.Transparent
                                )
                            )
                        )
                )
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Vault Lock Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }

            Text(
                text = "SECRET GALLERY",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text(
                text = "Zero-Knowledge, Stateless Media Vault",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            // Chuyển cảnh mượt mà giữa Danh sách Saved Vaults (targetUri == null) và Ô nhập Password (targetUri != null)
            AnimatedContent(
                targetState = vaultUri,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth / 4 },
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(240)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(180))
                            )
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(240)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> fullWidth / 4 },
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(180))
                            )
                    }
                },
                label = "welcome_step_transition",
                modifier = Modifier.fillMaxWidth()
            ) { selectedUri ->
                if (selectedUri == null) {
                    // Step 1: Trang chủ hiển thị danh sách các Vault đã ghi nhớ
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (savedVaults.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Saved Vaults",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${savedVaults.size} remembered",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Tap a remembered vault below to enter its master password, or add another folder.",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(savedVaults, key = { it.toString() }) { uriItem ->
                                        val (itemFolderName, itemFolderPath) = remember(uriItem) {
                                            parseVaultDisplayInfo(uriItem)
                                        }
                                        val itemConfigured = remember(uriItem) {
                                            CryptoEngine.hasVaultConfig(context, uriItem)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateItem()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    viewModel.setVaultUri(context, uriItem)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                                .testTag("saved_vault_item_$itemFolderName"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (itemConfigured) Icons.Filled.Lock else Icons.Filled.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Dòng 1: Tên folder cuối (chứa trực tiếp file mã hoá)
                                            // Dòng 2: Đường dẫn (path) của folder đó
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = itemFolderName,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = itemFolderPath,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            IconButton(
                                                onClick = { vaultUriPendingRemoval = uriItem },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("forget_vault_button_$itemFolderName")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Forget Vault",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                OutlinedButton(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("select_vault_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CreateNewFolder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ADD / SELECT ANOTHER FOLDER",
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                // Chưa có Vault nào được ghi nhớ
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = "Folder Icon",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(48.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Choose Vault Location",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Select an empty external directory or an existing Vault folder. All encrypted images, videos, and filenames will live entirely inside this selected directory.",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("select_vault_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = "SELECT FOLDER",
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Step 2: Người dùng đã click vào 1 Vault cụ thể -> Hiện ô nhập Master Password
                    val (folderName, _) = remember(selectedUri) {
                        parseVaultDisplayInfo(selectedUri)
                    }
                    val isVaultConfigured = remember(selectedUri) {
                        CryptoEngine.hasVaultConfig(context, selectedUri)
                    }

                    val submitAction = {
                        if (!isLoading && password.isNotEmpty() && (isVaultConfigured || confirmPassword.isNotEmpty())) {
                            if (isVaultConfigured) {
                                viewModel.unlockVault(context, password)
                            } else {
                                if (password != confirmPassword) {
                                    viewModel.setError("Passwords do not match.")
                                } else {
                                    viewModel.createVault(context, password)
                                }
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Hàng đầu tiên: Nút mũi tên Back ở góc trên bên trái + Nhãn tên thư mục ở chính giữa
                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { viewModel.setVaultUri(context, null) },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .testTag("change_folder_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to saved vaults",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = "Folder",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = folderName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = if (isVaultConfigured) "Unlock Your Vault" else "Initialize New Vault",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (isVaultConfigured) {
                                    "Enter the master password to derive the decryption key and open your media gallery."
                                } else {
                                    "This folder does not contain a vault configuration. Set a strong password below to initialize a secure cryptobox."
                                },
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Password Field
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    if (error != null) viewModel.clearError()
                                },
                                enabled = !isLoading,
                                label = { Text("Master Password") },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = if (isVaultConfigured) ImeAction.Done else ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitAction() }
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        AnimatedContent(
                                            targetState = showPassword,
                                            transitionSpec = {
                                                (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
                                                    .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.8f, animationSpec = tween(120)))
                                            },
                                            label = "password_eye_toggle"
                                        ) { visible ->
                                            Icon(
                                                imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (visible) "Hide password" else "Show password"
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Confirm Password Field (only for initialization)
                            AnimatedVisibility(
                                visible = !isVaultConfigured,
                                enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
                                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(180))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = {
                                            confirmPassword = it
                                            if (error != null) viewModel.clearError()
                                        },
                                        enabled = !isLoading,
                                        label = { Text("Confirm Password") },
                                        singleLine = true,
                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { submitAction() }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("confirm_password_input"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            // Error Banner
                            AnimatedVisibility(
                                visible = error != null,
                                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                                exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(tween(160))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Error,
                                            contentDescription = "Error",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = error ?: "",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { submitAction() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("unlock_create_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = if (isLoading) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    }
                                ),
                                enabled = !isLoading && password.isNotEmpty() && (isVaultConfigured || confirmPassword.isNotEmpty())
                            ) {
                                AnimatedContent(
                                    targetState = isLoading,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180)))
                                            .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.92f, animationSpec = tween(140)))
                                    },
                                    label = "unlock_button_loading_state"
                                ) { loading ->
                                    if (loading) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = if (isVaultConfigured) "UNLOCKING VAULT..." else "INITIALIZING...",
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                maxLines = 1
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = if (isVaultConfigured) "UNLOCK VAULT" else "INITIALIZE VAULT",
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hộp thoại xác nhận trước khi xoá Vault khỏi danh sách đã ghi nhớ
        vaultUriPendingRemoval?.let { pendingUri ->
            val (pendingFolderName, pendingFolderPath) = remember(pendingUri) {
                parseVaultDisplayInfo(pendingUri)
            }
            AlertDialog(
                onDismissRequest = { vaultUriPendingRemoval = null },
                title = {
                    Text(
                        text = "Remove Saved Vault?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Remove \"$pendingFolderName\" ($pendingFolderPath) from your saved vaults list? Encrypted files inside the folder will remain untouched on your device."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeSavedVault(context, pendingUri)
                            vaultUriPendingRemoval = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_forget_vault_button")
                    ) {
                        Text("REMOVE", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vaultUriPendingRemoval = null }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}