
package com.linku.soundspeedexp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var bluetooth: BluetoothManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val systemBluetooth =
            getSystemService(android.bluetooth.BluetoothManager::class.java)

        bluetooth = BluetoothManager(systemBluetooth.adapter)

        setContent {
            MaterialTheme {
                BluetoothScreen(bluetooth)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BluetoothScreen(bluetooth: BluetoothManager) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("블루투스 권한을 확인해 주세요.") }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var measurements by remember { mutableStateOf<List<Measurement>>(emptyList()) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(createCsv(measurements).toByteArray(Charsets.UTF_8))
                }
                status = "CSV 저장 완료"
            } catch (e: Exception) {
                status = "CSV 저장 실패: ${e.message}"
            }
        }
    }

    fun loadDevices() {
        val adapter =
            context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                .adapter

        if (adapter == null) {
            status = "이 기기는 블루투스를 지원하지 않습니다."
            return
        }

        if (!adapter.isEnabled) {
            status = "휴대폰 블루투스를 켜 주세요."
            return
        }

        devices = adapter.bondedDevices.toList()
        status = if (devices.isEmpty()) {
            "페어링된 기기가 없습니다. 휴대폰 설정에서 HC-05/HC-06을 페어링해 주세요."
        } else {
            "기기를 선택해 주세요."
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                loadDevices()
            } else {
                status = "블루투스 연결 권한이 필요합니다."
            }
        }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            loadDevices()
        } else {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("음속 측정 실험", style = MaterialTheme.typography.headlineSmall)
        Text(status)

        Button(
            onClick = { loadDevices() },
            enabled = !busy
        ) {
            Text("페어링된 기기 새로고침")
        }

        Text("기기 목록", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(devices) { device ->
                val address = device.address
                val name = device.name ?: "이름 없는 기기"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name)
                        Text(address, style = MaterialTheme.typography.bodySmall)
                    }

                    RadioButton(
                        selected = selectedAddress == address,
                        onClick = { selectedAddress = address }
                    )
                }
            }
        }

        Button(
            onClick = {
                val address = selectedAddress ?: return@Button

                scope.launch {
                    busy = true
                    status = "연결 중..."

                    try {
                        bluetooth.connect(address)
                        connected = true
                        status = "블루투스 연결 완료"
                    } catch (e: Exception) {
                        connected = false
                        status = "연결 실패: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = selectedAddress != null && !connected && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("연결")
        }

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    status = "데이터 수신 중..."

                    try {
                        measurements = bluetooth.receiveMeasurements()
                        status = "수신 완료: ${measurements.size}개 레코드"
                    } catch (e: Exception) {
                        status = "수신 실패: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = connected && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("데이터 수신")
        }

        Text("수신 데이터: ${measurements.size}개")

        measurements.takeLast(5).forEachIndexed { _, item ->
            Text(
                "음속: ${item.soundSpeed} cm/s, " +
                        "온도: ${item.temperature} °C"
            )
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        bluetooth.disconnect()
                        connected = false
                        status = "연결 해제됨"
                    } catch (e: Exception) {
                        status = "연결 해제 오류: ${e.message}"
                    }
                }
            },
            enabled = connected && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("연결 해제")
        }
        Button(
            onClick = {
                val timestamp = SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(Date())

                csvLauncher.launch("sound_speed_$timestamp.csv")
            },
            enabled = measurements.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CSV로 저장")
        }
    }
}

fun createCsv(measurements: List<Measurement>): String {
    val csv = StringBuilder()
    csv.append("sound_speed_cm_s,temperature_c\n")

    measurements
        .filter { it.soundSpeed != 0f && it.temperature != 0f }
        .forEach { measurement ->
            csv.append("${measurement.soundSpeed},${measurement.temperature}\n")
        }

    return csv.toString()
}
