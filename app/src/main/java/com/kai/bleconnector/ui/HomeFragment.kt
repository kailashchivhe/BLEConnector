package com.kai.bleconnector.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.kai.bleconnector.databinding.FragmentHomeBinding
import java.nio.charset.StandardCharsets
import java.util.*


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mScanning = false
    private val handler = Handler()
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mBluetoothDevice: BluetoothDevice? = null
    var tempChar: BluetoothGattCharacteristic? = null
    var bulbChar:BluetoothGattCharacteristic? = null
    var beepChar:BluetoothGattCharacteristic? = null

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            searchDevices()
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            mBluetoothDevice = result.device
            _binding?.connectButton?.isEnabled = true
        }
    }

    private val mBluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback(){
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if(status == 0x0000){
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        gatt.discoverServices()
                        showBleUI()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _binding?.connectButton?.isEnabled = false
                        _binding?.connectButton?.text = "Connect"
                        mBluetoothDevice = null
                    }
                }
            }
            else{
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if(status == 0x0000 ){
                showServices(gatt.services)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic.uuid.toString().equals("0ced9345-b31f-457d-a6a2-b3db9b03e39a")) {
                val value = characteristic.value
                val str = String(value)
                updateTemperatureUi(str)
            }
        }
    }

    private fun updateTemperatureUi(str: String) {
        activity?.runOnUiThread {
            _binding?.temperatureTextView?.text = "${str} F"
        }
    }

    private fun showBleUI() {
        activity?.runOnUiThread {
            _binding?.bleContainer?.visibility = View.VISIBLE
        }
    }

    private fun showServices(services: List<BluetoothGattService>) {
        for (gattService in services) {
            for (characteristic in gattService.characteristics) {
                if (characteristic.uuid.toString() == "0ced9345-b31f-457d-a6a2-b3db9b03e39a") {
                    tempChar = characteristic
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    mBluetoothGatt?.setCharacteristicNotification(tempChar, true)
                } else if (characteristic.uuid.toString() == "fb959362-f26e-43a9-927c-7e17d8fb2d8d") {
                    bulbChar = characteristic
                } else if (characteristic.uuid.toString() == "ec958823-f26e-43a9-927c-7e17d8f32a90") {
                    beepChar = characteristic
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bluetoothManager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter

        _binding?.searchButton?.setOnClickListener {
            checkPermissions()
        }

        _binding?.connectButton?.setOnClickListener {
            connectClicked()
        }

        _binding?.beepToggle?.setOnCheckedChangeListener { toggleButton, isChecked ->
            if (isChecked) {
                beepPower(1)
            } else {
                beepPower(0)
            }
        }
        _binding?.bulbToggle?.setOnCheckedChangeListener { toggleButton, isChecked ->
            if (isChecked) {
                bulbPower(1)
            } else {
                bulbPower(0)
            }
        }
    }

    private fun bulbPower(data: Int){
        var dataArray: ByteArray = "0".toByteArray(StandardCharsets.UTF_8)
        if(data == 1) {
            dataArray = "1".toByteArray(StandardCharsets.UTF_8)
        }
        bulbChar?.value = dataArray
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mBluetoothGatt!!.writeCharacteristic(bulbChar)
    }

    private fun beepPower(data: Int){
        var dataArray: ByteArray = "0".toByteArray(StandardCharsets.UTF_8)
        if(data == 1) {
            dataArray = "1".toByteArray(StandardCharsets.UTF_8)
        }
        beepChar?.value = dataArray
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mBluetoothGatt!!.writeCharacteristic(beepChar)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions( arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ), 101 )
        } else {
            if (! mBluetoothAdapter!!.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startForResult.launch(enableBtIntent)
            } else {
                searchDevices()
            }
        }
    }

    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<out String>, grantResults: IntArray ) {
        if(requestCode == 101){
            searchDevices()
        }
    }

    private fun connectClicked() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if(mBluetoothGatt == null){
            mBluetoothGatt = mBluetoothDevice?.connectGatt(requireContext(), false, mBluetoothGattCallback )
            _binding?.connectButton?.text = "Disconnect"
        }
        else{
            _binding?.connectButton?.text = "Connect"
            _binding?.bleContainer?.visibility = View.INVISIBLE
            mBluetoothGatt?.disconnect()
            Toast.makeText( requireContext(), "Press Search to Connect Again", Toast.LENGTH_SHORT ).show()
        }
    }

    private fun searchDevices() {
        val scanFilterList: MutableList<ScanFilter> = ArrayList()

        val filter = ScanFilter.Builder()
            .setDeviceName("Smart Bulb")
            .build()

        scanFilterList.add(filter)

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()

        if (!mScanning) {
            handler.postDelayed({
                mScanning = false
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    mBluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
                }
            }, 10000 )
            mScanning = true
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mBluetoothAdapter?.bluetoothLeScanner?.startScan(scanFilterList, scanSettings, leScanCallback)
            }
        } else {
            mScanning = false
            mBluetoothAdapter?.bluetoothLeScanner?.stopScan( object: ScanCallback(){
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mBluetoothGatt?.close()
        _binding = null
    }
}