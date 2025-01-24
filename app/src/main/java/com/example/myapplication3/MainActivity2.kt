package com.example.myapplication3

// Importation des bibliothèques nécessaires pour gérer Bluetooth, les permissions et l'interface utilisateur
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*

class MainActivity : AppCompatActivity() {

    // Variables pour gérer Bluetooth et les permissions
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private val permissionRequestCode = 1 // Code pour identifier les demandes de permissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Récupération des vues depuis le layout
        val etUuid = findViewById<EditText>(R.id.et_uuid) // Champ pour saisir l'UUID
        val etMajor = findViewById<EditText>(R.id.et_major) // Champ pour saisir le Major
        val etMinor = findViewById<EditText>(R.id.et_minor) // Champ pour saisir le Minor
        val btnSubmit = findViewById<Button>(R.id.btn_submit) // Bouton pour démarrer l'émission
        val btnStop = findViewById<Button>(R.id.btn_stop) // Bouton pour arrêter l'émission
        val tvMacAddress = findViewById<TextView>(R.id.tv_mac_address) // Texte pour afficher l'adresse MAC

        // Vérification et demande des permissions nécessaires
        checkAndRequestPermissions()

        // Initialisation du Bluetooth via le service BluetoothManager
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser // Initialisation de l'émetteur BLE

        // Génération d'une adresse MAC pseudo-aléatoire si l'adresse réelle n'est pas disponible
        val pseudoMacAddress = generatePseudoMacAddress()
        tvMacAddress.text = "Pseudo-MAC : $pseudoMacAddress" // Affichage de la pseudo-MAC

        // Vérification que le Bluetooth est activé et disponible
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth non activé ou non disponible", Toast.LENGTH_SHORT).show()
            return
        }

        // Définir un UUID par défaut dans le champ UUID
        etUuid.setText("2D7A9F0C-E0E8-4CC9-A71B-A21DB2D034A1")

        // Action lorsque l'utilisateur clique sur le bouton "Envoyer"
        btnSubmit.setOnClickListener {
            val uuidString = etUuid.text.toString()
            val majorString = etMajor.text.toString()
            val minorString = etMinor.text.toString()

            // Vérification que tous les champs sont remplis et que les valeurs sont valides
            if (uuidString.isNotEmpty() && majorString.isNotEmpty() && minorString.isNotEmpty()) {
                try {
                    val uuid = UUID.fromString(uuidString) // Conversion de l'UUID
                    val major = majorString.toInt() // Conversion du Major en entier
                    val minor = minorString.toInt() // Conversion du Minor en entier
                    startAdvertising(uuid, pseudoMacAddress, major, minor) // Lancement de l'émission BLE
                } catch (e: IllegalArgumentException) {
                    // Gestion des erreurs en cas de format invalide
                    Toast.makeText(this, "UUID, Major ou Minor invalide", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Message d'erreur si des champs sont vides
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
            }
        }

        // Action lorsque l'utilisateur clique sur le bouton "Arrêter"
        btnStop.setOnClickListener {
            stopAdvertising() // Arrêt de l'émission BLE
        }
    }

    // Fonction pour vérifier et demander les permissions nécessaires
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION // Permission pour la localisation requise pour le BLE
        )

        // Permissions supplémentaires pour Android 12 (API 31) et supérieur
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        // Vérification des permissions manquantes
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        // Demande des permissions manquantes à l'utilisateur
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), permissionRequestCode)
        }
    }

    // Fonction pour démarrer l'émission BLE avec les données fournies
    private fun startAdvertising(uuid: UUID, macAddress: String, major: Int, minor: Int) {
        try {
            if (isAdvertising) stopAdvertising() // Arrêt de toute émission en cours

            // Configuration des paramètres d'émission BLE
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // Mode rapide
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // Puissance maximale
                .setConnectable(false) // Non connectable
                .build()

            // Construction des données à émettre (UUID, Major, Minor, MAC)
            val manufacturerData = ByteBuffer.allocate(16 + 2 + 2 + 6)
            manufacturerData.put(uuid.toByteArray()) // UUID sur 16 octets
            manufacturerData.putShort(major.toShort()) // Major sur 2 octets
            manufacturerData.putShort(minor.toShort()) // Minor sur 2 octets
            manufacturerData.put(macAddress.replace(":", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()) // MAC sur 6 octets

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Ne pas inclure le nom de l'appareil
                .setIncludeTxPowerLevel(false) // Ne pas inclure la puissance d'émission
                .addManufacturerData(0x004C, manufacturerData.array()) // Ajout des données au format iBeacon
                .build()

            advertiser?.startAdvertising(settings, advertiseData, advertiseCallback) // Démarrer l'émission
            isAdvertising = true // Mise à jour de l'état

            // Affichage d'un message avec les données
            Toast.makeText(
                this,
                "UUID: $uuid\nMajor: $major\nMinor: $minor\nMAC: $macAddress",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: SecurityException) {
            Toast.makeText(this, "Échec : autorisations Bluetooth non disponibles", Toast.LENGTH_SHORT).show()
        }
    }

    // Fonction pour arrêter l'émission BLE
    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        if (!isAdvertising) {
            Toast.makeText(this, "Pas d'émission en cours", Toast.LENGTH_SHORT).show()
            return
        }

        advertiser?.stopAdvertising(advertiseCallback) // Arrêt de l'émission
        isAdvertising = false // Mise à jour de l'état

        Toast.makeText(this, "Émission arrêtée", Toast.LENGTH_SHORT).show()
    }

    // Callback pour gérer le retour d'état de l'émission BLE
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Toast.makeText(this@MainActivity, "Émission BLE commencée", Toast.LENGTH_SHORT).show()
        }

        override fun onStartFailure(errorCode: Int) {
            val message = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Données trop volumineuses"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Trop d'émetteurs BLE actifs"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Émission déjà en cours"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Erreur interne"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Fonction non supportée"
                else -> "Erreur inconnue : $errorCode"
            }
            Toast.makeText(this@MainActivity, "Échec de l'émission BLE : $message", Toast.LENGTH_SHORT).show()
        }
    }

    // Extension pour convertir un UUID en tableau de bytes
    private fun UUID.toByteArray(): ByteArray {
        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(this.mostSignificantBits)
        buffer.putLong(this.leastSignificantBits)
        return buffer.array()
    }

    // Génération d'une adresse MAC pseudo-aléatoire basée sur l'identifiant Android de l'appareil
    private fun generatePseudoMacAddress(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val uniqueDeviceString = androidId + Build.MODEL + Build.BRAND
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uniqueDeviceString.toByteArray(Charsets.UTF_8))
        return hash.take(6).joinToString(":") { byte -> String.format("%02X", byte) }
    }
}
