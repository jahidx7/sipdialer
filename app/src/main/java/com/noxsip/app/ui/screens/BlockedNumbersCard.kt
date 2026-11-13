package com.noxsip.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noxsip.app.ui.theme.Red500
import com.noxsip.app.util.BlockList

/** Settings → Blocked numbers: তালিকা দেখা, নম্বর যোগ করা, আনব্লক করা */
@Composable
fun BlockedNumbersCard() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { BlockList.init(context) }
    val numbers by BlockList.numbers.collectAsState()
    var input by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Calls from blocked numbers are rejected without ringing and appear in the call list as missed. " +
                    "Their messages still arrive, but without a notification.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() || c == '+' } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Number to block") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Button(
                    enabled = input.filter { it.isDigit() }.length >= 3,
                    onClick = { BlockList.add(context, input); input = "" },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Block") }
            }
            if (numbers.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No blocked numbers", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("${numbers.size} blocked", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                numbers.sorted().forEach { n ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(n, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { BlockList.remove(context, n) }) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Red500, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unblock", color = Red500)
                        }
                    }
                }
            }
        }
    }
}
