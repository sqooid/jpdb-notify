package com.sqooid.jpdbnotifier

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp

@Composable
fun SettingLabel(text: String, modifier: Modifier = Modifier, description: String = "") {
    Column {
        Text(text = text, modifier = modifier)
        if (description != "") {
            Text(text = description, modifier = Modifier.alpha(0.5f), fontSize = 8.sp)
        }
    }
}

@Composable
fun SliderSetting(
    name: String,
    description: String,
    prefs: SharedPreferences,
    key: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 1f..50f,
    steps: Int = 48,
    int: Boolean = true,
    default: Float = 0f,
    onChange: (v: Float) -> Unit = {}
) {
    val value = remember {
        mutableFloatStateOf(prefs.getFloat(key, default))
    }
    Column {
        SettingLabel(name, description = description)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(value = value.floatValue, onValueChange = {
                value.floatValue = it
                prefs.edit().putFloat(key, it).apply()
                onChange(it)
            }, modifier = modifier.weight(1f), valueRange = valueRange, steps = steps)
            Text(text = value.floatValue.let {
                if (int) it.toInt()
                else it
            }.toString())
        }
    }
}