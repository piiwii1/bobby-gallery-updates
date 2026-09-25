package ch.piiwii.remote2clean

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.widget.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object { const val PORT = 45821; const val PROTO = "PIIWII_REMOTE/1" }
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var ip: EditText
    private lateinit var pin: EditText
    private lateinit var status: TextView
    private var token = ""
    private lateinit var clientId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clientId = getPreferences(MODE_PRIVATE).getString("clientId", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            getPreferences(MODE_PRIVATE).edit().putString("clientId", it).apply()
        }
        token = getPreferences(MODE_PRIVATE).getString("token", "") ?: ""
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28); setBackgroundColor(Color.rgb(18,20,24)) }
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(box)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun title(t:String, size:Float=20f) = TextView(this).apply { text=t; textSize=size; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); setPadding(0,8,0,10) }
        box.addView(title("PiiWii Remote — connexion locale", 24f))
        box.addView(TextView(this).apply { text="PC Windows uniquement · UDP $PORT · aucun code Internet/WordPress"; textSize=14f; setTextColor(Color.LTGRAY) })
        ip = EditText(this).apply { hint="IP locale du PC (ex. 192.168.1.117)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); inputType=InputType.TYPE_CLASS_PHONE }
        pin = EditText(this).apply { hint="Code à 6 chiffres de l’Agent"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); inputType=InputType.TYPE_CLASS_NUMBER; maxLines=1 }
        box.addView(ip); box.addView(pin)

        val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        row.addView(button("Détecter le PC") { discover() }, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(button("Appairer") { pair() }, LinearLayout.LayoutParams(0,-2,1f))
        box.addView(row)
        status = TextView(this).apply { text = if(token.isBlank()) "Non appairé" else "Jeton local présent — renseigne l’IP puis Test"; setTextColor(Color.YELLOW); textSize=15f; setPadding(0,12,0,18) }
        box.addView(status)
        box.addView(button("TEST PING / PONG") { ping() })

        box.addView(title("Navigation"))
        addGrid(box, listOf("↑" to "NAV_UP", "←" to "NAV_LEFT", "OK" to "NAV_OK", "→" to "NAV_RIGHT", "↓" to "NAV_DOWN", "Retour" to "NAV_BACK", "Accueil" to "NAV_HOME", "Menu" to "NAV_MENU"))
        box.addView(title("Média / volume"))
        addGrid(box, listOf("⏮" to "MEDIA_PREVIOUS", "⏯" to "MEDIA_PLAY_PAUSE", "⏹" to "MEDIA_STOP", "⏭" to "MEDIA_NEXT", "Vol −" to "VOLUME_DOWN", "Muet" to "VOLUME_MUTE", "Vol +" to "VOLUME_UP"))
        box.addView(title("PiiWii TV"))
        addGrid(box, listOf("Direct" to "PIIWII_TV_DIRECT", "Chaîne −" to "PIIWII_TV_CHANNEL_DOWN", "Chaîne +" to "PIIWII_TV_CHANNEL_UP", "−10 s" to "PIIWII_TV_SEEK_BACK", "+30 s" to "PIIWII_TV_SEEK_FORWARD", "REC" to "PIIWII_TV_RECORD", "Guide" to "PIIWII_TV_GUIDE", "Radio" to "PIIWII_TV_RADIO", "Plein écran" to "PIIWII_TV_FULLSCREEN"))
        box.addView(button("EFFACER L’APPAIRAGE LOCAL") { token=""; getPreferences(MODE_PRIVATE).edit().remove("token").apply(); status.text="Appairage local effacé" })
        setContentView(root)
    }

    private fun button(label:String, action:()->Unit) = Button(this).apply { text=label; isAllCaps=false; setOnClickListener{ action() } }
    private fun addGrid(parent:LinearLayout, items:List<Pair<String,String>>) {
        var row:LinearLayout?=null
        items.forEachIndexed { i,p ->
            if(i%3==0){ row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; parent.addView(row) }
            row!!.addView(button(p.first){ sendCommand(p.second) }, LinearLayout.LayoutParams(0,-2,1f))
        }
    }

    private fun frame(id:String, cmd:String, payload:String):ByteArray {
        val b64=Base64.getEncoder().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return "$PROTO|$id|$cmd|$b64".toByteArray(StandardCharsets.UTF_8)
    }
    private data class Reply(val id:String,val cmd:String,val payload:String)
    private fun parse(bytes:ByteArray,len:Int):Reply? {
        val s=String(bytes,0,len,StandardCharsets.UTF_8); val p=s.split('|', limit=4)
        if(p.size!=4 || p[0]!=PROTO) return null
        return try { Reply(p[1],p[2],String(Base64.getDecoder().decode(p[3]),StandardCharsets.UTF_8)) } catch(_:Exception){null}
    }
    private fun endpoint():InetAddress? = try { val s=ip.text.toString().trim().replace(',', '.'); if(s.isBlank()) null else InetAddress.getByName(s) } catch(_:Exception){null}
    private fun ui(msg:String, ok:Boolean=false){ runOnUiThread { status.text=msg; status.setTextColor(if(ok) Color.rgb(90,220,120) else Color.rgb(255,190,70)) } }

    private fun request(cmd:String,payload:String, expected:String, timeout:Int=1200, retries:Int=3):Reply? {
        val addr=endpoint() ?: return null
        repeat(retries) {
            val id=UUID.randomUUID().toString().replace("-","")
            try {
                DatagramSocket().use { s ->
                    s.soTimeout=timeout
                    val out=frame(id,cmd,payload); s.send(DatagramPacket(out,out.size,addr,PORT))
                    val buf=ByteArray(65535); val dp=DatagramPacket(buf,buf.size)
                    while(true){ s.receive(dp); val r=parse(dp.data,dp.length) ?: continue; if(r.id==id && r.cmd==expected) return r }
                }
            } catch(_:Exception){}
        }
        return null
    }
    private fun discover(){ ui("Recherche de l’Agent sur le réseau local…"); io.execute {
        try { DatagramSocket().use { s -> s.broadcast=true; s.soTimeout=1100; val id=UUID.randomUUID().toString().replace("-",""); val out=frame(id,"PING",""); s.send(DatagramPacket(out,out.size,InetAddress.getByName("255.255.255.255"),PORT)); val buf=ByteArray(4096); val dp=DatagramPacket(buf,buf.size); s.receive(dp); val r=parse(dp.data,dp.length); if(r!=null && r.id==id && r.cmd=="PONG"){ runOnUiThread{ip.setText(dp.address.hostAddress)}; ui("Agent détecté : ${dp.address.hostAddress}",true) } else ui("Aucun Agent détecté") } } catch(_:Exception){ ui("Aucun Agent détecté — vérifie Wi‑Fi/pare-feu") }
    }}
    private fun ping(){ ui("Test de liaison…"); io.execute { val r=request("PING","","PONG",900,2); if(r!=null) ui("PONG reçu — liaison locale OK",true) else ui("Aucune réponse de l’Agent sur ${ip.text}:$PORT") } }
    private fun pair(){ val code=pin.text.toString().trim(); if(code.length!=6){ ui("Le code doit contenir 6 chiffres"); return }; ui("Appairage local…"); io.execute {
        val label="${Build.MANUFACTURER} ${Build.MODEL}".trim(); val r=request("PAIR_REQUEST","$clientId\n$label\n$code","PAIR_RESPONSE",1200,3)
        if(r==null){ ui("Agent introuvable ou pas de réponse"); return@execute }
        val lines=r.payload.split('\n',limit=2)
        if(lines.size==2 && lines[0]=="OK"){ token=lines[1].trim(); getPreferences(MODE_PRIVATE).edit().putString("token",token).apply(); ui("Appairage réussi — jeton local reçu",true) }
        else ui(lines.getOrNull(1) ?: "Appairage refusé par l’Agent")
    }}
    private fun sendCommand(cmd:String){ if(token.isBlank()){ ui("Appaire d’abord le téléphone avec l’Agent"); return }; ui("Envoi : $cmd"); io.execute {
        val auth="AUTH1\n$clientId\n$token\n"; val r=request(cmd,auth,"COMMAND_ACK",700,3)
        if(r!=null && r.payload.startsWith("OK")) ui("$cmd — reçu par le PC",true) else ui("$cmd — aucune confirmation du PC")
    }}
}
