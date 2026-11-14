package com.ultratv.tv.nativeapp.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.settings.FormField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fixed server of the Calix TV service: the user only types user and password. */
const val CALIX_SERVER = "https://sneoplus.net:8443"

sealed interface LoginState {
    data object Idle : LoginState
    data object Busy : LoginState
    data class Error(val msg: String) : LoginState
}

internal enum class AuthResult { OK, REJECTED, UNREACHABLE }

/** Reads the answer of `player_api.php`: `user_info.auth` is 1 for a valid account. Anything that is not JSON counts as unreachable. */
internal fun parseAuth(body: String): AuthResult {
    val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return AuthResult.UNREACHABLE
    val info = root["user_info"] as? JsonObject ?: return AuthResult.REJECTED
    val auth = (info["auth"] as? JsonPrimitive)?.content?.trim()
    return if (auth == "1" || auth.equals("true", ignoreCase = true)) AuthResult.OK else AuthResult.REJECTED
}

internal object CalixAuth {
    /** Blocking call: run it off the main thread. */
    fun check(http: OkHttpClient, server: String, user: String, pass: String): AuthResult = try {
        val url = "${server.trimEnd('/')}/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", user).addQueryParameter("password", pass).build()
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) AuthResult.UNREACHABLE else parseAuth(resp.body?.string().orEmpty())
        }
    } catch (_: Exception) {
        AuthResult.UNREACHABLE
    }
}

/** Calix TV login: logo, user, password and one button. Used on phones and on Android TV. */
@Composable
fun OnboardingWizard(onOpenSettings: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val state by vm.login.collectAsState()
    val busy = state is LoginState.Busy
    Box(Modifier.fillMaxSize().background(Color(0xFF050B1A)).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(painterResource(R.drawable.calix_logo), contentDescription = "Calix TV", contentScale = ContentScale.Fit, modifier = Modifier.height(190.dp).fillMaxWidth())
            FormField("Usuario", user, { user = it }, autoFocus = false, required = true, testTag = "field-user")
            FormField("Contraseña", pass, { pass = it }, password = true, last = true, required = true, placeholder = "••••••••", testTag = "field-pass")
            (state as? LoginState.Error)?.let { Text(it.msg, color = Ux.Err, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            FocusSurface(
                onClick = { if (!busy) vm.login(user, pass) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                bg = Color(0xFF1D4ED8),
            ) {
                Text(if (busy) "Verificando…" else "INGRESAR", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
            }
        }
    }
}
