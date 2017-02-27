/**
 *  Yamaha Network Receiver
 *     Works on RX-V*
 *    SmartThings driver to connect your Yamaha Network Receiver to SmartThings
 *
 *  Loosely based on: https://github.com/BirdAPI/yamaha-network-receivers
 *   and: http://openremote.org/display/forums/Controlling++RX+2065+Yamaha+Amp
 */

preferences {
    input("destIp", "text", title: "IP", description: "The device IP")
    input("destPort", "number", title: "Port", description: "The port you wish to connect")
}


metadata {
    definition (name: "Yamaha Network Receiver", namespace: "KristopherKubicki", author: "kristopher@acm.org") {
        capability "Actuator"
        capability "Switch"
        capability "Polling"
        capability "Switch Level"

        attribute "mute", "string"
        attribute "input", "string"
        attribute "model_name", "string"

        command "mute"
        command "unmute"
        command "toggleMute"
        command "zoneOneOn"
        command "zoneOneOff"
        command "zoneTwoOn"
        command "zoneTwoOff"
        command "configure"
    }

    simulator {
        // TODO-: define status and reply messages here
    }

    tiles(scale: 2) {
        standardTile("main", "device.switch", width: 2, height: 2, canChangeIcon: false, canChangeBackground: false) {
            state "on", label: '${name}', action:"switch.off", backgroundColor: "#79b821", icon:"st.Electronics.electronics13"
            state "off", label: '${name}', action:"switch.on", backgroundColor: "#ffffff", icon:"st.Electronics.electronics13"
        }
        standardTile("zone1", "device.switchZone1", width: 2, height: 2, canChangeIcon: false, canChangeBackground: true) {
            state "on", label: 'zone 1', action:"zoneOneOff", backgroundColor: "#79b821", icon:"st.Electronics.electronics16"
            state "off", label: 'zone 1', action:"zoneOneOn", backgroundColor: "#ffffff", icon:"st.Electronics.electronics16"
        }
        standardTile("zone2", "device.switchZone2", width: 2, height: 2, canChangeIcon: false, canChangeBackground: true) {
            state "on", label: 'zone 2', action:"zoneTwoOff", backgroundColor: "#79b821", icon:"st.Electronics.electronics16"
            state "off", label: 'zone 2', action:"zoneTwoOn", backgroundColor: "#ffffff", icon:"st.Electronics.electronics16"
        }
        standardTile("mute", "device.mute", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
            state "muted", label: '${name}', action:"unmute", backgroundColor: "#79b821", icon:"st.Electronics.electronics13"
            state "unmuted", label: '${name}', action:"mute", backgroundColor: "#ffffff", icon:"st.Electronics.electronics13"
        }
        controlTile("level", "device.level", "slider", height: 1, width: 5, inactiveLabel: false, range: "(0..100)") {
            state "level", label: '${name}', action:"setLevel"
        }
        valueTile("input", "device.input", width: 6, height: 2, decoration: 'flat') {
            state "input", label: '${currentValue}'
        }
        standardTile("poll", "device.poll", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
            state "poll", label: "", action: "polling.poll", icon: "st.secondary.refresh", backgroundColor: "#FFFFFF"
        }

        main "main"
        details(["main", "zone1", "zone2", "mute", "level", "input"])
    }
}

def setOrGet(name, value) {
  if(value == null) {
    return device.currentValue(name)
  }
  else {
    sendEvent(name: name, value: value)
    return value
  }
}

def getVolumeXml(state, mute, level) {
    if(state == 'off' || mute == 'muted') {
      return "<Volume><Mute>On</Mute></Volume>"
    }
    else {
      def int dB = level * 9 - 800
      log.debug "scaled $dB"
      dB = ((dB/5) as Integer) * 5
      log.debug "scaled $dB"

      return "<Volume><Lvl><Val>${dB}</Val><Exp>1</Exp><Unit>dB</Unit></Lvl></Volume>"
    }
}

def configure(config) {
    def input = setOrGet('input', config['input'])
    def level = setOrGet('level', config['level'])
    def mute  = setOrGet('mute', config['mute'])
    def zone1 = setOrGet('switchZone1', config['zone1'])
    def zone2 = setOrGet('switchZone2', config['zone2'])

    log.debug("input[$input], level[$level], mute[$mute], zone1[$zone1], zone2[$zone2]")

    def inputXml = "<Input><Input_Sel>${input}</Input_Sel></Input>"
    put("<System><Party_Mode><Mode>On</Mode></Party_Mode></System><Main_Zone>${getVolumeXml(zone1, mute, level)}${inputXml}</Main_Zone><Zone_2>${getVolumeXml(zone2, mute, level)}</Zone_2>")
}

