package de.salomax.currencies.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.kittinunf.fuel.core.FuelError
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import kotlinx.coroutines.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ExchangeRatesRepository(private val context: Context) {

    private val liveExchangeRates = Database(context).getExchangeRates()
    private val liveTimeline = MutableLiveData<Timeline?>()
    private var liveError = MutableLiveData<String?>()
    private var isUpdating = Database(context).isUpdating()

    /**
     * Gets and returns all latest exchange rates from the API.
     */
    fun getExchangeRates(): LiveData<ExchangeRates?> {
        val start = System.currentTimeMillis()
        Database(context).setUpdating(true)

        // run in background
        CoroutineScope(Dispatchers.IO).launch {
            // call api
            ExchangeRatesService.getRates(
                // use the right api
                apiProvider = Database(context).getApiProvider(),
                date = Database(context).getHistoricalDate()
            ).run  {
                val rates = component1()
                val fuelError = component2()
                // received some json
                if (rates != null && fuelError == null) {
                    // SUCCESS! update /store rates to preferences
                    if (rates.success == null || rates.success == true) {
                        postIsUpdating(start)
                        Database(context).insertExchangeRates(rates)
                        // reset error
                        liveError.postValue(null)
                    }
                    // ERROR: got response from API, but just an error message
                    else {
                        postError(rates.error)
                    }
                }
                // generic error
                else handleGenericError(fuelError)
            }
        }

        return liveExchangeRates
    }

    /**
     * Gets and returns the timeline of the last year of the given base and target currency
     */
    fun getTimeline(base: Currency, symbol: Currency): LiveData<Timeline?> {
        val start = System.currentTimeMillis()
        Database(context).setUpdating(true)

        // run in background
        CoroutineScope(Dispatchers.IO).launch {
            // call api
            ExchangeRatesService.getTimeline(
                // use the right api
                apiProvider = Database(context).getApiProvider(),
                base = base,
                symbol = symbol
            ).run {
                val timeline = component1()
                val fuelError = component2()
                // received some json
                if (timeline != null && fuelError == null) {
                    // SUCCESS! update /store rates to preferences
                    if (timeline.success == null || timeline.success == true) {
                        postIsUpdating(start)
                        CoroutineScope(Dispatchers.Main).launch {
                            liveTimeline.setValue(timeline)
                        }
                        // reset error
                        liveError.postValue(null)
                    }
                    // ERROR! got response from API, but just an error message
                    else {
                        postError(timeline.error)
                    }
                }
                // generic error
                else handleGenericError(fuelError)
            }
        }

        return liveTimeline
    }

    private fun handleGenericError(fuelError: FuelError?) {
        when {
            // shouldn't happen...
            fuelError == null ->
                postError(context.getString(R.string.error_generic))
            // print http response code, if available
            fuelError.response.statusCode != -1 && fuelError.response.statusCode != 200 -> {
                postError(context.getString(R.string.error_http, fuelError.response.statusCode))
            }
            // generic network error
            else -> {
                when (fuelError.exception) {
                    // timeout after 15s. likely server not reachable
                    is SocketTimeoutException ->
                        postError(context.getString(R.string.error_timeout))
                    // happens e.g. when device is offline or there's a DNS error
                    is UnknownHostException ->
                        postError(context.getString(R.string.error_no_data))
                    // everything else
                    else ->
                        postError(fuelError.localizedMessage ?: context.getString(R.string.error_generic))
                }
            }
        }
    }

    fun getError(): LiveData<String?> {
        return liveError
    }

    fun isUpdating(): LiveData<Boolean> {
        return isUpdating
    }

    /*
     * "update" for at least 750ms
     */
    private suspend fun postIsUpdating(start: Long) {
        val now = System.currentTimeMillis()
        if (now - start < 750) {
            Database(context).setUpdating(true)

            withContext(Dispatchers.Main) {
                launch {
                    delay(750 - (now - start))
                    Database(context).setUpdating(false)
                }
            }
        } else
            Database(context).setUpdating(false)
    }

    private fun postError(message: String?) {
        Database(context).setUpdating(false)
        liveError.postValue(
            if (message != null)
                context.getString(R.string.error, message)
            else
                context.getString(R.string.error_api_error)
        )
        // reset timeline
        liveTimeline.postValue(null)
    }

}
