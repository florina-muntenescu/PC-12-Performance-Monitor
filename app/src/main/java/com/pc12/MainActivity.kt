package com.pc12

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pc12.ui.theme.Cyan
import com.pc12.ui.theme.PC12PerformanceMonitorTheme
import kotlinx.coroutines.launch
import java.lang.Float.NaN

class MainActivity : ComponentActivity() {
    private val flightDataViewModel by viewModels<FlightDataViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            window.statusBarColor = Cyan.toArgb()
            PC12PerformanceMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    if (!flightDataViewModel.getUserAgreedTerms()) {
                        FirstRunDialog({
                                flightDataViewModel.setUserAgreedTerms()
                                flightDataViewModel.startNetworkRequests()
                            },{
                                finish()
                            }
                        )
                    }
                    OverflowMenu()
                    PerformanceMonitorScreen(flightDataViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        flightDataViewModel.startNetworkRequests()
    }

    override fun onPause() {
        super.onPause()
        flightDataViewModel.stopNetworkRequests()
    }
}

@Composable
fun PerformanceMonitorScreen(flightDataViewModel: FlightDataViewModel) {
    PerformanceDataDisplay(flightDataViewModel.uiState.avionicsData.altitude,
                           flightDataViewModel.uiState.avionicsData.outsideTemp,
                           flightDataViewModel.uiState.perfData.torque,
                           flightDataViewModel.uiState.age)
}

@Composable
fun PerformanceDataDisplay(altitude: Int, outsideTemp: Int, torque: Float, age: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Column {
            val MAXAGE = 300  // 5 mins
            val statusColor = if (age > MAXAGE || torque.isNaN()) Color(200, 0, 0) else Color(30, 140, 100)
            val altitudeStr = (if (torque.isNaN()) "---" else altitude)
            val outsideTempStr = (if (torque.isNaN()) "---" else outsideTemp)
            val torqueStr = (if (torque.isNaN() || age > MAXAGE) "---" else torque)
            val ageStr = if (age > 60) (age / 60).toString() + " min" else "$age sec"

            OutlinedTextField(
                value = "Altitude: $altitudeStr ft\nSAT: $outsideTempStr \u2103",
                onValueChange = { },
                label = { Text("Avionics Data" + if (!torque.isNaN()) " ($ageStr)" else "") },
                enabled = false,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    disabledTextColor = (if (isSystemInDarkTheme()) Color.White else Color.Black),
                    disabledBorderColor = statusColor,
                    disabledLabelColor = statusColor,
                ),
                textStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp),
            )

            Spacer(modifier = Modifier.height(50.dp))

            OutlinedTextField(
                value = "TRQ: $torqueStr psi",
                onValueChange = { },
                label = {
                    Text("Max Cruise")
                },
                enabled = false,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    disabledTextColor = (if (isSystemInDarkTheme()) Color.White else Color.Black),
                    disabledBorderColor = statusColor,
                    disabledLabelColor = statusColor,
                ),
                textStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp),
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun OverflowMenu() {
    val expanded = remember { mutableStateOf(false) }
    val scaffoldState = rememberScaffoldState()

    Scaffold(
        scaffoldState = scaffoldState,
        content = { },
        topBar = {
            TopAppBar(
                title = { Text("PC-12 Performance Monitor") },
                backgroundColor = Cyan,
                contentColor = Color.White,
                actions = {
                    IconButton(
                        onClick = {
                            expanded.value = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Aircraft Type",
                            tint = Color.White,
                        )
                    }
                    DropDownMenuItems(expanded = expanded.value) { expanded.value = it }
                }
            )
        }
    )
}

@Composable
fun DropDownMenuItems(expanded: Boolean, updateExpanded: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dataStore = remember { AircraftTypeStore(context)  }
    val aircraftTypeFlow = dataStore.aircraftTypeFlow.collectAsState(initial = PerfCalculator.PC_12_47E_MSN_1576_1942_5_Blade)

    val menuItems = listOf(
        dataStore.aircraftTypeToString(PerfCalculator.PC_12_47E_MSN_1451_1942_4_Blade),
        dataStore.aircraftTypeToString(PerfCalculator.PC_12_47E_MSN_1576_1942_5_Blade),
        dataStore.aircraftTypeToString(PerfCalculator.PC_12_47E_MSN_2001_5_Blade)
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { updateExpanded(false) }) {
        menuItems.forEachIndexed { index, item ->
            DropdownMenuItem(onClick = {
                scope.launch {
                    dataStore.saveAircraftModel(index)
                }
                updateExpanded(false)
            }) {
                Text(text = item, Modifier.weight(1f))
                if (index == aircraftTypeFlow.value) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FirstRunDialog(onStart: () -> Unit, onFinish: () -> Unit) {
    val firstRun = remember { mutableStateOf(true)  }

    if (firstRun.value) {
        val isTermsChecked = remember { mutableStateOf(false) }
        AlertDialog(
            title = {
                Text(text = "Warning", fontWeight = FontWeight.Bold)
            },
            backgroundColor = Color(200,0,0),
            contentColor = Color.White,
            text = {
                Column {
                    Text(
                        "THIS APP IS FOR DEMO PURPOSES ONLY. It must not be used to set engine " +
                                "torque. Always refer to the manufacturer's QRH or AFM for " +
                                "authoritative engine settings.\n\n" +
                                "LIMITATION OF LIABILITY: In no event shall the author(s) of this app " +
                                "be held responsible for any engine or aircraft damage, " +
                                "consequential / indirect / special damages, or loss of profit or revenue " +
                                "resulting from the use of this app.\n",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row {
                        Text(
                            text = "I agree to these terms & conditions ",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,)
                        Checkbox(
                            checked = isTermsChecked.value,
                            onCheckedChange = { isTermsChecked.value = it },
                            enabled = true,
                            colors = CheckboxDefaults.colors(uncheckedColor = Color.White,
                                                             checkedColor = Color.White,
                                                             checkmarkColor = Color.Black)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = isTermsChecked.value,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = Color.Black),
                    onClick = {
                        onStart()
                        firstRun.value = false
                    }) {
                    Text("PROCEED")
                }
            },
            dismissButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = Color.Black),
                    onClick = {
                        onFinish()
                    }) {
                    Text("CANCEL")
                }
            },
            onDismissRequest = { },
        )
    }
}

@Preview(showBackground = true,  heightDp = 600, widthDp = 400)
@Composable
fun DefaultPreview() {
    PC12PerformanceMonitorTheme {
        OverflowMenu()
        PerformanceDataDisplay(24000, -32, 30.1f, 5)
    }
}
