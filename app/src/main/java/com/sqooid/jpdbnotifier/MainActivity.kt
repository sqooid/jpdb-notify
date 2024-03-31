package com.sqooid.jpdbnotifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.sqooid.jpdbnotifier.Constants.CHANNEL_ID
import com.sqooid.jpdbnotifier.ui.theme.JPDBNotifierTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.UUID


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Init notifications
        // Create the NotificationChannel.
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)

        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(Constants.SHARED_PREFS, Context.MODE_PRIVATE)
        val context = this
        setContent {
            val showLoginPage = remember {
                mutableStateOf(false)
            }
            val webViewState = rememberWebViewState(Constants.JPDB_LOGIN_URL)
            val navigator = rememberWebViewNavigator()
            val authCookies = remember {
                mutableStateOf(prefs.getString(Constants.PREFS_COOKIES_KEY, "") ?: "")
            }

            if (authCookies.value.isNotEmpty()) {
                createWorker(context)
            }

            JPDBNotifierTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!showLoginPage.value) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Title()
                            Description()
                            Spacer(modifier = Modifier.height(20.dp))

                            // Login and logout
                            if (authCookies.value == "") {
                                LoginButton(onClick = {
                                    Log.d("app", "Showing login page")
                                    showLoginPage.value = true
                                })
                            } else {
                                LogoutButton(onClick = {
                                    Log.d("app", "Logging out")
                                    clearAuthCookies()
                                    navigator.loadUrl(Constants.JPDB_LOGIN_URL)
                                    authCookies.value = ""
                                    prefs.edit()
                                        .putString(Constants.PREFS_COOKIES_KEY, authCookies.value)
                                        .apply()
                                    cancelWorker(context)
                                })
                            }

                            // Settings
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(text = "Settings", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            SliderSetting(
                                name = "Card threshold",
                                description = "Minimum number of cards ready for review before a notification is sent",
                                prefs = prefs,
                                key = Constants.PREFS_CARD_THRESHOLD_KEY,
                                valueRange = 1f..50f,
                                steps = 48,
                                int = true,
                                default = 1f
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SliderSetting(
                                name = "Check interval",
                                description = "Number of minutes between each check",
                                prefs = prefs,
                                key = Constants.PREFS_CHECK_INTERVAL_KEY,
                                valueRange = 15f..60f,
                                steps = 8,
                                default = 15f,
                                int = true,
                                onChange = { createWorker(context) }
                            )
                            
                            Button(onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    scan(context)
                                }
                            }) {
                                Text(text = "Test notification")
                            }
                        }
                    } else {
                        val loadingState = webViewState.loadingState
                        if (loadingState is LoadingState.Loading) {
                            LinearProgressIndicator(
                                progress = loadingState.progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        val lastLoadedUrl = webViewState.lastLoadedUrl
                        if (lastLoadedUrl == Constants.JPDB_HOME_URL) {
                            showLoginPage.value = false
                            authCookies.value = getAuthCookies()
                            Log.d("app", "Auth cookies: ${authCookies.value}")
                            prefs.edit().putString(Constants.PREFS_COOKIES_KEY, authCookies.value)
                                .apply()
                        }
                        LoginView(webViewState, navigator)
                    }
                }
            }
        }
    }
}

fun createWorker(context: Context) {
    val prefs = context.getSharedPreferences(Constants.SHARED_PREFS, Context.MODE_PRIVATE)
    val existingId = prefs.getString(Constants.WORK_REQUEST_ID, "") ?: ""
    val period = prefs
        .getFloat(Constants.PREFS_CHECK_INTERVAL_KEY, 15f)
    val workRequest =
        PeriodicWorkRequestBuilder<ScanWorker>(Duration.ofMinutes(period.toLong())).apply {
            if (existingId.isNotEmpty()) {
                setId(UUID.fromString(existingId))
            }
        }.build()

    val workManager = WorkManager.getInstance(context)
    if (existingId.isEmpty()) {
        val id = workRequest.id.toString()
        prefs.edit().putString(Constants.WORK_REQUEST_ID, id).apply()
        workManager.enqueue(workRequest)
        Log.d("app", "Created new worker id: $id")
    } else {
        workManager.updateWork(workRequest)
        Log.d("app", "Updated worker id: $existingId")
    }
}

fun cancelWorker(context: Context) {
    val workManager = WorkManager.getInstance(context)
    val prefs = context.getSharedPreferences(Constants.SHARED_PREFS, Context.MODE_PRIVATE)
    val existingId = prefs.getString(Constants.WORK_REQUEST_ID, "") ?: ""
    prefs.edit().putString(Constants.WORK_REQUEST_ID, "").apply()
    Log.d("app", "Cancelled worker id: $existingId")
    workManager.cancelWorkById(UUID.fromString(existingId))
}

fun getAuthCookies(): String {
    val cookieManager = CookieManager.getInstance()
    return try {
        cookieManager.getCookie(Constants.JPDB_HOME_URL)
    } catch(e: Exception) {
        ""
    }
}

fun clearAuthCookies() {
    val cookieManager = CookieManager.getInstance()
//    cookieManager.setCookie(Constants.JPDB_HOME_URL, "sid=;Max-Age=0")
    cookieManager.removeAllCookies(null)
}

@Composable
fun LoginButton(onClick: () -> Unit) {
    Button(onClick = { onClick() }) {
        Text("Log in")
    }
}

@Composable
fun LogoutButton(onClick: () -> Unit) {
    Button(onClick = { onClick() }) {
        Text("Log out")
    }
}

@Composable
fun LoginView(state: WebViewState, navigator: WebViewNavigator) {
    state.webSettings.customUserAgentString = Constants.USER_AGENT
    WebView(state, navigator = navigator)
}

@Composable
fun Title(modifier: Modifier = Modifier) {
    Text(
        text = "JPDB Notifier",
        modifier = modifier,
        fontSize = 30.sp
    )
}

@Composable
fun Description(modifier: Modifier = Modifier) {
    Text(
        text = "Get notifications when jpdb.io cards are ready for review",
        modifier = modifier.alpha(0.5f),
        fontSize = 15.sp,
    )
}
