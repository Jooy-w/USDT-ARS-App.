package com.example.usdtars

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.*
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerBuy: RecyclerView
    private lateinit var recyclerSell: RecyclerView
    private lateinit var chart: LineChart
    private lateinit var alertEditText: EditText
    private lateinit var setAlertButton: Button
    private val prices = mutableListOf<Float>()
    private var alertPrice = 0f

    private val apiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            setLevel(HttpLoggingInterceptor.Level.BODY)
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://p2p.binance.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(BinanceApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerBuy = findViewById(R.id.recycler_buy)
        recyclerSell = findViewById(R.id.recycler_sell)
        chart = findViewById(R.id.price_chart)
        alertEditText = findViewById(R.id.alert_price)
        setAlertButton = findViewById(R.id.set_alert)

        recyclerBuy.layoutManager = LinearLayoutManager(this)
        recyclerSell.layoutManager = LinearLayoutManager(this)

        val buyAdapter = PriceAdapter()
        val sellAdapter = PriceAdapter()
        recyclerBuy.adapter = buyAdapter
        recyclerSell.adapter = sellAdapter

        setAlertButton.setOnClickListener {
            val text = alertEditText.text.toString()
            if (text.isNotEmpty()) {
                alertPrice = text.toFloat()
                Toast.makeText(this, "Alerta establecida en $alertPrice ARS", Toast.LENGTH_SHORT).show()
            }
        }

        val timer = Timer()
        timer.scheduleAtFixedRate(timerTask {
            fetchPrices("BUY") { buyList ->
                runOnUiThread {
                    buyAdapter.submitList(buyList.map { it.adv.price })
                    val avg = buyList.map { it.adv.price.toFloat() }.average().toFloat()
                    prices.add(avg)
                    if (alertPrice > 0 && avg <= alertPrice) {
                        Toast.makeText(this, "¡Precio alcanzado! $avg ARS", Toast.LENGTH_LONG).show()
                        alertPrice = 0f
                    }
                    updateChart()
                }
            }

            fetchPrices("SELL") { sellList ->
                runOnUiThread {
                    sellAdapter.submitList(sellList.map { it.adv.price })
                }
            }
        }, 0, 30000)
    }

    private fun fetchPrices(tradeType: String, callback: (List<AdvItem>) -> Unit) {
        val json = """
            {
              "page":1,
              "rows":5,
              "payTypes":[],
              "asset":"USDT",
              "tradeType":"$tradeType",
              "fiat":"ARS"
            }
        """.trimIndent()

        val body = RequestBody.create(MediaType.parse("application/json"), json)
        apiService.getP2PPrices(body).enqueue(object : Callback<BinanceP2PResponse> {
            override fun onResponse(call: Call<BinanceP2PResponse>, response: Response<BinanceP2PResponse>) {
                response.body()?.data?.let { callback(it) }
            }

            override fun onFailure(call: Call<BinanceP2PResponse>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun updateChart() {
        val entries = prices.mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val dataSet = LineDataSet(entries, "Precio Promedio USDT/ARS")
        chart.data = LineData(dataSet)
        chart.invalidate()
    }
}

interface BinanceApiService {
    @Headers("Content-Type: application/json")
    @POST("bapi/c2c/v2/friendly/c2c/adv/search")
    fun getP2PPrices(@Body body: RequestBody): Call<BinanceP2PResponse>
}

data class BinanceP2PResponse(val data: List<AdvItem>)
data class AdvItem(val adv: Adv)
data class Adv(val price: String)
