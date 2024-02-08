package de.salomax.currencies.model.provider

import android.content.Context
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.salomax.currencies.R
import de.salomax.currencies.model.ApiProvider
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.model.Timeline
import de.salomax.currencies.model.adapter.LocalDateAdapter
import de.salomax.currencies.model.adapter.FrankfurterAppRatesAdapter
import de.salomax.currencies.model.adapter.FrankfurterAppTimelineAdapter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FrankfurterApp : ApiProvider.Api() {

    override val name = "Frankfurter.app"

    override fun descriptionShort(context: Context) =
        context.getText(R.string.api_frankfurterApp_descriptionShort)

    override fun getDescriptionLong(context: Context) =
        context.getText(R.string.api_frankfurterApp_descriptionFull)

    override fun descriptionUpdateInterval(context: Context) =
        context.getText(R.string.api_frankfurterApp_descriptionUpdateInterval)

    override fun descriptionHint(context: Context) =
        null

    override val baseUrl = "https://api.frankfurter.app"

    override suspend fun getRates(context: Context?, date: LocalDate?): Result<ExchangeRates, FuelError> {
        // Currency conversions are done relatively to each other - so it basically doesn't matter
        // which base is used here. However, Euro is a strong currency, preventing rounding errors.
        val base = Currency.EUR
        val dateString =
            if (date != null) date.format(DateTimeFormatter.ISO_LOCAL_DATE) else "latest"

        return Fuel.get(
            baseUrl +
                    "/$dateString" +
                    "?base=$base"
        ).awaitResult(
            moshiDeserializerOf(
                Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .apply {
                        add(FrankfurterAppRatesAdapter(base))
                        add(LocalDateAdapter())
                    }
                    .build()
                    .adapter(ExchangeRates::class.java)
            )
        ).map { rates ->
            rates.copy(provider = ApiProvider.FRANKFURTER_APP)
        }
    }

    override suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<Timeline, FuelError> {

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        // can't search for FOK - have to use DKK instead
        val parameterBase = if (base == Currency.FOK) "DKK" else base.iso4217Alpha()
        val parameterSymbol = if (symbol == Currency.FOK) "DKK" else symbol.iso4217Alpha()

        return Fuel.get(
            "$baseUrl/" +
                    startDate.format(dateFormatter) +
                    ".." +
                    endDate.format(dateFormatter) +
                    "?base=$parameterBase" +
                    "&symbols=$parameterSymbol"
        ).awaitResult(
            moshiDeserializerOf(
                Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .apply {
                        add(FrankfurterAppRatesAdapter(base))
                        add(LocalDateAdapter())
                        add(FrankfurterAppTimelineAdapter(symbol))
                    }
                    .build()
                    .adapter(Timeline::class.java)
            )
        ).map { timeline ->
            when (base) {
                // change dkk base back to fok, if needed
                Currency.FOK -> timeline.copy(base = base.iso4217Alpha())
                else -> timeline
            }
        }.map { timeline ->
            timeline.copy(provider = ApiProvider.FRANKFURTER_APP)
        }
    }

}
