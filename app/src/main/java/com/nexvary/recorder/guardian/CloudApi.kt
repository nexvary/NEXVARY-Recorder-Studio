package com.nexvary.recorder.guardian

import android.content.Context
import com.nexvary.recorder.family.FamilyStore
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class CloudFailure(val code: String,val status: Int=0): Exception(code)

/** Cognito client IDs and endpoint addresses are public configuration. No AWS key enters the APK. */
class CloudApi(private val context: Context, private val expectedSession: JSONObject? = null) {
    fun request(method: String,path: String,body: JSONObject?=null): JSONObject {
        require(path.startsWith("/") && !path.contains(".."))
        var config=config(context)
        expectedSession?.let { if(!CloudSessionState.sameSession(config,it)) throw CloudFailure("SESSION_CHANGED",401) }
        if(System.currentTimeMillis() >= config.optLong("expiresAt")-30_000 && config.optString("refreshToken").isNotBlank()) {
            val refreshed=cognito("InitiateAuth",JSONObject().put("ClientId",config.getString("clientId")).put("AuthFlow","REFRESH_TOKEN_AUTH")
                .put("AuthParameters",JSONObject().put("REFRESH_TOKEN",config.getString("refreshToken"))))
            val tokens=refreshed.getJSONObject("AuthenticationResult")
            FamilyStore(context).update { root ->
                if(!CloudSessionState.sameSession(config(root),config)) throw CloudFailure("SESSION_CHANGED",401)
                config(root).put("idToken",tokens.getString("IdToken")).put("expiresAt",System.currentTimeMillis()+tokens.getLong("ExpiresIn")*1000)
            }
            config=config(context)
            expectedSession?.let { if(!CloudSessionState.sameSession(config,it)) throw CloudFailure("SESSION_CHANGED",401) }
        }
        val token=config.optString("idToken")
        if(token.isBlank() || System.currentTimeMillis() >= config.optLong("expiresAt")) throw CloudFailure("LOGIN_REQUIRED",401)
        return json(config.getString("baseUrl").trimEnd('/')+path,method,body,mapOf("Authorization" to "Bearer $token"))
    }
    fun accountId(): String {
        return CloudSessionState.binding(config(context).optString("idToken"))
    }
    fun authenticate(email: String,password: String) {
        val config=config(context)
        val result=cognito("InitiateAuth",JSONObject().put("ClientId",config.getString("clientId")).put("AuthFlow","USER_PASSWORD_AUTH")
            .put("AuthParameters",JSONObject().put("USERNAME",email.trim()).put("PASSWORD",password)))
        val tokens=result.optJSONObject("AuthenticationResult") ?: throw CloudFailure("AUTH_CHALLENGE_REQUIRED")
        FamilyStore(context).update { root ->
            if(CloudApi.config(root).optString("baseUrl")!=config.optString("baseUrl") || CloudApi.config(root).optString("clientId")!=config.optString("clientId")) throw CloudFailure("SESSION_CHANGED",401)
            CloudSessionState.install(root,tokens)
        }
    }
    fun signup(email: String,password: String) = cognito("SignUp",JSONObject().put("ClientId",config(context).getString("clientId"))
        .put("Username",email.trim()).put("Password",password).put("UserAttributes",org.json.JSONArray().put(JSONObject().put("Name","email").put("Value",email.trim()))))
    fun confirm(email: String,code: String) = cognito("ConfirmSignUp",JSONObject().put("ClientId",config(context).getString("clientId")).put("Username",email.trim()).put("ConfirmationCode",code.trim()))
    fun forgot(email: String)=cognito("ForgotPassword",JSONObject().put("ClientId",config(context).getString("clientId")).put("Username",email.trim()))
    fun reset(email: String,code: String,password: String)=cognito("ConfirmForgotPassword",JSONObject().put("ClientId",config(context).getString("clientId")).put("Username",email.trim()).put("ConfirmationCode",code.trim()).put("Password",password))
    private fun cognito(action: String,body: JSONObject): JSONObject {
        val region=config(context).getString("region")
        require(region.matches(Regex("[a-z]{2}-[a-z]+-\\d")))
        return json("https://cognito-idp.$region.amazonaws.com/","POST",body,mapOf("X-Amz-Target" to "AWSCognitoIdentityProviderService.$action","Content-Type" to "application/x-amz-json-1.1"))
    }
    companion object {
        fun config(root: JSONObject): JSONObject = root.optJSONObject("cloud") ?: JSONObject().also { root.put("cloud",it) }
        fun config(context: Context)=config(FamilyStore(context).snapshot())
        fun configured(context: Context)=config(context).let { it.optString("baseUrl").isNotBlank() }
        fun json(address: String,method: String,body: JSONObject?,headers: Map<String,String> = emptyMap()): JSONObject {
            val url=URL(address);require(url.protocol=="https" && url.userInfo==null)
            val connection=url.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects=false;connection.requestMethod=method;connection.connectTimeout=15_000;connection.readTimeout=30_000
            connection.setRequestProperty("Content-Type","application/json")
            headers.forEach { (name,value) -> connection.setRequestProperty(name,value) }
            return try {
                if(body!=null) { connection.doOutput=true;connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
                val code=connection.responseCode
                val stream=if(code in 200..299) connection.inputStream else connection.errorStream
                val bytes=stream?.use { BoundedInput.read(it,256*1024) } ?: byteArrayOf()
                val result=runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrDefault(JSONObject())
                if(code !in 200..299) throw CloudFailure(result.optString("error",result.optString("__type","HTTP_$code")).substringAfter('#'),code)
                result
            } finally { connection.disconnect() }
        }
    }
}

/** One source of truth for sensitive quotas: GET /usage, never the handset calendar. */
class QuotaManager(private val context: Context) {
    fun refresh(): JSONObject {
        val session=CloudApi.config(context)
        val account=CloudSessionState.binding(session.optString("idToken"))
        val usage=CloudApi(context,session).request("GET","/usage")
        FamilyStore(context).update { root ->
            if(runCatching { CloudSessionState.binding(CloudApi.config(root).optString("idToken")) }.getOrNull()!=account) throw CloudFailure("SESSION_CHANGED",401)
            root.put("cloudUsage",usage.put("receivedAt",System.currentTimeMillis()))
        }
        return usage
    }
    fun cached()=FamilyStore(context).snapshot().optJSONObject("cloudUsage")
}
