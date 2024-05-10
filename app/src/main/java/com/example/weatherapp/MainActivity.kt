package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences : SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setupUI()
        if(!isLocationEnabled()){
            Toast.makeText(this, "Your location provider is turned OFF. Please enable location services 😊", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
           Dexter.withActivity(this)
               .withPermissions(
                   Manifest.permission.ACCESS_FINE_LOCATION,
                   Manifest.permission.ACCESS_COARSE_LOCATION
               )
               .withListener(object :  MultiplePermissionsListener {
                   override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                       if(report!!.areAllPermissionsGranted()){
                           requestLocationData()
                       }

                       if(report.isAnyPermissionPermanentlyDenied){
                           Toast.makeText(this@MainActivity,
                               "You have denied location permission. Please allow it is mandatory.",
                               Toast.LENGTH_SHORT).show()
                       }
                   }

                   override fun onPermissionRationaleShouldBeShown(
                       permissions: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                       token: PermissionToken?
                   ) {
                       showRationalDialogForPermissions()
                   }

               }).onSameThread()
               .check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            val longitude = mLastLocation?.longitude
            if (latitude != null) {
                if (longitude != null) {
                    getLocationWeatherDetails(latitude, longitude)
                }
            }
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
             val retrofit: Retrofit = Retrofit.Builder()
                 .baseUrl(Constants.BASE_URL)
                 .addConverterFactory(GsonConverterFactory.create())
                 .build()

             val service : WeatherService = retrofit
                 .create<WeatherService>(WeatherService::class.java)

             val listCall: Call<WeatherResponse> = service.getWeather(
                 latitude,
                 longitude,
                 Constants.METRIC_UNIT,
                 Constants.APP_ID
             )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        hideProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        Log.i("Response Result", "$weatherList")
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(
                            Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString
                        )
                        editor.apply()
                        setupUI()
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error","Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error !!", t.message.toString())
                    hideProgressDialog()
                }

            })
        }
        else{
            Toast.makeText(this, "No internet connection available.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if(mProgressDialog!=null){
            mProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            } else -> {
                super.onOptionsItemSelected(item)
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "Go To Settings"
            ){ _, _ ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") {
                    dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        val weatherResponseJsonString = mSharedPreferences
            .getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (weatherResponseJsonString != null) {
            if(weatherResponseJsonString.isNotEmpty()){
                val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
                for(i in weatherList.weather.indices){
                    Log.i("weather name", weatherList.weather.toString())
                    findViewById<TextView>(R.id.tv_main).text = weatherList.weather[i].main
                    findViewById<TextView>(R.id.tv_main_description).text = weatherList.weather[i].description
                    findViewById<TextView>(R.id.tv_temp).text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                    findViewById<TextView>(R.id.tv_sunrise_time).text = unixTime(weatherList.sys.sunrise).toString() + " am"
                    findViewById<TextView>(R.id.tv_sunset_time).text = unixTime(weatherList.sys.sunset).toString() + " pm"
                    findViewById<TextView>(R.id.tv_humidity).text = weatherList.main.humidity.toString() + " percent"
                    findViewById<TextView>(R.id.tv_speed).text = weatherList.wind.speed.toString()
                    findViewById<TextView>(R.id.tv_name).text = weatherList.name
                    findViewById<TextView>(R.id.tv_country).text = weatherList.sys.country
                    findViewById<TextView>(R.id.tv_min).text = weatherList.main.tempMin.toString() + " min"
                    findViewById<TextView>(R.id.tv_max).text = weatherList.main.tempMax.toString() + " max"
                    findViewById<TextView>(R.id.tv_area).text = weatherList.sys.country
                    findViewById<TextView>(R.id.tv_sea).text = weatherList.main.sea_level.toString()
                    findViewById<TextView>(R.id.tv_grnd).text = weatherList.main.grnd_level.toString()
                    findViewById<TextView>(R.id.tv_base).text = weatherList.base
                    findViewById<TextView>(R.id.tv_pressure).text = weatherList.main.pressure.toString()
                    findViewById<TextView>(R.id.tv_visibility).text = weatherList.visibility.toString()
                    findViewById<TextView>(R.id.tv_wind_deg).text = weatherList.wind.deg.toString() + "°"


                    when(weatherList.weather[i].icon){
                        "01d" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.sunny)
                        "02d" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.cloud)
                        "03d" -> findViewById<ImageView>(R.id.tv_main).setImageResource(R.drawable.cloud)
                        "04d" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.cloud)
                        "10d" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.rain)
                        "11d" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.storm)
                        "13d" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.snowflake)
                        "01n" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.moon)
                        "02n" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.nightcloud)
                        "03n" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.night1)
                        "10n" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.nightcloud)
                        "11n" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.app)
                        "13n" -> findViewById<ImageView>(R.id.iv_main).setImageResource(R.drawable.mist)
                    }
                }
            }
        }
    }

    private fun getUnit(value: String):String {
        var value = "°C"
        if("US" == value || "LR" == value || "MM" == value){
            value =  "°F"
        }
        return value
    }

    private fun unixTime(timex: Long) : String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.CANADA)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}


