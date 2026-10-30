package com.linku.soundspeedexp

import android.annotation.SuppressLint
        import android.bluetooth.BluetoothAdapter
        import android.bluetooth.BluetoothSocket
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.withContext
        import java.io.IOException
        import java.nio.ByteBuffer
        import java.nio.ByteOrder
        import java.util.UUID

        data class Measurement(
    val soundSpeed: Float,
    val temperature: Float
)

class BluetoothManager(
    private val adapter: BluetoothAdapter
) {
    companion object {
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String) = withContext(Dispatchers.IO) {
        check(adapter.isEnabled) { "블루투스가 비활성화되어 있습니다." }

        disconnect()

        val device = adapter.getRemoteDevice(address)
        val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)

        try {
            newSocket.connect()
            socket = newSocket
        } catch (e: IOException) {
            try {
                newSocket.close()
            } catch (_: IOException) {
            }
            throw e
        }
    }

    suspend fun receiveMeasurements(): List<Measurement> =
        withContext(Dispatchers.IO) {
            val s = socket ?: error("블루투스가 연결되지 않았습니다.")
            val input = s.inputStream
            val output = s.outputStream

            val measurements = mutableListOf<Measurement>()
            val buffer = ByteArray(8)
            var offset = 0

            output.write('D'.code)
            output.flush()

            while (true) {
                val value = input.read()
                if (value == -1) {
                    throw IOException("블루투스 연결이 종료되었습니다.")
                }

                if (offset == 0 && value == 'E'.code) {
                    break
                }

                buffer[offset++] = value.toByte()

                if (offset == buffer.size) {
                    val data = ByteBuffer.wrap(buffer)
                        .order(ByteOrder.LITTLE_ENDIAN)

                    val speed = data.float
                    val temperature = data.float

                    if (speed != 0f && temperature != 0f) {
                        measurements.add(
                            Measurement(speed, temperature)
                        )
                    }

                    offset = 0
                }
            }

            measurements
        }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            socket?.close()
        } finally {
            socket = null
        }
    }
}
