package dev.droidos.shell.apps.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.RowDivider
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel
import dev.droidos.shell.ui.components.SettingRow
import kotlinx.coroutines.launch

@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val client = remember { WeatherClient() }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var places by remember { mutableStateOf<List<WeatherClient.Place>>(emptyList()) }
    var selected by remember { mutableStateOf<WeatherClient.Place?>(null) }
    var forecast by remember { mutableStateOf<WeatherClient.Forecast?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            busy = true
            error = null
            client.geocode(query).fold(
                onSuccess = {
                    places = it
                    forecast = null
                    selected = null
                    if (it.isEmpty()) error = "No place matched \"${query.trim()}\""
                },
                onFailure = { error = it.message }
            )
            busy = false
        }
    }

    fun load(place: WeatherClient.Place) {
        scope.launch {
            busy = true
            error = null
            client.forecast(place.latitude, place.longitude).fold(
                onSuccess = {
                    selected = place
                    forecast = it
                    places = emptyList()
                },
                onFailure = { error = it.message }
            )
            busy = false
        }
    }

    ScreenScaffold(
        title = "Weather",
        subtitle = selected?.let { WeatherEngine.placeLabel(it.name, it.admin1, it.country) }
            ?: "Open-Meteo forecast",
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("City or place") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { search() }, enabled = !busy && query.isNotBlank()) {
                    Text("Find")
                }
            }

            if (busy) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            forecast?.let { report ->
                Spacer(Modifier.height(12.dp))
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    WeatherEngine.temperature(report.current.temperature),
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    WeatherEngine.describe(report.current.code) +
                                        if (report.current.isDay) "" else " (night)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "feels ${WeatherEngine.temperature(report.current.feelsLike)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Humidity ${WeatherEngine.percent(report.current.humidity)}  |  " +
                                "Wind ${WeatherEngine.wind(report.current.windSpeed, report.current.windDirection)}  |  " +
                                "Rain ${report.current.precipitation?.let { "$it mm" } ?: "--"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        report.timezone?.let {
                            Text(
                                "Timezone $it - observed from api.open-meteo.com",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (report.days.isNotEmpty()) {
                    SectionLabel("${report.days.size}-day outlook")
                    GlassCard {
                        Column(Modifier.fillMaxWidth()) {
                            report.days.forEachIndexed { index, day ->
                                if (index > 0) RowDivider()
                                SettingRow(
                                    icon = Icons.Rounded.Cloud,
                                    title = WeatherEngine.dayName(day.date),
                                    subtitle = WeatherEngine.describe(day.code) +
                                        day.precipitationChance?.let { " - $it% precipitation" }.orEmpty(),
                                    trailing = {
                                        Text(
                                            "${WeatherEngine.temperature(day.min)} / " +
                                                WeatherEngine.temperature(day.max),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (places.isNotEmpty()) {
                SectionLabel("Matches")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    places.forEach { place ->
                        GlassCard(onClick = { load(place) }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    WeatherEngine.placeLabel(place.name, place.admin1, place.country),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "%.3f, %.3f".format(place.latitude, place.longitude),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (forecast == null && places.isEmpty() && !busy) {
                EmptyState(
                    icon = Icons.Rounded.Cloud,
                    title = "Search a place",
                    message = "Forecasts come straight from Open-Meteo's public API, which needs no key " +
                        "and no account.",
                    actionLabel = null,
                    onAction = null
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                MessageBanner(it, isError = true)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
