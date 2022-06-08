package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    val CITY:String = "Seattle"
    val API:String = "683a25e891bd24d291303f092f57b3bb"
    private val TAG = "MainActivity_TEST"

    var bluetoothManager: BluetoothManager? = null
    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothLeScanner: BluetoothLeScanner? = null
    lateinit var pairedDevices: Set<BluetoothDevice>
    val REQUEST_ENABLE_BLUETOOTH = 24
    private val SCAN_PERIOD: Long = 6000
    private val FIVE_SECOND: Long = 5000
    val BluetoothDeviceName: String = "Thermo"
    var selectedDevice: BluetoothDevice? = null
    var bluetoothGatt: BluetoothGatt? = null
    private lateinit var myBtGattListAdapter: GattListAdapter

    var scanning = false
    val handler = Handler()

    lateinit var scanButton:Button
    lateinit var connectButton:Button
    lateinit var disconnectButton: Button

    lateinit var bluetoothTextView: TextView
    lateinit var tempTextView: TextView

    lateinit var scanList:ArrayList<BluetoothDevice>

    lateinit var buttonCharacteristic: BluetoothGattCharacteristic
    var secondCharacteristic:Boolean = false

    var SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    var TEMPERATURE_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    var BUTTON_MEASUREMENT_UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    lateinit var mainHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scanList = ArrayList()

        // Setup the buttons
        scanButton = findViewById<Button>(R.id.scanButton)
        connectButton = findViewById<Button>(R.id.connectButton)
        disconnectButton = findViewById<Button>(R.id.disconnectButton)

        bluetoothTextView = findViewById<TextView>(R.id.BluetoothStatus)
        tempTextView = findViewById<TextView>(R.id.BluetoothDeviceTemp)

        // Check permissions in the onCreate of your main Activity
        ActivityCompat.requestPermissions(this,
            arrayOf( Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 1)

        // Setup Bluetooth stuff
        this.bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = this.bluetoothManager!!.getAdapter()
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        myBtGattListAdapter = GattListAdapter()

        // Check that if bluetooth is available
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter!!.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling ActivityCompat#requestPermissions
                    return
                }
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
            }
        }


        // Scan and generate the bluetooth device list
        scanButton.setOnClickListener{
            scanLeDevice()
        }

        connectButton.setOnClickListener{
            connectToDevice()
        }

        disconnectButton.setOnClickListener{
            disconnectDevice()
        }


        mainHandler = Handler(Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice() {
        if (!scanning) {

            bluetoothTextView.text = "Scanning..."

            scanButton.isEnabled = false
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(stopLeScan(), SCAN_PERIOD)
            // Start the scan
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)

        } else {
            // Will hit here if we are already scanning
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopLeScan() = Runnable {
        scanning = false
        bluetoothLeScanner?.stopScan(leScanCallback)
        scanButton.text = "SCAN"
        bluetoothTextView.text = "Scanning done. Found nothing. Try again."
        scanButton.isEnabled = true

        selectedDevice = null

        for(item in scanList) {
            //bluetoothDeviceAddress.text = item.name + " " + item.address
            if(item.name.toString().equals(BluetoothDeviceName)){
                selectedDevice = item
                bluetoothTextView.text = selectedDevice?.name + " " + selectedDevice?.address
            }else{
                bluetoothTextView.text = "Didn't found "+BluetoothDeviceName+". Try again."
            }
        }

        scanList.clear()
    }

    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.i(TAG,"Device name: "+result.device.name)
            if(result.device.name != null){
                if(!scanList.contains(result.device)){
                    scanList.add(result.device)
                }
            }

        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        if (selectedDevice != null) {
            bluetoothTextView.text = "Connecting..."
            bluetoothAdapter?.let { adapter ->
                try {
                    val device = adapter.getRemoteDevice(selectedDevice!!.address)
                    // connect to the GATT server on the device
                    bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                    return
                } catch (exception: IllegalArgumentException) {
                    Log.w(TAG, "Device not found with provided address.  Unable to connect.")
                    return
                }
            } ?: run {
                Log.w(TAG, "BluetoothAdapter not initialized")
                return
            }
        }else{
            bluetoothTextView.text = "Not Connected"
        }
    }


    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                Log.i(TAG, "Starting service discovery")
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered: ")
                Log.i(TAG, gatt?.services.toString())
                displayGattServices(bluetoothGatt?.services)
                checkAndConnect(bluetoothGatt?.services)
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)

            // Processing the data that comes from the CircuitPlayground
            Log.i(TAG, characteristic?.uuid.toString())
            Log.i(TAG, characteristic?.value?.get(0)?.toUByte().toString())
            Log.i(TAG, characteristic?.value?.get(1)?.toUByte().toString())
            handler.post {

                // Update the temperature text field "°C"
                // Update the button status text field
                if(characteristic?.uuid == TEMPERATURE_MEASUREMENT_UUID){
                    tempTextView.text = characteristic?.value?.get(1)?.toUByte().toString() + "°C"
                }

            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)

            // secondCharacteristic is init as false
            if(!secondCharacteristic ){
                if (!bluetoothGatt?.setCharacteristicNotification(buttonCharacteristic, true)!!) {
                    // Stop if the characteristic notification setup failed.
                    Log.e(TAG, "characteristic notification setup failed")
                    return
                }
                Log.i(TAG, "characteristic notification setup success")

                // Then, write a descriptor to the btGatt to enable notification
                val descriptor = buttonCharacteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGatt!!.writeDescriptor(descriptor)
                secondCharacteristic = true
            }
        }

    }



    private fun displayGattServices(gattServices: List<BluetoothGattService>?) {
        if (gattServices == null) return
        handler.post {
            myBtGattListAdapter.addServices(gattServices)
        }
    }


    @SuppressLint("MissingPermission")
    private fun checkAndConnect(services: List<BluetoothGattService>?) {
        Log.i(TAG, "Checking for Thermo Service")

        services?.forEach { service ->
            if (service.uuid == SERVICE_UUID){
                Log.i(TAG, "Thermo")
                Log.i(TAG, service.uuid.toString())
                val characteristic = service.getCharacteristic(TEMPERATURE_MEASUREMENT_UUID)
                buttonCharacteristic = service.getCharacteristic(BUTTON_MEASUREMENT_UUID)
                bluetoothGatt?.readCharacteristic(characteristic)

                // First, call setCharacteristicNotification to enable notification.
                if (!bluetoothGatt?.setCharacteristicNotification(characteristic, true)!!) {
                    // Stop if the characteristic notification setup failed.
                    Log.e(TAG, "characteristic notification setup failed")
                    return
                }
                Log.i(TAG, "characteristic notification setup success")

                // Then, write a descriptor to the btGatt to enable notification
                val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGatt!!.writeDescriptor(descriptor)

                bluetoothTextView.text = "Connected"

                // When the characteristic value changes, the Gatt callback will be notified
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectDevice() {
        disconnectButton.isEnabled = false
        handler.post {
            bluetoothGatt?.disconnect()

            bluetoothTextView.text = "Not connected"

            secondCharacteristic = false
        }
        disconnectButton.isEnabled = true
    }

    // API Stuff starts here
    //* * Helper function @param is @return
    fun convertStreamToString(`is`: InputStream): String {
        val s = Scanner(`is`).useDelimiter("\\A")
        return if (s.hasNext()) s.next().replace(",", ",\n") else ""
    }


    inner class weather() : AsyncTask<String,Void,String>(){

        override fun onPreExecute() {
            super.onPreExecute()
        }

        override fun doInBackground(vararg p0: String?): String? {
            val results = arrayOfNulls<String>(2)
            var url: URL? = null

            try {
                url = URL("https://api.openweathermap.org/data/2.5/weather?q=$CITY&appid=$API")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.doInput = true
                conn.connect()

                // Read response.
                val inputStream = conn.inputStream
                val resp: String = convertStreamToString(inputStream)

                return resp
            }catch(e: Exception){
                return null
            }


            return "results"
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)

            Log.i(TAG,"Try to process the return JSON variable")
            try{

                val jsonObj = JSONObject(result)
                val main = jsonObj.getJSONObject("main")
                val sys = jsonObj.getJSONObject("sys")
                val wind = jsonObj.getJSONObject("wind")
                val coor = jsonObj.getJSONObject("coord")
                val weather = jsonObj.getJSONArray("weather").getJSONObject(0)

                Log.i(TAG,result.toString())
                Log.i(TAG,main.toString())
                Log.i(TAG,sys.toString())
                Log.i(TAG,wind.toString())
                Log.i(TAG,coor.toString())

                // Location
                val address = jsonObj.getString("name")+", "+sys.getString("country")
                findViewById<TextView>(R.id.address).text = address

                // Last update time
                val updatedAt:Long = jsonObj.getLong("dt")
                val updatedAtText = "Last API update at: "+ SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.ENGLISH).format(Date(updatedAt*1000))
                findViewById<TextView>(R.id.lastUpdateTime).text = updatedAtText

                // Latitude and Longitude
                val coordination = "Latitude " + coor.getString("lat") +" "+ "Longitude "+ coor.getString("lon")
                findViewById<TextView>(R.id.longitudeAndLatitude).text = coordination

                // Weather Status
                val weatherDescription = weather.getString("description")
                findViewById<TextView>(R.id.mainWeatherStatus).text = weatherDescription.capitalize()

                // Main temperature
                val temp = main.getString("temp")
                val tempInCelsius = temp.toDouble()-273.15
                val tempRoundoff = String.format("%.2f", tempInCelsius)
                findViewById<TextView>(R.id.mainTemperature).text= tempRoundoff + "°C"



            }catch(e: Exception){
                Log.i(TAG,"Failed to process the JSON variable")
            }
        }


    }

    override fun onPause() {
        super.onPause()
        // Update weather
        mainHandler.removeCallbacks(updateTextTask)
    }

    override fun onResume() {
        super.onResume()
        // Update weather
        mainHandler.post(updateTextTask)
    }

    // The updateTextTask
    // Run this to update the weather
    private val updateTextTask = object : Runnable {
        override fun run() {
            weather().execute()
            mainHandler.postDelayed(this, FIVE_SECOND*5)
        }
    }



}