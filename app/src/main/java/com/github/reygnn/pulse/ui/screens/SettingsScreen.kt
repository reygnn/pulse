package com.github.reygnn.pulse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.reygnn.pulse.workout.UserProfile

@Composable
fun SettingsSheet(
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var age by remember { mutableStateOf(profile.age.toString()) }
    var weight by remember { mutableStateOf(profile.weightKg.toInt().toString()) }
    var isMale by remember { mutableStateOf(profile.isMale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val newProfile = UserProfile(
                        age = age.toIntOrNull()?.coerceIn(10, 99) ?: 30,
                        weightKg = weight.toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: 75.0,
                        isMale = isMale
                    )
                    onSave(newProfile)
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
        icon = {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Profil", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    text = "Für eine genaue Kalorienberechnung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Gender toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Geschlecht",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )

                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = isMale,
                            onClick = { isMale = true },
                            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                        ) {
                            Text("Mann")
                        }
                        SegmentedButton(
                            selected = !isMale,
                            onClick = { isMale = false },
                            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                        ) {
                            Text("Frau")
                        }
                    }
                }

                // Age
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Alter") },
                    suffix = { Text("Jahre") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Weight
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Gewicht") },
                    suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Calculated info
                val ageInt = age.toIntOrNull() ?: 30
                val estimatedMaxHr = (208 - 0.7 * ageInt).toInt()

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.MonitorHeart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Geschätzte max. HR: $estimatedMaxHr bpm",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    )
}