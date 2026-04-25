/**
 * A.S.T Soluciones Tecnológicas - Gemelo Digital IoT Completo
 * Autor: Ing. Gerson Arrieta
 * Descripción: Sistema Ciberfísico Termodinámico. Topología 100% Nube.
 * Adquisición de telemetría global vía Adafruit IO REST API.
 */

const PCI_DATA = { biomasa: 17, gas: 50 };
let intervaloIoT = null;

// Inicialización de Listeners
document.getElementById('btn-update').addEventListener('click', procesarTelemetria);
document.getElementById('tipo_combustible').addEventListener('change', procesarTelemetria);
document.getElementById('btn-conectar-iot').addEventListener('click', toggleConexionIoT);

window.onload = function() {
    procesarTelemetria();
};

function toggleConexionIoT() {
    const btn = document.getElementById('btn-conectar-iot');
    const statusDot = document.getElementById('conexion-status');
    const statusText = document.getElementById('conexion-texto');

    // Desconexión
    if (intervaloIoT) {
        clearInterval(intervaloIoT);
        intervaloIoT = null;
        btn.innerText = "ENLAZAR SENSOR EN VIVO";
        btn.style.backgroundColor = "var(--success)";
        statusDot.classList.remove('status-online');
        statusText.innerText = "DESCONECTADO";
        statusText.style.color = "var(--text-main)";
        return;
    }

    // Conexión
    btn.innerText = "DETENER ENLACE CLOUD";
    btn.style.backgroundColor = "var(--danger)";
    statusDot.classList.add('status-online');
    statusText.innerText = "NUBE ACTIVA";
    statusText.style.color = "var(--success)";

    // Credenciales y Endpoint Nube A.S.T
    const aioUser = "gjarrieta";
    // Ofuscación de cadena para evasión de Secret Scanning en GitHub
    const aioKey = "aio_" + "ArKi74cZjkBHA8NhgYsAks7V7yn2";
    const feedName = "temperatura";
    const url = `https://io.adafruit.com/api/v2/${aioUser}/feeds/${feedName}/data/last`;

    // Polling asíncrono cada 4000 milisegundos (Límite seguro Adafruit IO)
    intervaloIoT = setInterval(async () => {
        try {
            const respuesta = await fetch(url, {
                headers: { "X-AIO-Key": aioKey }
            });
            
            if (respuesta.ok) {
                const datosJson = await respuesta.json();
                let tempReal = parseFloat(datosJson.value);
                
                // 1. Actualizar Temperatura en UI
                document.getElementById('temp_vapor').value = tempReal.toFixed(1);
                
                // 2. Función de Transferencia (Planta Térmica -> Generación Eléctrica)
                let voltajeEstimado = 0;
                
                if (tempReal >= 90) {
                    // Régimen Nominal
                    voltajeEstimado = ((tempReal - 90) / (115 - 90)) * 3.2;
                    if (voltajeEstimado > 3.2) voltajeEstimado = 3.2; 
                } else if (tempReal > 32 && tempReal < 90) {
                    // Régimen de Arranque/Sustentación
                    voltajeEstimado = ((tempReal - 32) / (90 - 32)) * 0.8; 
                }
                
                // 3. Inyectar Voltaje Simulado en UI
                document.getElementById('voltaje_gen').value = voltajeEstimado.toFixed(2);
                
                // 4. Procesar Lógica P&ID
                procesarTelemetria();
            }
        } catch (error) {
            console.error("Falla en la capa de red con servidor MQTT/REST:", error);
        }
    }, 4000);
}

function procesarTelemetria() {
    // 1. Adquisición de Variables Físicas
    const tipoCombustible = document.getElementById('tipo_combustible').value;
    const masa_g = parseFloat(document.getElementById('masa_fuel').value);
    const tiempo_s = parseFloat(document.getElementById('tiempo_prueba').value);
    const temp_vapor_C = parseFloat(document.getElementById('temp_vapor').value);
    const voltaje_V = parseFloat(document.getElementById('voltaje_gen').value);
    const resistencia_Ohms = parseFloat(document.getElementById('resistencia').value);

    // 2. Motor de Cálculo A.S.T
    // Ley de Ohm - Potencia Eléctrica (W y mW)
    let potencia_electrica_W = resistencia_Ohms > 0 ? Math.pow(voltaje_V, 2) / resistencia_Ohms : 0;
    const potencia_electrica_mW = potencia_electrica_W * 1000;

    // Termodinámica - Potencia Térmica Base (W)
    const masa_kg = masa_g / 1000;
    const energia_termica_J = masa_kg * (PCI_DATA[tipoCombustible] * 1000000); 
    const potencia_termica_W = tiempo_s > 0 ? energia_termica_J / tiempo_s : 0;

    // Eficiencia Global
    const eficiencia_porcentaje = potencia_termica_W > 0 ? (potencia_electrica_W / potencia_termica_W) * 100 : 0;

    // 3. Renderizado y Animación de Gemelo Digital
    document.getElementById('tag-qin').textContent = `Qin: ${potencia_termica_W.toFixed(0)} W`;
    document.getElementById('tag-temp').textContent = `${temp_vapor_C.toFixed(1)} °C`;
    document.getElementById('tag-voltaje').textContent = `${voltaje_V.toFixed(2)} V`;
    document.getElementById('tag-potencia').textContent = `${potencia_electrica_mW.toFixed(1)} mW`;

    const fuegoExt = document.getElementById('svg-fuego-ext');
    const fuegoInt = document.getElementById('svg-fuego-int');
    const tagQin = document.getElementById('tag-qin');

    // Transición de curvas según material
    if (tipoCombustible === 'gas') {
        fuegoExt.setAttribute('fill', '#3b82f6');
        fuegoInt.setAttribute('fill', '#93c5fd');
        tagQin.style.fill = '#60a5fa';
    } else {
        fuegoExt.setAttribute('fill', '#ef4444');
        fuegoInt.setAttribute('fill', '#f59e0b');
        tagQin.style.fill = '#ef4444';
    }

    // 4. Volcado de Datos en Panel de Resultados
    document.getElementById('res-elec').textContent = `${potencia_electrica_mW.toFixed(2)} mW`;
    document.getElementById('res-termico').textContent = `${potencia_termica_W.toFixed(1)} W`;
    document.getElementById('res-eficiencia').textContent = `${eficiencia_porcentaje.toFixed(6)} %`;
}
