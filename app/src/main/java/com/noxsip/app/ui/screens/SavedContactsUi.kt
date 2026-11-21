package com.noxsip.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.data.models.SavedContact
import com.noxsip.app.ui.theme.Green500
import com.noxsip.app.ui.theme.Indigo600
import com.noxsip.app.ui.theme.Red500

/** কল লগ বা ফোনবুক থেকে "Client/Office-এ যোগ" অনুরোধ */
data class AddContactRequest(val category: String, val number: String, val name: String)

private val WaGreen = Color(0xFF16A34A)
private val TgBlue = Color(0xFF2AABEE)

fun categoryLabel(category: String) =
    if (category == SavedContact.CATEGORY_OFFICE) "Office" else "Client"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedContactsTab(
    category: String,
    contacts: List<SavedContact>,
    totalInCategory: Int,
    onAdd: () -> Unit,
    onCall: (SavedContact) -> Unit,
    onEdit: (SavedContact) -> Unit,
    onDelete: (SavedContact) -> Unit,
    onOpenChat: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = categoryLabel(category)
    var menuFor by remember { mutableStateOf<SavedContact?>(null) }
    var detailsFor by remember { mutableStateOf<SavedContact?>(null) }
    var deleteFor by remember { mutableStateOf<SavedContact?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$totalInCategory $label contact" + if (totalInCategory == 1) "" else "s",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Indigo600)
                    .clickable { onAdd() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add $label", tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Add", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Indigo600.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Indigo600, modifier = Modifier.size(26.dp))
                    }
                    Text(
                        text = if (totalInCategory == 0) "No $label contacts yet" else "No matching contacts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (totalInCategory == 0) {
                        Text(
                            "Tap Add, or long-press a number in the call log",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(contacts, key = { _, c -> c.id }) { index, c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onCall(c) }, onLongClick = { menuFor = c })
                            .padding(horizontal = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 30).coerceIn(10, 22).dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.size(30.dp).clip(CircleShape).background(Indigo600.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = c.name.firstOrNull { it.isLetter() }?.uppercaseChar()
                                if (initial != null) {
                                    Text(initial.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Indigo600)
                                } else {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = Indigo600, modifier = Modifier.size(20.dp))
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = c.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = c.number,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    if (c.hasWhatsapp) MiniBadge("WA", WaGreen)
                                    if (c.hasTelegram) MiniBadge("TG", TgBlue)
                                }
                            }
                        }
                        IconButton(onClick = { onCall(c) }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Filled.Call, contentDescription = "Call", tint = Green500, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (index < contacts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }

    // লং-প্রেস মেনু: Edit / View details / Delete
    menuFor?.let { c ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    MenuItem(Icons.Filled.Visibility, "View details", MaterialTheme.colorScheme.onSurface) {
                        menuFor = null; detailsFor = c
                    }
                    MenuItem(Icons.Filled.Edit, "Edit", MaterialTheme.colorScheme.onSurface) {
                        menuFor = null; onEdit(c)
                    }
                    MenuItem(Icons.Filled.Delete, "Delete", Red500) {
                        menuFor = null; deleteFor = c
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuFor = null }) { Text("Cancel") } }
        )
    }

    deleteFor?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete contact?") },
            text = { Text("${c.name} will be removed from your $label list. Your phone contacts are not affected.") },
            confirmButton = {
                TextButton(onClick = { deleteFor = null; onDelete(c) }) { Text("Delete", color = Red500) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } }
        )
    }

    detailsFor?.let { c ->
        SavedContactDetailsDialog(
            contact = c,
            onDismiss = { detailsFor = null },
            onCall = { detailsFor = null; onCall(c) },
            onEdit = { detailsFor = null; onEdit(c) },
            onOpenChat = onOpenChat
        )
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SavedContactDetailsDialog(
    contact: SavedContact,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(contact.name, fontWeight = FontWeight.Bold)
                Text(
                    categoryLabel(contact.category),
                    fontSize = 12.sp,
                    color = Indigo600,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow("Number", contact.number)
                DetailRow("Company", contact.company)
                DetailRow("Email", contact.email)
                DetailRow("Address", contact.address)
                DetailRow("Birthday", contact.birthday)
                DetailRow("Notes", contact.notes)
                if (contact.hasWhatsapp || contact.hasTelegram) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (contact.hasWhatsapp) {
                            OutlinedButton(onClick = { onOpenChat("whatsapp", contact.number) }) {
                                Text("WhatsApp", color = WaGreen, fontSize = 13.sp)
                            }
                        }
                        if (contact.hasTelegram) {
                            OutlinedButton(onClick = { onOpenChat("telegram", contact.number) }) {
                                Text("Telegram", color = TgBlue, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCall) { Text("Call") }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** নতুন যোগ বা এডিটের ফর্ম (id == 0 হলে নতুন) */
@Composable
fun SavedContactFormDialog(
    draft: SavedContact,
    detectMessengers: suspend (String) -> Pair<Boolean, Boolean>,
    onSave: (SavedContact) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var number by remember(draft) { mutableStateOf(draft.number) }
    var company by remember(draft) { mutableStateOf(draft.company) }
    var email by remember(draft) { mutableStateOf(draft.email) }
    var address by remember(draft) { mutableStateOf(draft.address) }
    var birthday by remember(draft) { mutableStateOf(draft.birthday) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    var wa by remember(draft) { mutableStateOf(draft.hasWhatsapp) }
    var tg by remember(draft) { mutableStateOf(draft.hasTelegram) }
    var autoNote by remember(draft) { mutableStateOf<String?>(null) }

    // ফোনবুকে নম্বর মিললে WhatsApp/Telegram অটো টিক (শুধু চালু করে, বন্ধ করে না)
    LaunchedEffect(number) {
        if (number.filter { it.isDigit() }.length >= 7) {
            kotlinx.coroutines.delay(400)
            val (w, t) = detectMessengers(number)
            var found = false
            if (w && !wa) { wa = true; found = true }
            if (t && !tg) { tg = true; found = true }
            if (found) autoNote = "Detected from your phone contacts"
        }
    }

    val isNew = draft.id == 0L
    val canSave = name.isNotBlank() && number.filter { it.isDigit() }.length >= 3

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = (if (isNew) "Add " else "Edit ") + categoryLabel(draft.category) + " contact",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormField(name, { name = it }, "Name *")
                    FormField(number, { number = it }, "Number *", KeyboardType.Phone)
                    FormField(company, { company = it }, "Company")
                    FormField(email, { email = it }, "Email", KeyboardType.Email)
                    FormField(address, { address = it }, "Address")
                    FormField(birthday, { birthday = it }, "Birthday (DD/MM/YYYY)", KeyboardType.Number)
                    FormField(notes, { notes = it }, "Notes", singleLine = false)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Has WhatsApp", fontSize = 14.sp)
                        Switch(checked = wa, onCheckedChange = { wa = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Has Telegram", fontSize = 14.sp)
                        Switch(checked = tg, onCheckedChange = { tg = it })
                    }
                    autoNote?.let { Text(it, fontSize = 11.sp, color = Green500) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                draft.copy(
                                    name = name.trim(),
                                    number = number.trim(),
                                    company = company.trim(),
                                    email = email.trim(),
                                    address = address.trim(),
                                    birthday = birthday.trim(),
                                    notes = notes.trim(),
                                    hasWhatsapp = wa,
                                    hasTelegram = tg
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth()
    )
}
