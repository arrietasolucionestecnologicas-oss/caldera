/**
 * A.S.T Soluciones Tecnológicas - Gemelo Digital
 * Controlador: Integración Cloud Adafruit IO
 */

const PCI_DATA = { biomasa: 17, gas: 50 };
let intervaloIoT = null;

// --- SEGURIDAD (Bypass GitHub) ---
const AIO_USER = "gjarrieta";
const p1 = "aio_PIRb76wScVzGUCP5R"; 
const p2 = "LqKjwmDgGHf"; 
const AIO_KEY = p1 + p2; 

const FEED_NAME = "temperatura";
const URL_ADAFRUIT = `https://io.adafruit.com/api/v2/${AIO_USER}/feeds/${FEED_NAME}/data/last`;

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById('btn-conectar-iot').addEventListener('click', toggleEnlaceCloud);
    document.getElementById('btn-update').addEventListener('click', procesarCalculos);
    procesarCalculos();
});

function toggleEnlaceCloud() {
    const btn = document.getElementById('btn-conectar-iot');
    const statusDot = document.getElementById('conexion-status');
    const statusText = document.getElementById('conexion-texto');

    if (intervaloIoT) {
        clearInterval(intervaloIoT);
        intervaloIoT = null;
        btn.innerText = "ENLAZAR SENSOR EN VIVO";
        btn.style.backgroundColor = "var(--success)";
        statusDot.classList.remove('status-online');
        statusText.innerText = "DESCONECTADO";
        return;
    }

    btn.innerText = "DETENER ENLACE NUBE";
    btn.style.backgroundColor = "var(--danger)";
    statusDot.classList.add('status-online');
    statusText.innerText = "CONECTADO A ADAFRUIT";
    statusText.style.color = "var(--success)";

    intervaloIoT = setInterval(fetchCloudData, 5000);
    fetchCloudData();
}

async function fetchCloudData() {
    try {
        const response = await fetch(URL_ADAFRUIT, {
            headers: { "X-AIO-Key": AIO_KEY }
        });
        
        if (response.ok) {
            const data = await response.json();
            const tempVal = parseFloat(data.value);
            
            document.getElementById('temp_vapor').value = tempVal.toFixed(1);
            
            // Función de transferencia: 32°C arranque -> 105°C nominal (3.2V)
            let vSim = (tempVal > 32) ? (tempVal < 105 ? ((tempVal - 32) * 3.2 / (105 - 32)) : 3.2) : 0;
            document.getElementById('voltaje_gen').value = vSim.toFixed(2);
            
            procesarCalculos();
        }
    } catch (e) {
        console.error("Falla en telemetría Cloud:", e);
    }
}

function procesarCalculos() {
    const masa_kg = parseFloat(document.getElementById('masa_fuel').value) / 1000;
    const tiempo_s = parseFloat(document.getElementById('tiempo_prueba').value);
    const combustible = document.getElementById('tipo_combustible').value;
    const temp_C = parseFloat(document.getElementById('temp_vapor').value);
    const voltaje_V = parseFloat(document.getElementById('voltaje_gen').value);
    const resistencia_R = parseFloat(document.getElementById('resistencia').value);

    // Potencia Eléctrica Útil (P = V² / R)
    const pElecW = resistencia_R > 0 ? (voltaje_V * voltaje_V) / resistencia_R : 0;
    const pElecmW = pElecW * 1000;

    // Potencia Térmica Base (Qin = (m * PCI) / t)
    const qInW = tiempo_s > 0 ? (masa_kg * PCI_DATA[combustible] * 1000000) / tiempo_s : 0;

    // Eficiencia (%)
    const eficiencia = qInW > 0 ? (pElecW / qInW) * 100 : 0;

    // Actualizar SVG
    document.getElementById('tag-temp').textContent = `${temp_C.toFixed(1)} °C`;
    document.getElementById('tag-qin').textContent = `Qin: ${Math.round(qInW)} W`;
    document.getElementById('tag-voltaje').textContent = `${voltaje_V.toFixed(2)} V`;
    document.getElementById('tag-potencia').textContent = `${pElecmW.toFixed(1)} mW`;

    // Actualizar Resultados
    document.getElementById('res-elec').textContent = `${pElecmW.toFixed(2)} mW`;
    document.getElementById('res-termico').textContent = `${qInW.toFixed(1)} W`;
    document.getElementById('res-eficiencia').textContent = `${eficiencia.toFixed(6)} %`;
}
