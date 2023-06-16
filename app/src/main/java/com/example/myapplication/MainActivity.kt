package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.util.Xml
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.StringWriter
import java.util.*

class MainActivity: AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var locationManager: LocationManager
    private var accelerometerDataList: MutableList<FloatArray> = mutableListOf()
    private var gpsDataList: MutableList<Location> = mutableListOf()
    private var isTracking: Boolean = false

    companion object {
        private const val PERMISSION_REQUEST_LOCATION = 100
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val startButton: Button = findViewById(R.id.startButton)
        val stopButton: Button = findViewById(R.id.stopButton)
        val exportButton: Button = findViewById(R.id.exportButton)

        startButton.setOnClickListener {
            startTracking()
        }

        stopButton.setOnClickListener {
            stopTracking()
        }

        exportButton.setOnClickListener {
            exportGpx()
        }

        // Solicitar permissão de localização
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSION_REQUEST_LOCATION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Não é necessário implementar este método
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val values = event.values.clone()
            accelerometerDataList.add(values)
        }
    }

    override fun onLocationChanged(location: Location) {
        location?.let {
            gpsDataList.add(location)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permissão de localização concedida, você pode iniciar o rastreamento aqui
                    startTracking()
                } else {
                    // Permissão de localização negada, você pode lidar com isso de acordo com sua lógica de aplicativo
                    Log.d(TAG, "Permissão de localização negada.")
                }
            }
        }
    }

    private fun startTracking() {
        accelerometerDataList.clear()
        gpsDataList.clear()

        isTracking = true

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0,
                0f,
                this
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Erro ao solicitar atualizações de localização: ${e.message}")
            stopTracking()
        }

        val startButton: Button = findViewById(R.id.startButton)
        startButton.isEnabled = false
    }

    private fun stopTracking() {
        isTracking = false

        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            Log.e(TAG, "Erro ao remover atualizações de localização: ${e.message}")
        }

        val startButton: Button = findViewById(R.id.startButton)
        startButton.isEnabled = true
    }

    private fun exportGpx() {
        val xmlSerializer = Xml.newSerializer()
        val writer = StringWriter()

        xmlSerializer.setOutput(writer)

        try {
            xmlSerializer.startDocument("UTF-8", true)
            xmlSerializer.startTag(null, "gpx")
            xmlSerializer.attribute(null, "version", "1.1")
            xmlSerializer.attribute(null, "samu", "Corrida das notas")

            for (i in 0 until accelerometerDataList.size.coerceAtMost(gpsDataList.size)) {
                val acceleration = accelerometerDataList[i]
                val location = gpsDataList[i]

                xmlSerializer.startTag(null, "trkpt")
                xmlSerializer.attribute(null, "lat", location.latitude.toString())
                xmlSerializer.attribute(null, "lon", location.longitude.toString())

                xmlSerializer.startTag(null, "ele")
                xmlSerializer.text("0.0")
                xmlSerializer.endTag(null, "ele")

                xmlSerializer.startTag(null, "time")
                xmlSerializer.text(Date().toString())
                xmlSerializer.endTag(null, "time")

                xmlSerializer.startTag(null, "extensions")
                xmlSerializer.startTag(null, "acceleration")
                xmlSerializer.attribute(null, "x", acceleration[0].toString())
                xmlSerializer.attribute(null, "y", acceleration[1].toString())
                xmlSerializer.attribute(null, "z", acceleration[2].toString())
                xmlSerializer.endTag(null, "acceleration")
                xmlSerializer.endTag(null, "extensions")

                xmlSerializer.endTag(null, "trkpt")
            }

            xmlSerializer.endTag(null, "gpx")
            xmlSerializer.endDocument()

            val gpxData = writer.toString()

            val outputFile = File(externalCacheDir, "track.gpx")
            val fileWriter = FileWriter(outputFile)
            fileWriter.write(gpxData)
            fileWriter.close()

            val filePathText: TextView = findViewById(R.id.gpxFilePath)
            filePathText.text = "Arquivo GPX: ${outputFile.absolutePath}"
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao exportar GPX: ${e.message}")
        }
    }
}