def initialize() {
    log.debug "Yamaha Initialize"
}

def installed() {
    log.debug "Yamaha Installed"
}

def updated() {
    log.debug "Yamaha Updated"
}

def parse(String description) {
    def map = stringToMap(description)
    if(!map.body) { return }
    def body = new String(map.body.decodeBase64())
    log.debug "body: " + body

    def r = new XmlSlurper().parseText(body)

    if(r.System.Config.Model_Name) {
        log.debug "Model: " + r.System.Config.Model_Name
        createEvent(name: "model_name", value: r.System.Config.Model_Name)
    }
    createEvent(name: "model_name", value: "Go Fuck Yourself")

    def power = r.Main_Zone.Basic_Status.Power_Control.Power.text()
    if(power == "On") {
        sendEvent(name: "switch", value: 'on')
    }
    if(power != "" && power != "On") {
        sendEvent(name: "switch", value: 'off')
    }

    def inputChan = r.Main_Zone.Basic_Status.Input.Input_Sel.text()
    if(inputChan != "") {
    log.debug "input: $inputChan"
        sendEvent(name: "input", value: inputChan.replace('OPTICAL', 'O'))
    }

    def muteLevel = r.Main_Zone.Basic_Status.Volume.Mute.text()
    if(muteLevel == "On") {
        sendEvent(name: "mute", value: 'muted')
    }
    if(muteLevel != "" && muteLevel != "On") {
        sendEvent(name: "mute", value: 'unmuted')
    }

    if(r.Main_Zone.Basic_Status.Volume.Lvl.Val.text()) {
        def int volLevel = r.Main_Zone.Basic_Status.Volume.Lvl.Val.toInteger() ?: -250
        log.debug "volume (dB): $volLevel"
        volLevel = (((volLevel + 800) / 9)/5)*5
        log.debug "volume (percent): $volLevel"
           def int curLevel = 65
        try {
            curLevel = device.currentValue("level")
        } catch(org.codehaus.groovy.runtime.typehandling.GroovyCastException nfe) {
            curLevel = 65
        }
        if(curLevel != volLevel) {
            sendEvent(name: "level", value: volLevel)
        }
    }
}

def setLevel(value) {
    log.debug "setLevel($value)"
    configure(level: value)
}

def on(args) {
    log.debug "on()"
    sendEvent(name: "switch", value: 'on')
    put('<Main_Zone><Power_Control><Power>On</Power></Power_Control></Main_Zone>')
}

def off() {
    log.debug "off()"
    sendEvent(name: "switch", value: 'off')
    put('<Main_Zone><Power_Control><Power>Standby</Power></Power_Control></Main_Zone>')
}

def zoneOneOn(evt) {
    log.debug "zoneOneOn()"
    configure(zone1: 'on')
}

def zoneOneOff(evt) {
    log.debug "zoneOneOff()"
    configure(zone1: 'off')
}

def zoneTwoOn() {
    log.debug "zoneTwoOn()"
    configure(zone2: 'on')
}

def zoneTwoOff() {
    log.debug "zoneTwoOff()"
    configure(zone2: 'off')
}

def toggleMute(){
    device.currentValue("mute") == "muted" ? unmute() : mute()
}

def mute() {
    configure(mute: 'muted')
}

def unmute() {
    configure(mute: 'unmuted')
}

def poll() {
    log.debug "poll"
    refresh()
}

def refresh() {
    log.debug "refresh"
    get('<System><Config>GetParam</Config></System>');
}

def get(body) {
    request('<YAMAHA_AV cmd="GET">' + body + '</YAMAHA_AV>')
}

def put(body) {
    request('<YAMAHA_AV cmd="PUT">' + body + '</YAMAHA_AV>')
}

def request(body) {
    log.debug "request: " + body
    def hosthex = convertIPtoHex(destIp)
    def porthex = convertPortToHex(destPort)
    device.deviceNetworkId = "$hosthex:$porthex"

    def hubAction = new physicalgraph.device.HubAction(
      'method': 'POST',
      'path': "/YamahaRemoteControl/ctrl",
      'body': body,
      'headers': [ HOST: "$destIp:$destPort" ]
    )

    //log.debug "HUB Action: $hubAction"

    hubAction
}

private String convertIPtoHex(ipAddress) {
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}
